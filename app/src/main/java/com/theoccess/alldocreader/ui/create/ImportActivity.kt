package com.theoccess.alldocreader.ui.create

import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.ui.scan.ProgressPill
import com.theoccess.alldocreader.ui.viewer.OpenDocumentActivity
import com.theoccess.alldocreader.ui.viewer.ViewerActivity
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Import files": opens the system file browser (Recent, Images, Audio, Drive, Downloads…).
 * The chosen file opens straight in the viewer, where it can be converted (Word ⇄ PDF) or
 * edited — no "All Document Reader" loading screen in between (that one is only for files
 * sent from other apps, see [OpenDocumentActivity]). A small "Loading…" pill shows only if
 * the file has to be copied first (Drive, e-mail attachments…).
 */
class ImportActivity : AppCompatActivity() {

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) finish() else open(uri)
    }

    private fun open(uri: Uri) {
        val pill = ProgressPill(this)
        lifecycleScope.launch {
            val showPill = launch { delay(350); pill.show(getString(R.string.loading)) }
            val file = withContext(Dispatchers.IO) {
                try { OpenDocumentActivity.resolve(applicationContext, uri) } catch (e: Exception) { null }
            }
            showPill.cancel()
            pill.dismiss()
            if (isFinishing || isDestroyed) return@launch
            if (file == null) {
                toast(R.string.import_failed)
                finish()
                return@launch
            }
            val doc = DocFile.from(file)
            LibraryStore.addRecent(doc.path)
            FileRepository.refresh(applicationContext, force = true)
            when {
                ViewerActivity.canOpen(doc) -> {
                    startActivity(ViewerActivity.intent(this@ImportActivity, file))
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
                doc.type != FileType.OTHER -> FileActions.open(this@ImportActivity, doc)
                else -> toast(R.string.unsupported_title)
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            picker.launch(
                arrayOf(
                    "application/pdf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.ms-excel",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/vnd.ms-powerpoint",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "text/*", "image/*", "application/rtf", "text/csv", "*/*"
                )
            )
        }
    }
}
