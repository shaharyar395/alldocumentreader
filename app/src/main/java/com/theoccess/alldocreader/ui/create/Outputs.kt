package com.theoccess.alldocreader.ui.create

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import android.webkit.MimeTypeMap
import com.theoccess.alldocreader.ui.viewer.Converters
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Where created / imported files are saved: Documents/AllDocumentReader/<folder>. */
object Outputs {
    private fun base(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AllDocumentReader")

    fun createDir(): File = File(base(), "create").apply { mkdirs() }
    fun importDir(): File = File(base(), "import").apply { mkdirs() }

    /** "AllDocReader_09291552" like the original app. */
    fun defaultName(prefix: String = "AllDocReader"): String =
        prefix + "_" + SimpleDateFormat("MMddHHmm", Locale.US).format(Date())

    /** Removes characters that are not allowed in file names. */
    fun clean(name: String): String = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifEmpty { defaultName() }

    /**
     * Puts a finished file ([temp], e.g. in the cache) into Documents/AllDocumentReader/[sub] on the
     * phone and returns the saved file. It is copied straight into the folder when the app may write
     * there; otherwise (Android 10+ without "All files access") it goes in through MediaStore, which
     * needs no permission. [temp] is deleted afterwards.
     */
    fun publish(context: Context, temp: File, sub: String, name: String, ext: String): File {
        val dir = File(base(), sub)
        try {
            dir.mkdirs()
            val f = Converters.uniqueFile(dir, name, ext)
            temp.copyTo(f)
            temp.delete()
            return f
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return publishToMediaStore(context, temp, dir, sub, name, ext)
            throw e
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun publishToMediaStore(context: Context, temp: File, dir: File, sub: String, name: String, ext: String): File {
        val resolver = context.contentResolver
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.$ext")
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOCUMENTS}/AllDocumentReader/$sub")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IOException("MediaStore insert failed")
        try {
            (resolver.openOutputStream(uri) ?: throw IOException("no stream")).use { out -> temp.inputStream().use { it.copyTo(out) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        temp.delete()
        // the name MediaStore gave it ("name (1).jpg" if taken)
        val saved = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: "$name.$ext"
        return File(dir, saved)
    }
}
