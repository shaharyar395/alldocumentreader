package com.theoccess.alldocreader.ui.edit

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

/** Draws editor items on a page canvas that is [w] pixels wide (shared by screen and export). */
object EditRenderer {

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()

    fun textLayout(t: TextItem, w: Float): StaticLayout {
        textPaint.textSize = max(1f, t.size * w)
        textPaint.color = t.color
        val width = max(1, (t.width * w).toInt())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(t.text, 0, t.text.length, TextPaint(textPaint), width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(t.text, TextPaint(textPaint), width, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false)
        }
    }

    /** Natural single-line width of [text] at [size] (page-width units). */
    fun measure(text: String, size: Float): Float {
        textPaint.textSize = 100f
        val widest = text.split('\n').maxOf { textPaint.measureText(it) }
        return widest / 100f * size * 1.04f + size * 0.1f
    }

    /** Box size of a text or image item in pixels. */
    fun boxSize(item: EditItem, w: Float): Pair<Float, Float> = when (item) {
        is TextItem -> item.width * w to textLayout(item, w).height.toFloat()
        is ImageItem -> item.width * w to item.height * w
        else -> 0f to 0f
    }

    fun center(item: EditItem, w: Float): Pair<Float, Float> = when (item) {
        is TextItem -> item.cx * w to item.cy * w
        is ImageItem -> item.cx * w to item.cy * w
        else -> 0f to 0f
    }

    fun rotation(item: EditItem) = when (item) {
        is TextItem -> item.rotation
        is ImageItem -> item.rotation
        else -> 0f
    }

    fun draw(canvas: Canvas, items: List<EditItem>, w: Float) {
        // white-out under edited text first
        fill.color = 0xFFFFFFFF.toInt()
        fill.alpha = 255
        items.forEach { if (it is TextItem && it.cover != null) {
            val c = it.cover
            canvas.drawRect(c.left * w, c.top * w, c.right * w, c.bottom * w, fill)
        } }
        items.forEach { drawItem(canvas, it, w) }
    }

    fun drawItem(canvas: Canvas, item: EditItem, w: Float) {
        when (item) {
            is MarkItem -> {
                val r = item.rect
                when (item.kind) {
                    MarkKind.HIGHLIGHT -> {
                        fill.color = item.color
                        fill.alpha = 90
                        canvas.drawRect(r.left * w, r.top * w, r.right * w, r.bottom * w, fill)
                    }
                    MarkKind.UNDERLINE, MarkKind.STRIKE -> {
                        stroke.color = item.color
                        stroke.alpha = 255
                        stroke.strokeWidth = max(1.5f, (r.bottom - r.top) * w * 0.09f)
                        val y = if (item.kind == MarkKind.UNDERLINE) r.bottom * w else (r.top + r.bottom) / 2 * w
                        canvas.drawLine(r.left * w, y, r.right * w, y, stroke)
                    }
                }
            }
            is InkItem -> {
                val p = item.points
                if (p.size < 2) return
                stroke.color = item.color
                stroke.alpha = 255
                stroke.strokeWidth = max(1f, item.width * w)
                path.reset()
                path.moveTo(p[0] * w, p[1] * w)
                if (p.size == 2) path.lineTo(p[0] * w + 0.1f, p[1] * w)
                var i = 2
                while (i + 1 < p.size) { path.lineTo(p[i] * w, p[i + 1] * w); i += 2 }
                canvas.drawPath(path, stroke)
            }
            is TextItem -> {
                val layout = textLayout(item, w)
                canvas.save()
                canvas.translate(item.cx * w, item.cy * w)
                canvas.rotate(item.rotation)
                canvas.translate(-item.width * w / 2, -layout.height / 2f)
                layout.draw(canvas)
                canvas.restore()
            }
            is ImageItem -> {
                val bw = item.width * w
                val bh = item.height * w
                canvas.save()
                canvas.translate(item.cx * w, item.cy * w)
                canvas.rotate(item.rotation)
                canvas.drawBitmap(item.bitmap, null, RectF(-bw / 2, -bh / 2, bw / 2, bh / 2), bmpPaint)
                canvas.restore()
            }
        }
    }

    // ------------------------------------------------------------------ selection handles

    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
    }
    private val dashDark = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x99000000.toInt() }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handleIcon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Handle positions in the item's local (unrotated, centred) coordinates. */
    /**
     * Handles around a selected item, like the original: × delete (top-left), duplicate (bottom-left),
     * resize + rotate (bottom-right); text also has side dots for the box width, pictures and
     * signatures a pencil (top-right) to replace them.
     */
    fun handles(item: EditItem, w: Float, pad: Float): Map<String, Pair<Float, Float>> {
        val (bw, bh) = boxSize(item, w)
        val l = -bw / 2 - pad; val r = bw / 2 + pad; val t = -bh / 2 - pad; val b = bh / 2 + pad
        val m = mutableMapOf("delete" to (l to t), "copy" to (l to b), "scale" to (r to b))
        if (item is TextItem) { m["left"] = l to 0f; m["right"] = r to 0f }
        if (item is ImageItem) m["edit"] = r to t
        return m
    }

    private val boxLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF1E6FE6.toInt() }
    private val handleRing = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33000000 }

    fun drawSelection(canvas: Canvas, item: EditItem, w: Float, density: Float) {
        val pad = 6 * density
        val (bw, bh) = boxSize(item, w)
        val (cx, cy) = center(item, w)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(rotation(item))
        val box = RectF(-bw / 2 - pad, -bh / 2 - pad, bw / 2 + pad, bh / 2 + pad)
        if (item is ImageItem) {
            boxLine.strokeWidth = 1.2f * density
            canvas.drawRect(box, boxLine)
        } else {
            dashDark.strokeWidth = 1.2f * density
            dashDark.pathEffect = DashPathEffect(floatArrayOf(5 * density, 4 * density), 0f)
            dash.strokeWidth = 1.2f * density
            dash.pathEffect = DashPathEffect(floatArrayOf(5 * density, 4 * density), 5 * density)
            canvas.drawRect(box, dashDark)
            canvas.drawRect(box, dash)
        }
        val r = 9 * density
        handleIcon.strokeWidth = 1.5f * density
        handleRing.strokeWidth = 1f * density
        handles(item, w, pad).forEach { (name, pos) ->
            val (x, y) = pos
            val k = r * 0.45f
            if (name == "left" || name == "right") {
                handleFill.color = 0xFFFFFFFF.toInt()
                canvas.drawCircle(x, y, 4 * density, handleFill)
                canvas.drawCircle(x, y, 4 * density, handleRing)
                return@forEach
            }
            handleFill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(x, y, r, handleFill)
            canvas.drawCircle(x, y, r, handleRing)
            handleIcon.color = 0xFF1B1F2A.toInt()
            when (name) {
                "delete" -> {
                    handleIcon.color = 0xFFF0483E.toInt()
                    val d = r * 0.38f
                    canvas.drawLine(x - d, y - d, x + d, y + d, handleIcon)
                    canvas.drawLine(x + d, y - d, x - d, y + d, handleIcon)
                }
                "copy" -> {
                    val s = k * 1.1f
                    canvas.drawRoundRect(RectF(x - s * 0.9f, y - s * 0.4f, x + s * 0.4f, y + s * 0.9f), 1.5f * density, 1.5f * density, handleIcon)
                    canvas.drawLine(x - s * 0.4f, y - s * 0.9f, x + s * 0.9f, y - s * 0.9f, handleIcon)
                    canvas.drawLine(x + s * 0.9f, y - s * 0.9f, x + s * 0.9f, y + s * 0.4f, handleIcon)
                }
                "edit" -> {
                    canvas.drawLine(x - k, y + k, x + k * 0.8f, y - k * 0.8f, handleIcon)
                    canvas.drawLine(x - k, y + k, x - k * 0.55f, y + k * 0.1f, handleIcon)
                    canvas.drawLine(x - k, y + k, x - k * 0.1f, y + k * 0.55f, handleIcon)
                }
                "scale" -> if (item is ImageItem) {
                    // circular arrows: rotate + resize
                    canvas.drawArc(RectF(x - k, y - k, x + k, y + k), 200f, 130f, false, handleIcon)
                    canvas.drawArc(RectF(x - k, y - k, x + k, y + k), 20f, 130f, false, handleIcon)
                    canvas.drawLine(x + k * 0.5f, y - k * 0.87f, x + k * 0.95f, y - k * 0.95f, handleIcon)
                    canvas.drawLine(x - k * 0.5f, y + k * 0.87f, x - k * 0.95f, y + k * 0.95f, handleIcon)
                } else {
                    canvas.drawLine(x - k, y - k, x + k, y + k, handleIcon)
                    canvas.drawLine(x + k, y + k, x + k * 0.1f, y + k, handleIcon)
                    canvas.drawLine(x + k, y + k, x + k, y + k * 0.1f, handleIcon)
                    canvas.drawLine(x - k, y - k, x - k * 0.1f, y - k, handleIcon)
                    canvas.drawLine(x - k, y - k, x - k, y - k * 0.1f, handleIcon)
                }
            }
        }
        canvas.restore()
    }

    /** Point in the item's local frame (unrotated, centre = 0,0). */
    fun toLocal(item: EditItem, w: Float, x: Float, y: Float): Pair<Float, Float> {
        val (cx, cy) = center(item, w)
        val m = Matrix().apply { setRotate(-rotation(item)) }
        val pts = floatArrayOf(x - cx, y - cy)
        m.mapPoints(pts)
        return pts[0] to pts[1]
    }
}
