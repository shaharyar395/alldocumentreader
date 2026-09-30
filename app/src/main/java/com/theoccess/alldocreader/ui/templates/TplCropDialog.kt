package com.theoccess.alldocreader.ui.templates

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.DialogTplCropBinding
import kotlin.math.abs
import kotlin.math.min

/** Crop for a picture on a template: drag the corners / sides of the frame, then ✓. */
object TplCropDialog {
    fun show(context: Context, bmp: Bitmap, crop: RectF, onDone: (RectF) -> Unit) {
        val dialog = Dialog(context, R.style.Theme_DocReader_TextEntry)
        val b = DialogTplCropBinding.inflate(LayoutInflater.from(context))
        val view = CropFrameView(context, bmp, RectF(crop))
        b.cropHost.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        b.btnClose.setOnClickListener { dialog.dismiss() }
        b.btnReset.setOnClickListener { view.reset() }
        b.btnOk.setOnClickListener {
            dialog.dismiss()
            onDone(RectF(view.crop))
        }
        dialog.setContentView(b.root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
    }
}

private class CropFrameView(context: Context, private val bmp: Bitmap, val crop: RectF) : View(context) {

    private val d = resources.displayMetrics.density
    private val img = RectF()
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint().apply { color = 0x99000000.toInt() }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 2 * d }
    private val grid = Paint().apply { color = 0x66FFFFFF; strokeWidth = d }
    private val corner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt(); strokeWidth = 4 * d; strokeCap = Paint.Cap.ROUND }

    fun reset() { crop.set(0f, 0f, 1f, 1f); invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val m = 24 * d
        val s = min((w - 2 * m) / bmp.width, (h - 2 * m) / bmp.height)
        val iw = bmp.width * s; val ih = bmp.height * s
        img.set((w - iw) / 2, (h - ih) / 2, (w + iw) / 2, (h + ih) / 2)
    }

    private fun frameRect() = RectF(
        img.left + crop.left * img.width(), img.top + crop.top * img.height(),
        img.left + crop.right * img.width(), img.top + crop.bottom * img.height()
    )

    override fun onDraw(c: Canvas) {
        c.drawBitmap(bmp, null, img, bmpPaint)
        val r = frameRect()
        val p = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(img, Path.Direction.CW)
            addRect(r, Path.Direction.CW)
        }
        c.drawPath(p, dim)
        for (i in 1..2) {
            c.drawLine(r.left + r.width() * i / 3, r.top, r.left + r.width() * i / 3, r.bottom, grid)
            c.drawLine(r.left, r.top + r.height() * i / 3, r.right, r.top + r.height() * i / 3, grid)
        }
        c.drawRect(r, frame)
        val l = 18 * d
        c.drawLines(floatArrayOf(
            r.left, r.top, r.left + l, r.top, r.left, r.top, r.left, r.top + l,
            r.right, r.top, r.right - l, r.top, r.right, r.top, r.right, r.top + l,
            r.left, r.bottom, r.left + l, r.bottom, r.left, r.bottom, r.left, r.bottom - l,
            r.right, r.bottom, r.right - l, r.bottom, r.right, r.bottom, r.right, r.bottom - l
        ), corner)
    }

    // which edges the finger holds: bit 1 left, 2 top, 4 right, 8 bottom; 15 = move all
    private var edges = 0
    private var lx = 0f
    private var ly = 0f

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val r = frameRect()
                val t = 24 * d
                edges = 0
                val near = RectF(r).apply { inset(-t, -t) }.contains(e.x, e.y)
                if (near) {
                    // the closer side wins when the frame is narrower than two touch areas
                    val dl = abs(e.x - r.left); val dr = abs(e.x - r.right)
                    val dt = abs(e.y - r.top); val db = abs(e.y - r.bottom)
                    if (dl < t && dl <= dr) edges = edges or 1 else if (dr < t) edges = edges or 4
                    if (dt < t && dt <= db) edges = edges or 2 else if (db < t) edges = edges or 8
                }
                if (edges == 0 && r.contains(e.x, e.y)) edges = 15
                lx = e.x; ly = e.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = (e.x - lx) / img.width()
                val dy = (e.y - ly) / img.height()
                val minF = 0.08f
                if (edges == 15) {
                    val mx = dx.coerceIn(-crop.left, 1f - crop.right)
                    val my = dy.coerceIn(-crop.top, 1f - crop.bottom)
                    crop.offset(mx, my)
                } else {
                    if (edges and 1 != 0) crop.left = (crop.left + dx).coerceIn(0f, crop.right - minF)
                    if (edges and 2 != 0) crop.top = (crop.top + dy).coerceIn(0f, crop.bottom - minF)
                    if (edges and 4 != 0) crop.right = (crop.right + dx).coerceIn(crop.left + minF, 1f)
                    if (edges and 8 != 0) crop.bottom = (crop.bottom + dy).coerceIn(crop.top + minF, 1f)
                }
                lx = e.x; ly = e.y
                invalidate()
            }
        }
        return true
    }
}
