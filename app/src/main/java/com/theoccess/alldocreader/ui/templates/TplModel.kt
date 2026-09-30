package com.theoccess.alldocreader.ui.templates

import android.graphics.RectF

/*
 * An editable template page: shapes (fixed decoration), texts and pictures, positioned in
 * PDF points on an A4 page (595 × 842). Texts and pictures can be selected, moved, turned,
 * resized, restyled, duplicated and deleted in the template editor.
 */

const val PAGE_W = 595f
const val PAGE_H = 842f

enum class Category { RESUME, LETTER, BRIEFING, POSTER }

sealed class TEl {
    abstract var x: Float
    abstract var y: Float
    abstract var rotation: Float
    abstract fun copyEl(): TEl
}

/** Fixed decoration (background blocks, lines, circles); not selectable. */
class TShape(
    val kind: Kind,
    override var x: Float,
    override var y: Float,
    val w: Float,
    val h: Float,
    val color: Int,
    val color2: Int? = null,      // gradient end (top → bottom, or left → right when [horizontal])
    val horizontal: Boolean = false,
    val radius: Float = 0f,
    val stroke: Float = 0f,        // > 0 = outline only
    override var rotation: Float = 0f
) : TEl() {
    enum class Kind { RECT, OVAL, LINE }
    override fun copyEl() = TShape(kind, x, y, w, h, color, color2, horizontal, radius, stroke, rotation)
}

/** A text box of width [w]; its height follows the text. */
class TText(
    var text: String,
    override var x: Float,
    override var y: Float,
    var w: Float,
    var size: Float,
    var color: Int = 0xFF1B1F2A.toInt(),
    var font: String = TplFonts.DEFAULT,
    var bold: Boolean = false,
    var italic: Boolean = false,
    var underline: Boolean = false,
    var strike: Boolean = false,
    /** 0 = none, 1 = bullets, 2 = numbers */
    var list: Int = 0,
    /** 0 = left, 1 = centre, 2 = right */
    var align: Int = 0,
    var lineMult: Float = 1.2f,
    override var rotation: Float = 0f
) : TEl() {
    override fun copyEl() = TText(text, x, y, w, size, color, font, bold, italic, underline, strike, list, align, lineMult, rotation)
}

/** A picture: a built-in drawable ([res] = drawable name) or a picked photo ([path]). */
class TImage(
    var res: String?,
    var path: String?,
    override var x: Float,
    override var y: Float,
    var w: Float,
    var h: Float,
    override var rotation: Float = 0f,
    var flipH: Boolean = false,
    var flipV: Boolean = false,
    /** Visible part of the picture as fractions (Crop). */
    var crop: RectF = RectF(0f, 0f, 1f, 1f),
    var circle: Boolean = false,
    var corner: Float = 0f
) : TEl() {
    override fun copyEl() = TImage(res, path, x, y, w, h, rotation, flipH, flipV, RectF(crop), circle, corner)
}

class TPage(val bg: Int, val elements: MutableList<TEl>) {
    fun copyPage() = TPage(bg, elements.map { it.copyEl() }.toMutableList())
}

class Template(val id: String, val category: Category, val name: String, val build: () -> TPage)
