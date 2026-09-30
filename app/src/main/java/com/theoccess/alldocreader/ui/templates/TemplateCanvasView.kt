package com.theoccess.alldocreader.ui.templates

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import com.theoccess.alldocreader.R
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The page in the template editor. Tap a text or picture to select it (thin blue box with
 * duplicate / delete above it and a turn-and-resize handle below), drag to move it (a red
 * guide shows when it is centred on the page), tap the selected text again to edit it,
 * pinch to zoom and drag the empty area to move the page.
 */
class TemplateCanvasView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    interface Listener {
        fun onSelectionChanged(el: TEl?)
        fun onEditText(t: TText)
        /** Something was moved / turned / resized / duplicated / deleted (for undo). */
        fun onChanged()
    }

    var listener: Listener? = null

    var page: TPage? = null
        set(v) { field = v; selected = null; invalidate() }

    var selected: TEl? = null
        private set

    private val d = resources.displayMetrics.density

    // page → screen: screen = page * scale + (tx, ty)
    private var fitScale = 1f
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private val scale get() = fitScale * zoom
    private val tx get() = (width - PAGE_W * scale) / 2f + panX
    private val ty get() = pageTop + panY
    private val pageTop get() = 16 * d

    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x22000000 }
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF1E6FE6.toInt(); strokeWidth = 1.4f * d }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF2D2D.toInt(); strokeWidth = 1.2f * d }
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; setShadowLayer(4 * d, 0f, d, 0x33000000) }
    private val icCopy: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_q_copy)?.mutate()?.apply { setTint(0xFF1B1F2A.toInt()) }
    private val icDelete: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_delete)?.mutate()?.apply { setTint(0xFF1B1F2A.toInt()) }
    private val icRotate: Drawable? = ContextCompat.getDrawable(context, R.drawable.tpl_handle_rotate)

    // screen rects of the floating controls (updated in onDraw)
    private val copyRect = RectF()
    private val deleteRect = RectF()
    private val handleRect = RectF()

    private var showGuideV = false
    private var showGuideH = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)   // shadows + clip paths look the same everywhere
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitScale = w / PAGE_W
        clampPan()
    }

    fun select(el: TEl?) {
        if (selected === el) return
        selected = el
        listener?.onSelectionChanged(el)
        invalidate()
    }

    /** Keeps the selection after the page was replaced (undo / redo). */
    fun selectIndex(i: Int) {
        val p = page ?: return
        selected = p.elements.getOrNull(i)?.takeIf { it !is TShape }
        listener?.onSelectionChanged(selected)
        invalidate()
    }

    fun selectedIndex(): Int = page?.elements?.indexOfFirst { it === selected } ?: -1

    // ------------------------------------------------------------------ drawing

    override fun onDraw(c: Canvas) {
        val p = page ?: return
        val s = scale
        c.drawRect(tx + 2 * d, ty + 3 * d, tx + PAGE_W * s + 2 * d, ty + PAGE_H * s + 3 * d, shadow)
        c.save()
        c.translate(tx, ty)
        c.scale(s, s)
        c.clipRect(0f, 0f, PAGE_W, PAGE_H)
        TplRenderer.draw(context, c, p)
        c.restore()

        if (showGuideV) c.drawLine(tx + PAGE_W / 2 * s, ty, tx + PAGE_W / 2 * s, ty + PAGE_H * s, guidePaint)
        if (showGuideH) c.drawLine(tx, ty + PAGE_H / 2 * s, tx + PAGE_W * s, ty + PAGE_H / 2 * s, guidePaint)

        val el = selected ?: return
        val (w, h) = TplRenderer.size(el)
        val (cx, cy) = TplRenderer.center(el)
        val scx = tx + cx * s
        val scy = ty + cy * s
        val pad = 4 * d
        val hw = w * s / 2 + pad
        val hh = h * s / 2 + pad
        c.save()
        c.rotate(el.rotation, scx, scy)
        c.drawRect(scx - hw, scy - hh, scx + hw, scy + hh, boxPaint)
        c.restore()

        // controls stay upright: duplicate + delete bubble above, turn / resize handle below
        val m = Matrix().apply { setRotate(el.rotation, scx, scy) }
        val top = floatArrayOf(scx, scy - hh).also { m.mapPoints(it) }
        val bottom = floatArrayOf(scx, scy + hh).also { m.mapPoints(it) }
        val bw = 72 * d; val bh = 34 * d
        val bx = top[0] - bw / 2; val by = top[1] - bh - 10 * d
        c.drawRoundRect(bx, by, bx + bw, by + bh, 8 * d, 8 * d, bubblePaint)
        val isz = (20 * d).toInt()
        copyRect.set(bx, by, bx + bw / 2, by + bh)
        deleteRect.set(bx + bw / 2, by, bx + bw, by + bh)
        icCopy?.apply { setBounds((copyRect.centerX() - isz / 2).toInt(), (copyRect.centerY() - isz / 2).toInt(), (copyRect.centerX() + isz / 2).toInt(), (copyRect.centerY() + isz / 2).toInt()); draw(c) }
        icDelete?.apply { setBounds((deleteRect.centerX() - isz / 2).toInt(), (deleteRect.centerY() - isz / 2).toInt(), (deleteRect.centerX() + isz / 2).toInt(), (deleteRect.centerY() + isz / 2).toInt()); draw(c) }
        val hs = 28 * d
        handleRect.set(bottom[0] - hs / 2, bottom[1] + 6 * d, bottom[0] + hs / 2, bottom[1] + 6 * d + hs)
        icRotate?.apply { setBounds(handleRect.left.toInt(), handleRect.top.toInt(), handleRect.right.toInt(), handleRect.bottom.toInt()); draw(c) }
    }

    // ------------------------------------------------------------------ touch

    private enum class Mode { NONE, MOVE, TURN, PAN, PINCH }

    private var mode = Mode.NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var changed = false
    private var tappedSelected = false
    private var elStartX = 0f
    private var elStartY = 0f
    // TURN start values
    private var startAngle = 0f
    private var startDist = 1f
    private var startRot = 0f
    private var startW = 0f
    private var startH = 0f
    private var startSize = 0f
    private var startCx = 0f
    private var startCy = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(det: ScaleGestureDetector): Boolean {
            val old = zoom
            val ox = tx; val oy = ty
            zoom = (zoom * det.scaleFactor).coerceIn(1f, 5f)
            // keep the point under the fingers in place
            val f = zoom / old
            val fx = det.focusX; val fy = det.focusY
            val nx = fx - (fx - ox) * f
            val ny = fy - (fy - oy) * f
            panX += nx - (width - PAGE_W * scale) / 2f - panX
            panY += ny - pageTop - panY
            clampPan()
            invalidate()
            return true
        }
    }).apply { isQuickScaleEnabled = false }   // double-tap-drag would zoom while moving / panning

    private fun clampPan() {
        val pw = PAGE_W * scale
        val ph = PAGE_H * scale
        val maxX = max(0f, (pw - width) / 2f)
        panX = panX.coerceIn(-maxX, maxX)
        val minY = min(0f, height - ph - pageTop * 2)
        panY = panY.coerceIn(minY, 0f)
    }

    private fun toPage(x: Float, y: Float) = floatArrayOf((x - tx) / scale, (y - ty) / scale)

    /** Topmost text / picture under the page point (taking its turn into account). */
    private fun hit(px: Float, py: Float): TEl? {
        val p = page ?: return null
        val slop = 6 * d / scale
        for (i in p.elements.indices.reversed()) {
            val el = p.elements[i]
            if (el is TShape) continue
            val (w, h) = TplRenderer.size(el)
            val (cx, cy) = TplRenderer.center(el)
            val pt = floatArrayOf(px, py)
            if (el.rotation != 0f) Matrix().apply { setRotate(-el.rotation, cx, cy) }.mapPoints(pt)
            if (pt[0] >= el.x - slop && pt[0] <= el.x + w + slop && pt[1] >= el.y - slop && pt[1] <= el.y + h + slop) return el
        }
        return null
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = e.x; downY = e.y; lastX = e.x; lastY = e.y
                moved = false; changed = false; tappedSelected = false
                val sel = selected
                mode = when {
                    sel != null && copyRect.contains(e.x, e.y) -> { duplicate(); Mode.NONE }
                    sel != null && deleteRect.contains(e.x, e.y) -> { deleteSelected(); Mode.NONE }
                    sel != null && handleRect.contains(e.x, e.y) -> { startTurn(sel, e.x, e.y); Mode.TURN }
                    else -> {
                        val pt = toPage(e.x, e.y)
                        val el = hit(pt[0], pt[1])
                        if (el != null) {
                            tappedSelected = el === sel
                            select(el)
                            elStartX = el.x; elStartY = el.y
                            Mode.MOVE
                        } else Mode.PAN
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (mode == Mode.MOVE || mode == Mode.TURN) finishChange()
                mode = Mode.PINCH
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(e.x - downX, e.y - downY) > 6 * d) moved = true
                when (mode) {
                    Mode.MOVE -> if (moved) moveSelected(e.x, e.y)
                    Mode.TURN -> turnSelected(e.x, e.y)
                    Mode.PAN -> if (moved) {
                        panX += e.x - lastX; panY += e.y - lastY
                        clampPan(); invalidate()
                    }
                    else -> {}
                }
                lastX = e.x; lastY = e.y
            }
            MotionEvent.ACTION_UP -> {
                when (mode) {
                    Mode.MOVE -> {
                        if (!moved && tappedSelected) (selected as? TText)?.let { listener?.onEditText(it) }
                        finishChange()
                    }
                    Mode.TURN -> finishChange()
                    Mode.PAN -> if (!moved) select(null)
                    else -> {}
                }
                mode = Mode.NONE
            }
            MotionEvent.ACTION_CANCEL -> { finishChange(); mode = Mode.NONE }
        }
        return true
    }

    private fun finishChange() {
        showGuideV = false; showGuideH = false
        if (changed) listener?.onChanged()
        changed = false
        invalidate()
    }

    private fun moveSelected(x: Float, y: Float) {
        val el = selected ?: return
        val s = scale
        el.x = elStartX + (x - downX) / s
        el.y = elStartY + (y - downY) / s
        // snap the centre to the middle of the page
        val (cx, cy) = TplRenderer.center(el)
        val snap = 5 * d / s
        showGuideV = abs(cx - PAGE_W / 2) < snap
        if (showGuideV) el.x += PAGE_W / 2 - cx
        showGuideH = abs(cy - PAGE_H / 2) < snap
        if (showGuideH) el.y += PAGE_H / 2 - cy
        changed = true
        invalidate()
    }

    private fun startTurn(el: TEl, x: Float, y: Float) {
        val (cx, cy) = TplRenderer.center(el)
        startCx = cx; startCy = cy
        val sx = tx + cx * scale; val sy = ty + cy * scale
        startAngle = Math.toDegrees(atan2((y - sy).toDouble(), (x - sx).toDouble())).toFloat()
        startDist = max(1f, hypot(x - sx, y - sy))
        startRot = el.rotation
        when (el) {
            is TText -> { startW = el.w; startSize = el.size }
            is TImage -> { startW = el.w; startH = el.h }
            else -> {}
        }
    }

    private fun turnSelected(x: Float, y: Float) {
        val el = selected ?: return
        val sx = tx + startCx * scale; val sy = ty + startCy * scale
        val ang = Math.toDegrees(atan2((y - sy).toDouble(), (x - sx).toDouble())).toFloat()
        var rot = startRot + ang - startAngle
        rot = ((rot % 360f) + 360f) % 360f
        for (snapTo in floatArrayOf(0f, 90f, 180f, 270f, 360f)) if (abs(rot - snapTo) < 4f) rot = snapTo % 360f
        el.rotation = rot
        val f = (hypot(x - sx, y - sy) / startDist).coerceIn(0.2f, 6f)
        when (el) {
            is TText -> {
                el.size = (startSize * f).coerceIn(4f, 200f)
                el.w = (startW * f).coerceIn(20f, PAGE_W * 2)
            }
            is TImage -> {
                el.w = max(12f, startW * f); el.h = max(12f, startH * f)
            }
            else -> {}
        }
        // keep the centre where it was
        val (w, h) = TplRenderer.size(el)
        el.x = startCx - w / 2; el.y = startCy - h / 2
        changed = true
        invalidate()
    }

    fun duplicate() {
        val p = page ?: return
        val el = selected ?: return
        val copy = el.copyEl().apply { x += 16f; y += 16f }
        val i = p.elements.indexOf(el)
        p.elements.add(i + 1, copy)
        select(copy)
        listener?.onChanged()
        invalidate()
    }

    fun deleteSelected() {
        val p = page ?: return
        val el = selected ?: return
        p.elements.remove(el)
        select(null)
        listener?.onChanged()
        invalidate()
    }

    /** Adds a new text / picture in the middle of the visible part of the page and selects it. */
    fun addCentered(el: TEl) {
        val p = page ?: return
        val pt = toPage(width / 2f, height / 2f)
        val (w, h) = TplRenderer.size(el)
        el.x = (pt[0] - w / 2).coerceIn(0f, max(0f, PAGE_W - w))
        el.y = (pt[1] - h / 2).coerceIn(0f, max(0f, PAGE_H - h))
        p.elements.add(el)
        select(el)
        listener?.onChanged()
        invalidate()
    }
}
