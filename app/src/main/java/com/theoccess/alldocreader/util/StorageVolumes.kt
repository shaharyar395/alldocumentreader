package com.theoccess.alldocreader.util

import android.content.Context
import android.os.Environment
import java.io.File

/** Root folders of the phone storage ("0") and any SD card / USB volume ("0000-000C"). */
object StorageVolumes {

    fun roots(context: Context): List<File> {
        val result = LinkedHashSet<File>()
        result += Environment.getExternalStorageDirectory()
        context.getExternalFilesDirs(null).filterNotNull().forEach { f ->
            val path = f.absolutePath
            val idx = path.indexOf("/Android/data/")
            if (idx > 0) result += File(path.substring(0, idx))
        }
        return result.filter { it.exists() && it.canRead() }
    }

    /** Name as the original app shows it: the last path segment (e.g. "0"). */
    fun displayName(root: File): String = root.name.ifEmpty { root.absolutePath }

    fun rootOf(context: Context, file: File): File? =
        roots(context).firstOrNull { file.absolutePath.startsWith(it.absolutePath) }
}
