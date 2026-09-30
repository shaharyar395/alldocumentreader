package com.theoccess.alldocreader.data

import android.content.Context
import android.media.MediaScannerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Rename / delete operations that keep the cache, recents and bookmarks in sync. */
object FileOps {

    sealed class RenameResult {
        data class Success(val file: DocFile) : RenameResult()
        object Empty : RenameResult()
        object Invalid : RenameResult()
        object Exists : RenameResult()
        object Failed : RenameResult()
    }

    data class DeleteResult(val done: Int, val failed: Int)

    private val INVALID_CHARS = Regex("[\\\\/:*?\"<>|]")

    /** Base name without extension, as shown in the Rename box. */
    fun baseName(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) else name
    }

    private fun extension(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(dot) else ""
    }

    /** Renames keeping the original extension. Must be called from a coroutine. */
    suspend fun rename(context: Context, doc: DocFile, newBase: String): RenameResult {
        val base = newBase.trim()
        if (base.isEmpty()) return RenameResult.Empty
        if (INVALID_CHARS.containsMatchIn(base) || base == "." || base == "..") return RenameResult.Invalid
        val newName = base + extension(doc.name)
        if (newName == doc.name) return RenameResult.Success(doc)
        val result = withContext(Dispatchers.IO) {
            val src = doc.file
            val dst = File(src.parentFile, newName)
            when {
                dst.exists() -> RenameResult.Exists
                !src.exists() -> RenameResult.Failed
                src.renameTo(dst) -> {
                    scan(context, listOf(src.absolutePath, dst.absolutePath))
                    RenameResult.Success(DocFile.from(dst))
                }
                else -> RenameResult.Failed
            }
        }
        if (result is RenameResult.Success) {
            FileRepository.replace(doc.path, result.file)
            LibraryStore.renamePath(doc.path, result.file.path)
        }
        return result
    }

    /** Deletes files directly or moves them to the recycle bin. Must be called from a coroutine. */
    suspend fun delete(context: Context, docs: List<DocFile>, toRecycleBin: Boolean): DeleteResult {
        val removed = withContext(Dispatchers.IO) {
            val ok = mutableListOf<String>()
            docs.forEach { d ->
                val f = d.file
                val success = if (toRecycleBin) RecycleBin.moveToBin(context, f) else (!f.exists() || f.delete())
                if (success) ok += d.path
            }
            if (ok.isNotEmpty()) scan(context, ok)
            ok
        }
        FileRepository.forgetAll(removed.toSet())
        removed.forEach { LibraryStore.purge(it) }
        return DeleteResult(removed.size, docs.size - removed.size)
    }

    /** Lets MediaStore know files changed so other apps (and our next scan) see it. */
    private fun scan(context: Context, paths: List<String>) {
        try {
            MediaScannerConnection.scanFile(context.applicationContext, paths.toTypedArray(), null, null)
        } catch (ignored: Exception) {
        }
    }
}
