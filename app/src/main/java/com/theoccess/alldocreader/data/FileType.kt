package com.theoccess.alldocreader.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.theoccess.alldocreader.R

enum class FileType(val extensions: Set<String>, @DrawableRes val icon: Int, val fallbackMime: String) {
    PDF(setOf("pdf"), R.drawable.ic_cat_pdf, "application/pdf"),
    WORD(setOf("doc", "docx", "docm", "dot", "dotx", "rtf", "odt"), R.drawable.ic_cat_word, "application/msword"),
    EXCEL(setOf("xls", "xlsx", "xlsm", "xlt", "xltx", "csv", "ods"), R.drawable.ic_cat_excel, "application/vnd.ms-excel"),
    PPT(setOf("ppt", "pptx", "pptm", "pps", "ppsx", "pot", "potx", "odp"), R.drawable.ic_cat_ppt, "application/vnd.ms-powerpoint"),
    TXT(setOf("txt", "log", "md", "json", "xml"), R.drawable.ic_cat_txt, "text/plain"),
    IMAGE(setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"), R.drawable.ic_cat_image, "image/*"),
    OTHER(emptySet(), R.drawable.ic_file_unknown, "*/*");

    companion object {
        private val byExt: Map<String, FileType> = entries.flatMap { t -> t.extensions.map { it to t } }.toMap()

        fun fromName(name: String): FileType {
            val ext = name.substringAfterLast('.', "").lowercase()
            return byExt[ext] ?: OTHER
        }

        /** Types shown in the app (everything except OTHER). */
        val supported = entries.filter { it != OTHER }
    }
}

/** The 8 tiles on the "All files" home screen. */
enum class Category(@StringRes val label: Int, @DrawableRes val icon: Int, val type: FileType?) {
    ALL(R.string.cat_all, R.drawable.ic_cat_all, null),
    PDF(R.string.cat_pdf, R.drawable.ic_cat_pdf, FileType.PDF),
    WORD(R.string.cat_word, R.drawable.ic_cat_word, FileType.WORD),
    EXCEL(R.string.cat_excel, R.drawable.ic_cat_excel, FileType.EXCEL),
    PPT(R.string.cat_ppt, R.drawable.ic_cat_ppt, FileType.PPT),
    TXT(R.string.cat_txt, R.drawable.ic_cat_txt, FileType.TXT),
    IMAGE(R.string.cat_image, R.drawable.ic_cat_image, FileType.IMAGE),
    DIRECTORIES(R.string.cat_directories, R.drawable.ic_cat_dir, null)
}
