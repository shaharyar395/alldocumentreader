package com.theoccess.alldocreader.ui.scan

import android.app.Activity
import android.net.Uri
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Adding pages: Take a photo (Google's ML Kit document scanner, or the in-app camera when it is
 * not available) or Choose from gallery → "Choose cropping method" → new [ScanPage]s. Create it as a field of the activity so the
 * result launchers are registered before the activity starts.
 */
class AddPagesFlow(
    private val activity: AppCompatActivity,
    private val onAdded: (List<ScanPage>) -> Unit
) {
    /** Called when the person backs out without adding anything. */
    var onCancelled: () -> Unit = {}

    private val camera = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val paths = r.data?.getStringArrayListExtra(CameraActivity.EXTRA_PATHS).orEmpty()
        if (r.resultCode == Activity.RESULT_OK && paths.isNotEmpty()) askCrop(paths.map { File(it) }, fromCamera = true)
        else onCancelled()
    }

    private val gallery = activity.registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isEmpty()) onCancelled() else importPictures(uris)
    }

    /** Shows "Take a photo / Choose from gallery / Cancel". */
    fun chooseSource() = SourceSheet.show(activity, ::openCamera, ::openGallery, onCancel = { onCancelled() })

    /**
     * Google's document scanner finds, crops and straightens the pages itself, so its photos go
     * straight in (no "Choose cropping method"); when it is not available, the app's own camera.
     */
    private val scanner = DocScanner(
        activity,
        onPages = { files -> build(files, fromCamera = true, autoCrop = false) },
        onCancelled = { onCancelled() },
        onUnavailable = { camera.launch(CameraActivity.intent(activity, single = false)) }
    )

    fun openCamera() = scanner.start()

    fun openGallery() {
        try {
            gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: Exception) {
            activity.toast(R.string.convert_failed)
            onCancelled()
        }
    }

    /** Copies picked pictures into the job folder (the originals are never modified). */
    private fun importPictures(uris: List<Uri>) {
        val pill = ProgressPill(activity)
        pill.show(activity.getString(R.string.processing_progress, 0))
        activity.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapIndexedNotNull { i, uri ->
                    val f = ScanSession.newFile(activity, "img")
                    val ok = try {
                        activity.contentResolver.openInputStream(uri)?.use { input ->
                            f.outputStream().use { input.copyTo(it) }
                        } != null
                    } catch (e: Exception) {
                        false
                    }
                    withContext(Dispatchers.Main) {
                        pill.show(activity.getString(R.string.processing_progress, (i + 1) * 100 / uris.size))
                    }
                    if (ok) f else null
                }
            }
            pill.dismiss()
            if (files.isEmpty()) {
                activity.toast(R.string.convert_failed)
                onCancelled()
            } else {
                askCrop(files, fromCamera = false)
            }
        }
    }

    private fun askCrop(files: List<File>, fromCamera: Boolean) {
        if (ScanPrefs.dontAsk) build(files, fromCamera, ScanPrefs.autoCrop)
        else CropMethodSheet.show(activity) { auto -> build(files, fromCamera, auto) }
    }

    private fun build(files: List<File>, fromCamera: Boolean, autoCrop: Boolean) {
        activity.lifecycleScope.launch {
            val pages = withContext(Dispatchers.Default) {
                files.map { f ->
                    ScanSession.newPage(f, fromCamera).also { page ->
                        if (autoCrop) page.quad = detect(f)
                    }
                }
            }
            // tell the person when Auto crop could not find the page edges (they can still crop by hand)
            if (autoCrop && pages.any { it.quad == null }) activity.toast(R.string.crop_not_found)
            onAdded(pages)
        }
    }

    companion object {
        private const val MAX_PICK = 50

        /** Document corners for [file], or null to keep the whole picture. */
        fun detect(file: File): FloatArray? = try {
            ImageOps.decode(file, 900)?.let { bmp -> ImageOps.detectDocument(bmp).also { bmp.recycle() } }
        } catch (e: Throwable) {
            null
        }
    }
}
