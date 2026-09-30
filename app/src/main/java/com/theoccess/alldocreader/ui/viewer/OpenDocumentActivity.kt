package com.theoccess.alldocreader.ui.viewer

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.databinding.ActivitySplashBinding
import com.theoccess.alldocreader.ui.create.Outputs
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Opens a document chosen in "Import files" or sent from another app ("Open with" /
 * "Share"): shows the app's splash (logo + sliding progress bar) while the file is resolved,
 * then opens it in the in-app viewer — the same flow as the original app.
 *
 * Files that live in shared storage are opened in place (so conversions are saved next to
 * the other outputs and the file keeps its name); anything else (Drive, e-mail attachments…)
 * is copied into Documents/AllDocumentReader/import first.
 */
@SuppressLint("CustomSplashScreen")
class OpenDocumentActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var progressAnim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })
        startProgress()

        val uri = sourceUri(intent)
        if (uri == null) {
            toast(R.string.file_not_found)
            finish()
            return
        }
        lifecycleScope.launch {
            val started = System.currentTimeMillis()
            val file = withContext(Dispatchers.IO) {
                try { resolve(applicationContext, uri) } catch (e: Exception) { null }
            }
            // keep the splash on screen for a moment, like the original app
            val left = MIN_SPLASH_MS - (System.currentTimeMillis() - started)
            if (left > 0) delay(left)
            if (isFinishing || isDestroyed) return@launch
            if (file == null) {
                toast(R.string.import_failed)
                finish()
                return@launch
            }
            val doc = DocFile.from(file)
            LibraryStore.addRecent(doc.path)
            FileRepository.refresh(applicationContext, force = true)
            if (ViewerActivity.canOpen(doc)) {
                startActivity(ViewerActivity.intent(this@OpenDocumentActivity, file))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            } else if (doc.type != FileType.OTHER) {
                FileActions.open(this@OpenDocumentActivity, doc)
            } else {
                toast(R.string.unsupported_title)
            }
            finish()
        }
    }

    private fun startProgress() {
        binding.progressTrack.post {
            val travel = (binding.progressTrack.width - binding.progressThumb.width).toFloat()
            progressAnim = ObjectAnimator.ofFloat(binding.progressThumb, View.TRANSLATION_X, 0f, travel).apply {
                duration = 750
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    override fun onDestroy() {
        progressAnim?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val MIN_SPLASH_MS = 1200L

        fun intent(context: Context, uri: Uri): Intent =
            Intent(context, OpenDocumentActivity::class.java)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        private fun sourceUri(intent: Intent): Uri? = when (intent.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            else -> intent.data
        }

        /** Real file for [uri] if we can read it directly, otherwise a copy in the import folder. */
        fun resolve(context: Context, uri: Uri): File? {
            realFile(context, uri)?.let { if (it.isFile && it.canRead()) return it }
            val name = displayName(context, uri) ?: "Imported_${System.currentTimeMillis()}"
            val clean = Outputs.clean(name)
            val base = clean.substringBeforeLast('.', clean)
            val ext = clean.substringAfterLast('.', "").ifEmpty { extFromMime(context.contentResolver.getType(uri)) }
            // already imported (same name and size)? reuse it instead of making "name (1)"
            val size = querySize(context, uri)
            for (dir in candidateDirs(context)) {
                val existing = File(dir, "$base.$ext")
                if (existing.exists() && size > 0 && existing.length() == size) return existing
                try {
                    dir.mkdirs()
                    val out = Converters.uniqueFile(dir, base, ext)
                    val ok = context.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                        true
                    } ?: false
                    if (ok) {
                        com.theoccess.alldocreader.data.SavedFiles.onSaved(context, out)
                        return out
                    }
                    out.delete()
                } catch (e: Exception) {
                    // try the next folder
                }
            }
            return null
        }

        private fun candidateDirs(context: Context): List<File> = listOfNotNull(
            Outputs.importDir(),
            context.getExternalFilesDir("import"),
            File(context.filesDir, "import")
        )

        /** Maps file://, ExternalStorageProvider, DownloadsProvider "raw:" and MediaStore uris to paths. */
        private fun realFile(context: Context, uri: Uri): File? {
            if (uri.scheme == ContentResolver.SCHEME_FILE) return uri.path?.let { File(it) }
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return null
            try {
                if (DocumentsContract.isDocumentUri(context, uri)) {
                    val id = DocumentsContract.getDocumentId(uri)
                    when (uri.authority) {
                        "com.android.externalstorage.documents" -> {
                            val type = id.substringBefore(':')
                            val rel = id.substringAfter(':', "")
                            val root = if (type.equals("primary", true)) Environment.getExternalStorageDirectory().path
                            else "/storage/$type"
                            return File(root, rel)
                        }
                        "com.android.providers.downloads.documents" -> {
                            if (id.startsWith("raw:")) return File(id.removePrefix("raw:"))
                        }
                    }
                }
                // MediaStore / file managers that expose a _data column
                context.contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex("_data")
                        if (idx >= 0) c.getString(idx)?.let { return File(it) }
                    }
                }
            } catch (ignored: Exception) {
            }
            // some file managers use content://authority/root/storage/... or /external_files/...
            uri.path?.let { p ->
                val candidates = listOf(p.substringAfter("/root", ""), p.substringAfter("/storage", "").let { if (it.isEmpty()) "" else "/storage$it" })
                candidates.filter { it.startsWith("/") }.map { File(it) }.firstOrNull { it.isFile }?.let { return it }
            }
            return null
        }

        private fun displayName(context: Context, uri: Uri): String? = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
        } catch (e: Exception) {
            uri.lastPathSegment?.substringAfterLast('/')
        }

        private fun querySize(context: Context, uri: Uri): Long = try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L
        } catch (e: Exception) {
            -1L
        }

        private fun extFromMime(mime: String?): String = when (mime) {
            "application/pdf" -> "pdf"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/msword" -> "doc"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "application/vnd.ms-powerpoint" -> "ppt"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
            "application/vnd.ms-excel" -> "xls"
            "text/plain" -> "txt"
            "text/csv" -> "csv"
            "application/rtf" -> "rtf"
            else -> android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "bin"
        }
    }
}
