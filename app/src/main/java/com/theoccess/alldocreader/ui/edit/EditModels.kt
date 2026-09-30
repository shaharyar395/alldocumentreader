package com.theoccess.alldocreader.ui.edit

import android.graphics.Bitmap
import android.graphics.RectF

/*
 * Everything placed on a page in the PDF editor. Coordinates are in "page-width units":
 * x and y are both divided by the page's width, so 1.0 = the page width in either direction
 * and shapes keep their proportions at any zoom.
 */

sealed class EditItem {
    abstract fun copy(): EditItem
}

/** Typed text in a box of [width]; [size] = font size; [cover] = white-out behind it (Edit text). */
class TextItem(
    var text: String,
    var cx: Float,
    var cy: Float,
    var width: Float,
    var size: Float,
    var color: Int,
    var rotation: Float = 0f,
    val cover: RectF? = null
) : EditItem() {
    override fun copy() = TextItem(text, cx, cy, width, size, color, rotation, cover?.let { RectF(it) })
}

/** A picture or a signature. Height follows the bitmap's proportions. */
class ImageItem(
    var bitmap: Bitmap,
    var cx: Float,
    var cy: Float,
    var width: Float,
    var rotation: Float = 0f,
    val signature: Boolean = false
) : EditItem() {
    val height: Float get() = width * bitmap.height / bitmap.width.coerceAtLeast(1)
    override fun copy() = ImageItem(bitmap, cx, cy, width, rotation, signature)
}

/** Free-hand pen line. */
class InkItem(val points: FloatArray, val color: Int, val width: Float) : EditItem() {
    override fun copy() = InkItem(points.copyOf(), color, width)
}

enum class MarkKind { HIGHLIGHT, UNDERLINE, STRIKE }

/** Highlight / underline / strikethrough over a span of the page. */
class MarkItem(val kind: MarkKind, val rect: RectF, val color: Int) : EditItem() {
    override fun copy() = MarkItem(kind, RectF(rect), color)
}

/** A line of real text found in the PDF (for Edit text), in page-width units. */
class TextLine(val text: String, val rect: RectF, val size: Float)
