package com.theoccess.alldocreader.ui.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Crop editor: the page picture with a blue frame; drag the round corner handles (any shape,
 * the page is straightened later) or the bar handles in the middle of each side.
 * Corners are kept as 0..1 fractions of the picture: TL, TR, BR, BL.
 */
class CropView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private val d = resources.displayMetrics.density
    private var bitmap: Bitmap? = null
    private val imageRect = RectF()
    var quad: FloatArray = ImageOps.FULL.copyOf()
        private set

    /** Called after the person moves a handle. */
    var onChanged: (() -> Unit)? = null

    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint().apply { color = 0x55000000 }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = 0xFF1E6FE6.toInt()
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
        color = 0xFF1E6FE6.toInt()
    }
    private val path = Path()

    private var dragCorner = -1
    private var dragEdge = -1
    private var lastX = 0f
    private var lastY = 0f

    fun setImage(bmp: Bitmap?, q: FloatArray) {
        bitmap = bmp
        quad = q.copyOf()
        layoutImage()
        invalidate()
    }

    fun setQuad(q: FloatArray) {
        quad = q.copyOf()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        layoutImage()
    }

    private fun layoutImage() {
        val bmp = bitmap ?: return
        val pad = 22f * d
        val aw = width - 2 * pad
        val ah = height - 2 * pad
        if (aw <= 0 || ah <= 0) return
        val s = min(aw / bmp.width, ah / bmp.height)
        val w = bmp.width * s
        val h = bmp.height * s
        imageRect.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
    }

    private fun px(i: Int) = imageRect.left + quad[i * 2] * imageRect.width()
    private fun py(i: Int) = imageRect.top + quad[i * 2 + 1] * imageRect.height()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        canvas.drawBitmap(bmp, null, imageRect, bmpPaint)
        path.reset()
        path.moveTo(px(0), py(0))
        for (i in 1..3) path.lineTo(px(i), py(i))
        path.close()
        // light shade outside the frame
        canvas.save()
        canvas.clipRect(imageRect)
        val outside = Path().apply {
            addRect(imageRect, Path.Direction.CW)
            addPath(path)
            fillType = Path.FillType.EVEN_ODD
        }
        if (!ImageOps.isFull(quad)) canvas.drawPath(outside, shade)
        canvas.restore()
        canvas.drawPath(path, line)
        // side bars
        for (e in 0..3) {
            val a = e; val b = (e + 1) % 4
            val cx = (px(a) + px(b)) / 2; val cy = (py(a) + py(b)) / 2
            val ang = Math.toDegrees(atan2((py(b) - py(a)).toDouble(), (px(b) - px(a)).toDouble())).toFloat()
            canvas.save()
            canvas.rotate(ang, cx, cy)
            val r = RectF(cx - 11 * d, cy - 4 * d, cx + 11 * d, cy + 4 * d)
            canvas.drawRoundRect(r, 4 * d, 4 * d, handleFill)
            canvas.drawRoundRect(r, 4 * d, 4 * d, handleStroke)
            canvas.restore()
        }
        for (i in 0..3) {
            canvas.drawCircle(px(i), py(i), 8 * d, handleFill)
            canvas.drawCircle(px(i), py(i), 8 * d, handleStroke)
        }
        drawLoupe(canvas, bmp)
    }

    private val loupePath = Path()
    private val loupeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        color = 0xFFFFFFFF.toInt()
    }
    private val loupeShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val loupeLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF1E6FE6.toInt()
    }

    /** While a handle is dragged: a round magnifier (top-left, or top-right if the finger is there). */
    private fun drawLoupe(canvas: Canvas, bmp: Bitmap) {
        val (fx, fy) = when {
            dragCorner >= 0 -> px(dragCorner) to py(dragCorner)
            dragEdge >= 0 -> {
                val j = (dragEdge + 1) % 4
                (px(dragEdge) + px(j)) / 2 to (py(dragEdge) + py(j)) / 2
            }
            else -> return
        }
        val r = 46 * d
        val margin = 10 * d
        var cx = margin + r
        val cy = margin + r
        if (fx < cx + r * 1.4f && fy < cy + r * 1.4f) cx = width - margin - r
        val zoom = 2.2f
        canvas.drawCircle(cx, cy + 2 * d, r + 1 * d, loupeShadow)
        canvas.save()
        loupePath.reset()
        loupePath.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.clipPath(loupePath)
        canvas.drawColor(0xFFF3F4FA.toInt())
        canvas.translate(cx, cy)
        canvas.scale(zoom, zoom)
        canvas.translate(-fx, -fy)
        canvas.drawBitmap(bmp, null, imageRect, bmpPaint)
        loupeLine.strokeWidth = 2f * d / zoom
        canvas.drawPath(path, loupeLine)
        canvas.restore()
        canvas.drawCircle(cx, cy, r, loupeBorder)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (bitmap == null) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragCorner = -1; dragEdge = -1
                val touch = 30 * d
                var best = touch
                for (i in 0..3) {
                    val dist = hypot(e.x - px(i), e.y - py(i))
                    if (dist < best) { best = dist; dragCorner = i }
                }
                if (dragCorner < 0) {
                    for (i in 0..3) {
                        val j = (i + 1) % 4
                        val dist = hypot(e.x - (px(i) + px(j)) / 2, e.y - (py(i) + py(j)) / 2)
                        if (dist < best) { best = dist; dragEdge = i }
                    }
                }
                lastX = e.x; lastY = e.y
                if (dragCorner >= 0 || dragEdge >= 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    invalidate()
                }
                return dragCorner >= 0 || dragEdge >= 0
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.x - lastX) / imageRect.width()
                val dy = (e.y - lastY) / imageRect.height()
                lastX = e.x; lastY = e.y
                if (dragCorner >= 0) move(dragCorner, dx, dy)
                else if (dragEdge >= 0) { move(dragEdge, dx, dy); move((dragEdge + 1) % 4, dx, dy) }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val moved = dragCorner >= 0 || dragEdge >= 0
                dragCorner = -1; dragEdge = -1
                invalidate()
                if (moved) onChanged?.invoke()
                return true
            }
        }
        return false
    }

    private fun move(i: Int, dx: Float, dy: Float) {
        quad[i * 2] = (quad[i * 2] + dx).coerceIn(0f, 1f)
        quad[i * 2 + 1] = (quad[i * 2 + 1] + dy).coerceIn(0f, 1f)
    }
}
