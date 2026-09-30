package com.theoccess.alldocreader.ui.scan

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/** Rule-of-thirds lines over the camera preview. */
class GridOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99FFFFFF.toInt()
        strokeWidth = resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in 1..2) {
            canvas.drawLine(w * i / 3, 0f, w * i / 3, h, paint)
            canvas.drawLine(0f, h * i / 3, w, h * i / 3, paint)
        }
    }
}
