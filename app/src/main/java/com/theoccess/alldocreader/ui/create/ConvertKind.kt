package com.theoccess.alldocreader.ui.create

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileType

/** The file conversions offered in "Convert format". */
enum class ConvertKind(
    @StringRes val title: Int,
    @StringRes val pickHint: Int,
    @DrawableRes val icon: Int,
    val sourceType: FileType,
    val targetExt: String
) {
    WORD_TO_PDF(R.string.word_to_pdf, R.string.pick_word_hint, R.drawable.ic_cv_word2pdf, FileType.WORD, "pdf"),
    PDF_TO_WORD(R.string.pdf_to_word, R.string.pick_pdf_hint, R.drawable.ic_cv_pdf2word, FileType.PDF, "docx"),
    PPT_TO_PDF(R.string.ppt_to_pdf, R.string.pick_ppt_hint, R.drawable.ic_cv_ppt2pdf, FileType.PPT, "pdf"),
    IMAGE_TO_PDF(R.string.image_to_pdf, R.string.pick_image_hint, R.drawable.ic_cv_image2pdf, FileType.IMAGE, "pdf");

    /** Formats our converters can actually read (old binary .doc / .ppt are not supported). */
    fun canConvert(doc: DocFile): Boolean {
        val ext = doc.name.substringAfterLast('.', "").lowercase()
        return when (this) {
            WORD_TO_PDF -> ext in setOf("docx", "docm", "dotx", "txt")
            PDF_TO_WORD -> ext == "pdf"
            PPT_TO_PDF -> ext in setOf("pptx", "ppsx", "potx")
            IMAGE_TO_PDF -> ext in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif")
        }
    }

    /** Files listed in the picker (TXT is offered for Word to PDF too). */
    fun matches(doc: DocFile): Boolean =
        doc.type == sourceType || (this == WORD_TO_PDF && doc.type == FileType.TXT)
}
