package com.theoccess.alldocreader.data

import java.io.File

data class DocFile(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val type: FileType
) {
    val file: File get() = File(path)

    companion object {
        fun from(file: File): DocFile = DocFile(
            path = file.absolutePath,
            name = file.name,
            size = file.length(),
            modified = file.lastModified(),
            type = FileType.fromName(file.name)
        )
    }
}

/** Sorts for the "Filter by" sheet. Default is Date / Descending (newest first). */
fun List<DocFile>.sortedByMode(mode: SortMode, ascending: Boolean = false): List<DocFile> {
    val asc = when (mode) {
        SortMode.DATE -> sortedBy { it.modified }
        SortMode.NAME -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        SortMode.SIZE -> sortedBy { it.size }
    }
    return if (ascending) asc else asc.reversed()
}
