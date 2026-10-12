package com.theoccess.alldocreader.ui.viewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/**
 * Search highlights drawn over a page image: every match in yellow, the current one in orange
 * with a border. Boxes are in page points; [pageWidth] / [pageHeight] map them onto [target]
 * (the page picture), which may be smaller than this view (page-by-page mode).
 */
class HighlightView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var target: View? = null
    var pageWidth = 1f
    var pageHeight = 1f
    /** Quarter turns the page picture is rotated by (0 = upright). Highlights are hidden otherwise. */
    var rotation90 = 0
    private var all: List<RectF> = emptyList()
    private var current: List<RectF> = emptyList()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFD400 }
    private val currentFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88FF8A00.toInt() }
    private val currentStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFE65100.toInt()
        strokeWidth = 1.5f * resources.displayMetrics.density
    }

    fun set(all: List<RectF>, current: List<RectF>) {
        this.all = all
        this.current = current
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if ((all.isEmpty() && current.isEmpty()) || rotation90 % 4 != 0) return
        val t = target ?: return
        val w = t.width.toFloat()
        val h = t.height.toFloat()
        if (w <= 0f || h <= 0f) return
        val sx = w / pageWidth
        val sy = h / pageHeight
        val pad = 1.5f * resources.displayMetrics.density
        canvas.save()
        // both views sit in the same padded page frame: only their difference counts
        // (translating by the page's own left/top added the frame padding twice → boxes too low)
        canvas.translate((t.left - left).toFloat(), (t.top - top).toFloat())
        val r = RectF()
        for (b in all) {
            r.set(b.left * sx - pad, b.top * sy - pad, b.right * sx + pad, b.bottom * sy + pad)
            canvas.drawRoundRect(r, pad, pad, fill)
        }
        for (b in current) {
            r.set(b.left * sx - pad, b.top * sy - pad, b.right * sx + pad, b.bottom * sy + pad)
            canvas.drawRoundRect(r, pad, pad, currentFill)
            canvas.drawRoundRect(r, pad, pad, currentStroke)
        }
        canvas.restore()
    }
}
