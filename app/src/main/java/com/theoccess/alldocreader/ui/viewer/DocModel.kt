package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.text.Layout

/** Page size and margins in points (from the Word section properties). */
data class PageGeometry(
    val width: Float = 595f,
    val height: Float = 842f,
    val left: Float = 54f,
    val top: Float = 54f,
    val right: Float = 54f,
    val bottom: Float = 54f
) {
    companion object {
        val DEFAULT = PageGeometry()
    }
}

/** A picture positioned on the page instead of in the text flow (Word "anchor"). */
data class Anchor(
    val x: Float,
    val y: Float,
    /** page | margin | column | character */
    val relH: String,
    /** page | margin | paragraph | line */
    val relV: String,
    /** left | center | right (when Word uses alignment instead of an offset) */
    val alignH: String? = null,
    val alignV: String? = null,
    /** true = text flows over/under it (behind / in front, no wrapping) */
    val noWrap: Boolean = true,
    val behind: Boolean = false
)

/** Content blocks extracted from a Word or TXT file, laid out by [DocLayout]. */
sealed class DocBlock {
    class Para(
        val text: CharSequence,
        val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        val spaceBefore: Float = 0f,
        val spaceAfter: Float = 6f,
        val indent: Float = 0f,
        val lineMult: Float = 1.0f,
        val shading: Int? = null
    ) : DocBlock()

    class Image(
        val bitmap: Bitmap,
        val widthPt: Float,
        val heightPt: Float,
        val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        val anchor: Anchor? = null
    ) : DocBlock()

    /** [colWidths] are fractions of the content width (sum ≈ 1). */
    class Table(val rows: List<List<CharSequence>>, val colWidths: List<Float>? = null) : DocBlock()

    object PageBreak : DocBlock()
}

class ParsedDoc(val blocks: List<DocBlock>, val geometry: PageGeometry)
