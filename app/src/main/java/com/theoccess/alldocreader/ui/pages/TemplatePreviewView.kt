package com.theoccess.alldocreader.ui.pages

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View

/** A page preview: template ruling on the page colour, with the template name and size in the middle. */
class TemplatePreviewView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var spec: BlankSpec = BlankSpec(0, 420f, 595f, 0xFFFFFFFF.toInt())
        set(value) { field = value; requestLayout(); invalidate() }
    var title: String = ""
        set(value) { field = value; invalidate() }
    var subtitle: String = ""
        set(value) { field = value; invalidate() }

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF1B1F2A.toInt()
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8E949E.toInt()
        textAlign = Paint.Align.CENTER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = MeasureSpec.getSize(heightMeasureSpec)
        val maxW = MeasureSpec.getSize(widthMeasureSpec)
        var w = (h * spec.widthPt / spec.heightPt).toInt()
        var hh = h
        if (w > maxW && maxW > 0) { w = maxW; hh = (w * spec.heightPt / spec.widthPt).toInt() }
        setMeasuredDimension(w, hh)
    }

    override fun onDraw(canvas: Canvas) {
        val s = width / spec.widthPt
        canvas.save()
        canvas.scale(s, s)
        Templates.draw(canvas, spec)
        canvas.restore()
        val d = resources.displayMetrics.density
        titlePaint.textSize = 15 * d
        subPaint.textSize = 11 * d
        canvas.drawText(title, width / 2f, height / 2f, titlePaint)
        canvas.drawText(subtitle, width / 2f, height / 2f + 16 * d, subPaint)
    }
}
