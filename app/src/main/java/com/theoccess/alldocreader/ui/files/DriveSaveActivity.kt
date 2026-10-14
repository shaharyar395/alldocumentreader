package com.theoccess.alldocreader.ui.files

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.util.toast
import java.io.File

/**
 * "Save to Drive" for one file (file ⋮ menu, viewer ⋮ menu): hands the file to the Google Drive
 * app's own "Save to Drive" screen — the user picks the Google account and folder and taps
 * Save, and Drive uploads it. No Google sign-in / Cloud setup is needed in this app.
 * Without the Drive app, the user is offered to install it.
 */
class DriveSaveActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    companion object {
        private const val EXTRA_PATH = "path"
        private const val DRIVE_PACKAGE = "com.google.android.apps.docs"

        fun start(context: Context, file: File) {
            context.startActivity(Intent(context, DriveSaveActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath))
        }
    }

    private lateinit var file: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = File(intent.getStringExtra(EXTRA_PATH) ?: "")
        if (!file.exists()) {
            toast(R.string.file_not_found)
            finish()
            return
        }
        if (savedInstanceState != null) return
        if (openDriveApp()) {
            finish()
            return
        }
        // no Google Drive app on this phone
        MaterialAlertDialogBuilder(this, R.style.Theme_DocReader_Dialog)
            .setTitle(R.string.drive_save_failed_title)
            .setMessage(R.string.drive_app_missing)
            .setPositiveButton(R.string.drive_install) { _, _ -> installDrive() }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }

    /** Sends the file to Drive's "Save to Drive" screen; false when the Drive app isn't installed. */
    private fun openDriveApp(): Boolean {
        val uri = try {
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            return false
        }
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_TITLE, file.name)
            .putExtra(Intent.EXTRA_SUBJECT, file.name)
            .setPackage(DRIVE_PACKAGE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        @Suppress("DEPRECATION")
        val targets = try { packageManager.queryIntentActivities(send, 0) } catch (e: Exception) { emptyList() }
        if (targets.isEmpty()) return false
        // Drive offers "Save to Drive" (upload) and "Copy to clipboard": take the upload one
        val upload = targets.firstOrNull { it.activityInfo.name.contains("Upload", ignoreCase = true) }
            ?: targets.firstOrNull { !it.activityInfo.name.contains("Clipboard", ignoreCase = true) }
            ?: return false
        send.setClassName(upload.activityInfo.packageName, upload.activityInfo.name)
        return try {
            startActivity(send)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun installDrive() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$DRIVE_PACKAGE")))
        } catch (e: Exception) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$DRIVE_PACKAGE")))
            } catch (ignored: Exception) {
            }
        }
    }
}
