package com.theoccess.alldocreader.ui.scan

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaActionSound
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.ActivityCameraBinding
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * In-app document camera: X, flash (off / auto / on), grid, shutter sound, 2× zoom,
 * tap to focus, shutter, last-shot thumbnail with count and the Next arrow.
 * Several pages can be shot in a row; [EXTRA_SINGLE] returns right after one shot (Retake).
 */
class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private val shots = ArrayList<String>()
    private var single = false
    private var busy = false
    private var flashMode = ImageCapture.FLASH_MODE_OFF
    private var zoomRatio = 1f
    private var scanAnim: android.animation.ObjectAnimator? = null
    private lateinit var scaleDetector: android.view.ScaleGestureDetector
    private var sound: MediaActionSound? = null

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            toast(R.string.camera_permission_needed)
            finishCancelled()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
        }
        single = intent.getBooleanExtra(EXTRA_SINGLE, false)
        savedInstanceState?.getStringArrayList(STATE_SHOTS)?.let { shots.addAll(it) }
        flashMode = Prefs.raw.getInt(KEY_FLASH, ImageCapture.FLASH_MODE_OFF)

        binding.btnClose.setOnClickListener { finishCancelled() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finishCancelled()
        })
        binding.btnShutter.setOnClickListener { capture() }
        binding.btnNext.setOnClickListener { finishWithShots() }
        binding.thumbBox.setOnClickListener { finishWithShots() }
        binding.btnFlash.setOnClickListener {
            flashMode = when (flashMode) {
                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_AUTO
                ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                else -> ImageCapture.FLASH_MODE_OFF
            }
            Prefs.raw.edit().putInt(KEY_FLASH, flashMode).apply()
            imageCapture?.flashMode = flashMode
            updateButtons()
        }
        binding.btnGrid.setOnClickListener {
            Prefs.raw.edit().putBoolean(KEY_GRID, !Prefs.raw.getBoolean(KEY_GRID, false)).apply()
            updateButtons()
        }
        binding.btnSound.setOnClickListener {
            Prefs.raw.edit().putBoolean(KEY_SOUND, !Prefs.raw.getBoolean(KEY_SOUND, true)).apply()
            updateButtons()
        }
        // "+" shows a guide in the middle of the picture to line the page up
        binding.btnCross.setOnClickListener {
            Prefs.raw.edit().putBoolean(KEY_CROSS, !Prefs.raw.getBoolean(KEY_CROSS, false)).apply()
            updateButtons()
        }
        // pinch to zoom
        scaleDetector = android.view.ScaleGestureDetector(this, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: android.view.ScaleGestureDetector): Boolean {
                val state = camera?.cameraInfo?.zoomState?.value ?: return false
                zoomRatio = (zoomRatio * d.scaleFactor).coerceIn(state.minZoomRatio, state.maxZoomRatio)
                camera?.cameraControl?.setZoomRatio(zoomRatio)
                binding.tvZoom.text = String.format(java.util.Locale.US, "%.1f×", zoomRatio)
                binding.tvZoom.visibility = View.VISIBLE
                binding.tvZoom.removeCallbacks(hideZoom)
                binding.tvZoom.postDelayed(hideZoom, 1000)
                return true
            }
        })
        setupTapToFocus()
        updateButtons()
        updateThumb()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera()
        else permission.launch(Manifest.permission.CAMERA)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_SHOTS, shots)
    }

    private val hideZoom = Runnable { binding.tvZoom.visibility = View.GONE }

    private fun updateButtons() {
        binding.btnFlash.setImageResource(
            when (flashMode) {
                ImageCapture.FLASH_MODE_AUTO -> R.drawable.ic_flash_auto
                ImageCapture.FLASH_MODE_ON -> R.drawable.ic_flash_on
                else -> R.drawable.ic_flash_off
            }
        )
        val grid = Prefs.raw.getBoolean(KEY_GRID, false)
        binding.grid.visibility = if (grid) View.VISIBLE else View.GONE
        binding.btnGrid.alpha = if (grid) 1f else 0.6f
        binding.btnSound.setImageResource(if (Prefs.raw.getBoolean(KEY_SOUND, true)) R.drawable.ic_sound_on else R.drawable.ic_sound_off)
        val cross = Prefs.raw.getBoolean(KEY_CROSS, false)
        binding.ivCross.visibility = if (cross) View.VISIBLE else View.GONE
        binding.btnCross.alpha = if (cross) 1f else 0.6f
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = try { future.get() } catch (e: Exception) { null }
            if (provider == null) {
                toast(R.string.no_camera)
                finishCancelled()
                return@addListener
            }
            // bind once the preview is laid out, so the photo can be cut to exactly what is on screen
            binding.preview.post { bind(provider) }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bind(provider: ProcessCameraProvider) {
        if (isFinishing || isDestroyed) return
        @Suppress("DEPRECATION")
        val preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3).build()
        preview.setSurfaceProvider(binding.preview.surfaceProvider)
        @Suppress("DEPRECATION")
        val capture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setFlashMode(flashMode)
            .build()
        imageCapture = capture
        // The preview fills the screen (and so hides part of the camera picture). The view port makes
        // the saved photo show exactly what was visible when the shutter was pressed.
        val group = androidx.camera.core.UseCaseGroup.Builder()
            .addUseCase(preview)
            .addUseCase(capture)
            .apply { binding.preview.viewPort?.let { setViewPort(it) } }
            .build()
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, group)
        } catch (e: Exception) {
            toast(R.string.no_camera)
            finishCancelled()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTapToFocus() {
        binding.preview.setOnTouchListener { v, e ->
            scaleDetector.onTouchEvent(e)
            if (e.action == MotionEvent.ACTION_UP && !scaleDetector.isInProgress && e.pointerCount == 1) {
                val cam = camera ?: return@setOnTouchListener true
                val point = binding.preview.meteringPointFactory.createPoint(e.x, e.y)
                cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                val f = binding.ivFocus
                f.translationX = v.left + e.x - f.width / 2f
                f.translationY = v.top + e.y - f.height / 2f
                f.animate().cancel()
                f.alpha = 1f; f.scaleX = 1.3f; f.scaleY = 1.3f
                f.animate().scaleX(1f).scaleY(1f).setDuration(200).withEndAction {
                    f.animate().alpha(0f).setStartDelay(700).setDuration(250).start()
                }.start()
                v.performClick()
            }
            true
        }
    }

    private fun capture() {
        val capture = imageCapture ?: return
        if (busy) return
        busy = true
        binding.pbCapture.visibility = View.VISIBLE
        val file = ScanSession.newFile(this, "cam")
        if (Prefs.raw.getBoolean(KEY_SOUND, true)) try {
            val snd = sound ?: MediaActionSound().also { it.load(MediaActionSound.SHUTTER_CLICK); sound = it }
            snd.play(MediaActionSound.SHUTTER_CLICK)
        } catch (ignored: Exception) {}
        binding.flash.alpha = 0.8f
        binding.flash.animate().alpha(0f).setDuration(220).start()
        startScanning()
        val started = System.currentTimeMillis()
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // let the scanning sweep run at least once
                    val wait = (SCAN_MS - (System.currentTimeMillis() - started)).coerceAtLeast(0)
                    binding.root.postDelayed({
                        if (isFinishing || isDestroyed) return@postDelayed
                        busy = false
                        binding.pbCapture.visibility = View.GONE
                        stopScanning()
                        shots += file.absolutePath
                        if (single) finishWithShots() else flyToThumb(file)
                    }, wait)
                }

                override fun onError(exception: ImageCaptureException) {
                    stopScanning()
                    busy = false
                    binding.pbCapture.visibility = View.GONE
                    file.delete()
                    toast(R.string.capture_failed)
                }
            }
        )
    }

    /** Freezes the picture and sweeps a blue scanning band over it while the shot is saved. */
    private fun startScanning() {
        binding.preview.bitmap?.let {
            binding.ivFrozen.setImageBitmap(it)
            binding.ivFrozen.visibility = View.VISIBLE
        }
        val band = binding.scanBand
        val h = binding.preview.height.toFloat()
        band.visibility = View.VISIBLE
        scanAnim?.cancel()
        scanAnim = android.animation.ObjectAnimator.ofFloat(band, View.TRANSLATION_Y, -band.height.toFloat(), h - band.height).apply {
            duration = SCAN_MS
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
    }

    private fun stopScanning() {
        scanAnim?.cancel()
        scanAnim = null
        binding.scanBand.visibility = View.GONE
        binding.ivFrozen.visibility = View.GONE
        binding.ivFrozen.setImageDrawable(null)
    }

    /** The new shot shrinks from the preview into the thumbnail at the bottom left. */
    private fun flyToThumb(file: File) {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { ImageOps.decode(file, 600) }
            if (bmp == null || isFinishing) { updateThumb(); return@launch }
            val fly = binding.ivFly
            fly.setImageBitmap(bmp)
            fly.visibility = View.VISIBLE
            fly.scaleX = 1f; fly.scaleY = 1f; fly.translationX = 0f; fly.translationY = 0f
            val thumb = binding.thumbBox
            // ivFly was GONE (0×0 until the next layout pass); it is laid out over the preview,
            // so measure against the preview instead.
            val area = binding.preview
            if (area.width == 0 || area.height == 0) { fly.visibility = View.GONE; updateThumb(bmp); return@launch }
            val tl = IntArray(2).also { thumb.getLocationInWindow(it) }
            val fl = IntArray(2).also { area.getLocationInWindow(it) }
            val sx = thumb.width.toFloat() / area.width
            val sy = thumb.height.toFloat() / area.height
            fly.pivotX = 0f; fly.pivotY = 0f
            fly.animate()
                .scaleX(sx).scaleY(sy)
                .translationX((tl[0] - fl[0]).toFloat())
                .translationY((tl[1] - fl[1]).toFloat())
                .setDuration(380)
                .withEndAction {
                    fly.visibility = View.GONE
                    fly.setImageDrawable(null)
                    updateThumb(bmp)
                }.start()
        }
    }

    private fun updateThumb(bmp: android.graphics.Bitmap? = null) {
        val has = shots.isNotEmpty()
        binding.thumbBox.visibility = if (has) View.VISIBLE else View.INVISIBLE
        binding.btnNext.visibility = if (has) View.VISIBLE else View.INVISIBLE
        binding.tvCount.visibility = if (shots.size > 1) View.VISIBLE else View.GONE
        binding.tvCount.text = shots.size.toString()
        if (bmp != null) binding.ivThumb.setImageBitmap(bmp)
        else if (has) lifecycleScope.launch {
            val b = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(shots.last(), BitmapFactory.Options().apply { inSampleSize = 8 })
            }
            if (b != null) binding.ivThumb.setImageBitmap(b)
        }
    }

    private fun finishWithShots() {
        if (shots.isEmpty()) return
        setResult(Activity.RESULT_OK, Intent().putStringArrayListExtra(EXTRA_PATHS, shots))
        finish()
    }

    private fun finishCancelled() {
        shots.forEach { File(it).delete() }
        shots.clear()
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        scanAnim?.cancel()
        try { sound?.release() } catch (ignored: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATHS = "paths"
        private const val EXTRA_SINGLE = "single"
        private const val STATE_SHOTS = "shots"
        private const val KEY_FLASH = "camera_flash"
        private const val KEY_GRID = "camera_grid"
        private const val KEY_SOUND = "camera_sound"
        private const val KEY_CROSS = "camera_cross"
        private const val SCAN_MS = 900L

        fun intent(context: Context, single: Boolean) =
            Intent(context, CameraActivity::class.java).putExtra(EXTRA_SINGLE, single)
    }
}
