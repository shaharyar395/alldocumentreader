package com.theoccess.alldocreader.ui.main

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.drawToBitmap
import com.theoccess.alldocreader.databinding.LayoutCoachMarkBinding
import com.theoccess.alldocreader.util.dp

/**
 * Full-screen scrim with a white rounded "spotlight" over the category grid,
 * a bouncing hand, a hint and an OK button.
 */
@SuppressLint("ViewConstructor")
class CoachMarkOverlay(
    context: Context,
    private val target: View,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val scrimColor = Color.parseColor("#8C000000")
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val hole = RectF()
    private val radius = context.dp(24).toFloat()
    private var snapshot: Bitmap? = null
    private var snapshotX = 0f
    private var snapshotY = 0f
    private val content = LayoutCoachMarkBinding.inflate(LayoutInflater.from(context), this, false)
    private var handAnim: ObjectAnimator? = null
    private var dismissed = false

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
        addView(content.root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
        content.btnOk.setOnClickListener { dismiss() }
        handAnim = ObjectAnimator.ofFloat(content.ivHand, View.TRANSLATION_Y, 0f, -context.dp(12).toFloat()).apply {
            duration = 650
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        computeHole()
        content.root.translationY = hole.bottom + context.dp(8)
    }

    private fun computeHole() {
        val me = IntArray(2).also { getLocationInWindow(it) }
        val t = IntArray(2).also { target.getLocationInWindow(it) }
        val x = (t[0] - me[0]).toFloat()
        val y = (t[1] - me[1]).toFloat()
        val m = context.dp(6).toFloat()
        hole.set(
            maxOf(x - m, context.dp(4).toFloat()),
            y - m,
            minOf(x + target.width + m, width - context.dp(4).toFloat()),
            y + target.height + m
        )
        if (snapshot == null && target.width > 0 && target.height > 0) {
            snapshot = try { target.drawToBitmap() } catch (e: Exception) { null }
        }
        snapshotX = x
        snapshotY = y
    }

    override fun dispatchDraw(canvas: Canvas) {
        val save = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        canvas.drawColor(scrimColor)
        canvas.drawRoundRect(hole, radius, radius, clearPaint)
        canvas.restoreToCount(save)
        // white spotlight card with a snapshot of the category grid on top
        canvas.drawRoundRect(hole, radius, radius, cardPaint)
        snapshot?.let { canvas.drawBitmap(it, snapshotX, snapshotY, null) }
        super.dispatchDraw(canvas)
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        handAnim?.cancel()
        animate().alpha(0f).setDuration(200).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
            snapshot?.recycle()
            snapshot = null
            onDismiss()
        }.start()
    }
}
