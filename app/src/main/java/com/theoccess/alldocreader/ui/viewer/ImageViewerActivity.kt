package com.theoccess.alldocreader.ui.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.databinding.ActivityImageViewerBinding
import com.theoccess.alldocreader.ui.files.FileSheets
import com.theoccess.alldocreader.ui.scan.ConvertResultActivity
import com.theoccess.alldocreader.ui.scan.ProgressPill
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

/** In-app viewer for JPG / PNG (and other pictures): zoom, share, convert to PDF, bookmark, file menu. */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PATH = "path"

        fun intent(context: Context, file: File): Intent =
            Intent(context, ImageViewerActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath)

        /** Picture types the viewer shows (decoded by Android itself). */
        private val EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")

        fun canOpen(name: String) = name.substringAfterLast('.', "").lowercase() in EXTENSIONS
    }

    private lateinit var b: ActivityImageViewerBinding
    private lateinit var doc: DocFile
    private var converting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(b.root)
        val file = File(intent.getStringExtra(EXTRA_PATH) ?: "")
        if (!file.exists()) {
            toast(R.string.file_not_found)
            finish()
            return
        }
        doc = DocFile.from(file)
        LibraryStore.addRecent(doc.path)
        b.tvTitle.text = doc.name
        b.btnBack.setOnClickListener { finish() }
        b.btnShare.setOnClickListener { FileActions.share(this, doc) }
        b.btnConvert.setOnClickListener { convertToPdf() }
        b.btnBookmark.setOnClickListener { FileActions.toggleBookmark(this, doc); refreshBookmark() }
        b.btnMore.setOnClickListener {
            FileSheets.showMenu(this, doc) {
                // renamed / deleted from the menu: this copy is gone
                if (!File(doc.path).exists()) finish() else refreshBookmark()
            }
        }
        b.image.onTap = {
            val show = b.topBar.visibility != View.VISIBLE
            b.topBar.visibility = if (show) View.VISIBLE else View.GONE
        }
        refreshBookmark()

        lifecycleScope.launch {
            val dm = resources.displayMetrics
            val maxPx = (max(dm.widthPixels, dm.heightPixels) * 2).coerceAtMost(4096)
            val bmp = withContext(Dispatchers.IO) { decode(file, maxPx) }
            b.progress.visibility = View.GONE
            if (bmp == null) {
                toast(R.string.tpl_image_failed)
                finish()
                return@launch
            }
            b.image.bitmap = bmp
        }
    }

    private fun convertToPdf() {
        if (converting) return
        converting = true
        val pill = ProgressPill(this)
        pill.show(getString(R.string.converting_progress, 0))
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                try {
                    Converters.imageToPdf(applicationContext, doc.file, doc.file.nameWithoutExtension) { p ->
                        runOnUiThread { pill.show(getString(R.string.converting_progress, p)) }
                    }.also {
                        com.theoccess.alldocreader.data.SavedFiles.onSaved(applicationContext, it)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            pill.dismiss()
            converting = false
            if (out == null) {
                toast(R.string.convert_failed)
                return@launch
            }
            startActivity(ConvertResultActivity.intent(this@ImageViewerActivity, out))
        }
    }

    private fun refreshBookmark() {
        b.btnBookmark.setImageResource(
            if (LibraryStore.isBookmarked(doc.path)) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border
        )
    }

    private fun decode(file: File, maxPx: Int): Bitmap? = try {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        var sample = 1
        while (o.outWidth / sample > maxPx || o.outHeight / sample > maxPx) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        if (bmp == null) null else {
            val deg = try {
                when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) {
                0f
            }
            if (deg == 0f) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg) }, true)
        }
    } catch (e: Throwable) {
        null
    }
}
