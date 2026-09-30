package com.theoccess.alldocreader.ui.viewer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Shows a picture fitted to the screen; pinch or double-tap to zoom, drag to move around. */
class ZoomImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var bitmap: Bitmap? = null
        set(v) { field = v; zoom = 1f; panX = 0f; panY = 0f; invalidate() }

    /** Called on a single tap (the screen shows / hides its bars). */
    var onTap: (() -> Unit)? = null

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var anim: ValueAnimator? = null

    private fun fit(): Float {
        val b = bitmap ?: return 1f
        return min(width.toFloat() / b.width, height.toFloat() / b.height)
    }

    private fun shownRect(): RectF {
        val b = bitmap ?: return RectF()
        val s = fit() * zoom
        val w = b.width * s; val h = b.height * s
        val l = (width - w) / 2f + panX
        val t = (height - h) / 2f + panY
        return RectF(l, t, l + w, t + h)
    }

    private fun clamp() {
        val b = bitmap ?: return
        val s = fit() * zoom
        val mx = max(0f, (b.width * s - width) / 2f)
        val my = max(0f, (b.height * s - height) / 2f)
        panX = panX.coerceIn(-mx, mx)
        panY = panY.coerceIn(-my, my)
    }

    override fun onDraw(c: Canvas) {
        val b = bitmap ?: return
        val r = shownRect()
        m.reset()
        m.postScale(r.width() / b.width, r.height() / b.height)
        m.postTranslate(r.left, r.top)
        c.drawBitmap(b, m, paint)
    }

    private fun zoomAround(newZoom: Float, fx: Float, fy: Float) {
        val old = zoom
        val z = newZoom.coerceIn(1f, 6f)
        // keep the point under (fx, fy) in place
        val cx = width / 2f + panX
        val cy = height / 2f + panY
        val f = z / old
        panX = fx - (fx - cx) * f - width / 2f
        panY = fy - (fy - cy) * f - height / 2f
        zoom = z
        clamp()
        invalidate()
    }

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            anim?.cancel()
            zoomAround(zoom * d.scaleFactor, d.focusX, d.focusY)
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (zoom <= 1f) return false
            panX -= dx; panY -= dy
            clamp(); invalidate()
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean { onTap?.invoke(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val from = zoom
            val to = if (zoom > 1.2f) 1f else 2.5f
            anim?.cancel()
            anim = ValueAnimator.ofFloat(from, to).apply {
                duration = 220
                addUpdateListener { zoomAround(it.animatedValue as Float, e.x, e.y) }
                start()
            }
            return true
        }
    })

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (zoom > 1f || e.pointerCount > 1) parent?.requestDisallowInterceptTouchEvent(true)
        scaler.onTouchEvent(e)
        if (!scaler.isInProgress) gestures.onTouchEvent(e)
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        clamp()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }
}
