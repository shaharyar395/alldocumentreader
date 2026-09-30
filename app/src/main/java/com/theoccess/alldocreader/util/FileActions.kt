package com.theoccess.alldocreader.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.ui.files.FileSheets
import com.theoccess.alldocreader.ui.viewer.ViewerActivity

/** Open / share / info / bookmark actions for a document. */
object FileActions {

    private fun uriFor(context: Context, file: DocFile): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file.file)

    private fun mimeFor(file: DocFile): String {
        val ext = file.name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: file.type.fallbackMime
    }

    private fun ensureExists(context: Context, file: DocFile): Boolean {
        if (file.file.exists()) return true
        context.toast(R.string.file_not_found)
        LibraryStore.purge(file.path)
        FileRepository.forget(file.path)
        return false
    }

    /**
     * Opens a document: PDF, Word (.docx) and TXT open in the in-app viewer;
     * Excel / PowerPoint / images go to an installed app; anything else shows "File type not supported".
     */
    fun open(context: Context, file: DocFile) {
        if (!ensureExists(context, file)) return
        if (ViewerActivity.canOpen(file)) {
            LibraryStore.addRecent(file.path)
            val intent = ViewerActivity.intent(context, file.file)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
        if (file.type == FileType.OTHER) {
            unsupported(context)
            return
        }
        LibraryStore.addRecent(file.path)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, file), mimeFor(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (intent.resolveActivity(context.packageManager) == null &&
            context.packageManager.queryIntentActivities(intent, 0).isEmpty()
        ) {
            unsupported(context)
            return
        }
        try {
            context.startActivity(Intent.createChooser(intent, file.name))
        } catch (e: ActivityNotFoundException) {
            unsupported(context)
        } catch (e: IllegalArgumentException) {
            context.toast(R.string.no_app_to_open)
        }
    }

    private fun unsupported(context: Context) {
        val activity = context as? Activity
        if (activity == null || activity.isFinishing) context.toast(R.string.unsupported_title)
        else FileSheets.showUnsupported(activity)
    }

    fun share(context: Context, file: DocFile) {
        if (!ensureExists(context, file)) return
        val intent = Intent(Intent.ACTION_SEND)
            .setType(mimeFor(file))
            .putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
        } catch (e: Exception) {
            context.toast(R.string.no_app_to_open)
        }
    }

    fun toggleBookmark(context: Context, file: DocFile) {
        val added = LibraryStore.toggleBookmark(file.path)
        context.toast(if (added) R.string.bookmark_added else R.string.bookmark_removed)
    }

    fun showInfo(context: Context, file: DocFile) {
        val msg = buildString {
            append(context.getString(R.string.file_info_path)).append(": ").append(file.path).append("\n\n")
            append(context.getString(R.string.file_info_size)).append(": ").append(formatSize(file.size)).append("\n\n")
            append(context.getString(R.string.file_info_modified)).append(": ").append(formatDateTime(file.modified))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(file.name)
            .setMessage(msg)
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    /** Shares several files at once (selection mode). */
    fun shareMany(context: Context, files: List<DocFile>) {
        val existing = files.filter { it.file.exists() }
        if (existing.isEmpty()) return
        if (existing.size == 1) return share(context, existing[0])
        val uris = ArrayList(existing.map { uriFor(context, it) })
        val types = existing.map { mimeFor(it) }.distinct()
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(if (types.size == 1) types[0] else "*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
        } catch (e: Exception) {
            context.toast(R.string.no_app_to_open)
        }
    }

    /** The "⋮" menu for a file row: Share / Rename / To home screen / Delete. */
    @Suppress("UNUSED_PARAMETER")
    fun showMenu(context: Context, file: DocFile, inRecent: Boolean = false, onChanged: (() -> Unit)? = null) {
        FileSheets.showMenu(context, file, onChanged)
    }
}
