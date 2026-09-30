package com.theoccess.alldocreader.ui.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout

/**
 * Pinch / double-tap zoom for the page list. The child (RecyclerView) keeps scrolling vertically;
 * when zoomed in, horizontal drags pan the content.
 */
class ZoomLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    var scale = 1f
        private set
    private val minScale = 1f
    private val maxScale = 4f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            applyScale(scale * d.scaleFactor)
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            applyScale(if (scale > 1.2f) 1f else 2f)
            return true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scale > 1f) panBy(-dx)
            return false
        }
    })

    private fun applyScale(s: Float) {
        scale = s.coerceIn(minScale, maxScale)
        val child = getChildAt(0) ?: return
        child.pivotX = width / 2f
        child.pivotY = 0f
        child.scaleX = scale
        child.scaleY = scale
        panBy(0f)
    }

    private fun panBy(dx: Float) {
        val child = getChildAt(0) ?: return
        val limit = (scale - 1f) * width / 2f
        child.translationX = (child.translationX + dx).coerceIn(-limit, limit)
    }

    fun reset() {
        scale = 1f
        getChildAt(0)?.apply { scaleX = 1f; scaleY = 1f; translationX = 0f }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        gestureDetector.onTouchEvent(ev)
        if (scaleDetector.isInProgress) {
            // don't let the list scroll while pinching
            val cancel = MotionEvent.obtain(ev).apply { action = MotionEvent.ACTION_CANCEL }
            super.dispatchTouchEvent(cancel)
            cancel.recycle()
            return true
        }
        return super.dispatchTouchEvent(ev)
    }
}
