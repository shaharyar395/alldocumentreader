package com.theoccess.alldocreader.ui.edit

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** What a touch on the page does. */
enum class EditTool { NONE, EDIT_TEXT, OBJECTS, PEN, HIGHLIGHT, UNDERLINE, STRIKE, ERASER }

/** The editor activity, seen from a page's drawing layer. */
interface EditHost {
    val tool: EditTool
    var selected: EditItem?
    fun items(page: Int): MutableList<EditItem>
    fun lines(page: Int): List<TextLine>
    fun penColor(): Int
    fun penWidth(): Float
    fun markColor(): Int
    fun onTapEmpty(page: Int, x: Float, y: Float)
    fun onEditTextItem(page: Int, item: TextItem)
    fun onLineTap(page: Int, line: TextLine)
    fun onDelete(page: Int, item: EditItem)
    fun onDuplicate(page: Int, item: EditItem)
    fun onEditImage(page: Int, item: ImageItem)
    fun onSelectionChanged()
    fun commit()
    /** A text / picture was dropped outside its page: move it to the page under (rawX, rawY). */
    fun onMovedOffPage(page: Int, item: EditItem, rawX: Float, rawY: Float)
}

/**
 * Transparent layer over one page: draws the page's items and handles every touch —
 * select / move / rotate / resize / delete text and pictures, draw with the pen,
 * highlight / underline / strike, erase, and tap text lines for Edit text.
 */
class EditLayerView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    var host: EditHost? = null
    var page = 0

    private val d = resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF1E6FE6.toInt()
        strokeWidth = 1.2f * resources.displayMetrics.density
        pathEffect = DashPathEffect(floatArrayOf(6f, 4f), 0f)
    }
    private val lineFill = Paint().apply { color = 0x141E6FE6 }

    // gesture state
    private enum class Op { NONE, TAP, MOVE, ROTATE, SCALE, WIDTH_L, WIDTH_R, DRAW, MARK, ERASE }
    private var op = Op.NONE
    private var target: EditItem? = null
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false
    private var startAngle = 0f
    private var startRotation = 0f
    private var startDist = 1f
    private var startWidth = 0f
    private var startSize = 0f
    private var wasSelected = false
    private var drawing = ArrayList<Float>()
    private var markRect: RectF? = null
    private var changed = false

    private val w get() = width.toFloat().coerceAtLeast(1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = host ?: return
        if (h.tool == EditTool.EDIT_TEXT) {
            h.lines(page).forEach { l ->
                val r = RectF(l.rect.left * w, l.rect.top * w, l.rect.right * w, l.rect.bottom * w)
                canvas.drawRect(r, lineFill)
                canvas.drawRect(r, linePaint)
            }
        }
        EditRenderer.draw(canvas, h.items(page), w)
        // pen stroke / mark in progress
        if (op == Op.DRAW && drawing.size >= 2) {
            EditRenderer.drawItem(canvas, InkItem(drawing.toFloatArray(), h.penColor(), h.penWidth()), w)
        }
        markRect?.let { r ->
            val kind = when (h.tool) { EditTool.UNDERLINE -> MarkKind.UNDERLINE; EditTool.STRIKE -> MarkKind.STRIKE; else -> MarkKind.HIGHLIGHT }
            EditRenderer.drawItem(canvas, MarkItem(kind, r, h.markColor()), w)
        }
        val sel = h.selected
        if (sel != null && h.items(page).contains(sel)) EditRenderer.drawSelection(canvas, sel, w, d)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val h = host ?: return false
        if (h.tool == EditTool.NONE) return false
        val x = e.x
        val y = e.y
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y; lastX = x; lastY = y
                moved = false; changed = false
                op = Op.TAP
                when (h.tool) {
                    EditTool.PEN -> {
                        op = Op.DRAW
                        drawing = arrayListOf(x / w, y / w)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    EditTool.HIGHLIGHT, EditTool.UNDERLINE, EditTool.STRIKE -> {
                        op = Op.MARK
                        markRect = markAt(x, x, y)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    EditTool.ERASER -> {
                        op = Op.ERASE
                        eraseAt(x, y)
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    else -> startObjectGesture(h, x, y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && hypot(x - downX, y - downY) > slop) moved = true
                when (op) {
                    Op.DRAW -> { drawing.add(x / w); drawing.add(y / w) }
                    Op.MARK -> markRect = markAt(downX, x, downY)
                    Op.ERASE -> eraseAt(x, y)
                    Op.MOVE -> target?.let { t -> if (moved) { lift(true); moveBy(t, (x - lastX) / w, (y - lastY) / w); changed = true } }
                    Op.ROTATE -> target?.let { t ->
                        val (cx, cy) = EditRenderer.center(t, w)
                        val a = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                        setRotation(t, startRotation + (a - startAngle))
                        changed = true
                    }
                    Op.SCALE -> target?.let { t ->
                        val (cx, cy) = EditRenderer.center(t, w)
                        // the corner handle resizes and turns at the same time
                        val a = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                        setRotation(t, startRotation + (a - startAngle))
                        val k = (hypot(x - cx, y - cy) / startDist).coerceIn(0.15f, 8f)
                        when (t) {
                            is TextItem -> { t.size = (startSize * k).coerceIn(0.008f, 0.5f); t.width = startWidth * (t.size / startSize) }
                            is ImageItem -> t.width = (startWidth * k).coerceIn(0.03f, 3f)
                            else -> Unit
                        }
                        changed = true
                    }
                    Op.WIDTH_L, Op.WIDTH_R -> (target as? TextItem)?.let { t ->
                        val (lx, _) = EditRenderer.toLocal(t, w, x, y)
                        val half = abs(lx) / w
                        t.width = max(t.size * 1.2f, half * 2f).coerceAtMost(1.5f)
                        changed = true
                    }
                    else -> Unit
                }
                lastX = x; lastY = y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                when (op) {
                    Op.DRAW -> {
                        if (drawing.size >= 2) {
                            h.items(page).add(InkItem(drawing.toFloatArray(), h.penColor(), h.penWidth()))
                            h.commit()
                        }
                        drawing = ArrayList()
                    }
                    Op.MARK -> {
                        markRect?.let { r ->
                            if (r.width() > 0.01f) {
                                val kind = when (h.tool) { EditTool.UNDERLINE -> MarkKind.UNDERLINE; EditTool.STRIKE -> MarkKind.STRIKE; else -> MarkKind.HIGHLIGHT }
                                h.items(page).add(MarkItem(kind, r, h.markColor()))
                                h.commit()
                            }
                        }
                        markRect = null
                    }
                    Op.ERASE -> if (changed) h.commit()
                    Op.TAP -> if (!moved) onTap(h, x, y)
                    Op.MOVE -> {
                        val t = target
                        lift(false)
                        if (!moved && wasSelected && t is TextItem) h.onEditTextItem(page, t)
                        else if (changed && t != null) {
                            val (_, cy) = EditRenderer.center(t, w)
                            if (cy < 0f || cy > height) {
                                // dragged onto another page (or into the gap): let the editor move it there
                                val loc = IntArray(2)
                                getLocationOnScreen(loc)
                                val (cx, _) = EditRenderer.center(t, w)
                                h.onMovedOffPage(page, t, loc[0] + cx, loc[1] + cy)
                            } else h.commit()
                        }
                    }
                    else -> if (changed) h.commit()
                }
                op = Op.NONE
                target = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                lift(false)
                if (changed) h.commit()
                op = Op.NONE; target = null; drawing = ArrayList(); markRect = null
                invalidate()
                return true
            }
        }
        return false
    }

    private fun startObjectGesture(h: EditHost, x: Float, y: Float) {
        val sel = h.selected
        val items = h.items(page)
        // 1) handles of the selected item
        if (sel != null && items.contains(sel)) {
            val (lx, ly) = EditRenderer.toLocal(sel, w, x, y)
            val hit = EditRenderer.handles(sel, w, 6 * d).entries.firstOrNull { (_, p) -> hypot(lx - p.first, ly - p.second) < 18 * d }?.key
            if (hit != null) {
                target = sel
                parent?.requestDisallowInterceptTouchEvent(true)
                when (hit) {
                    "delete" -> { op = Op.NONE; target = null; h.onDelete(page, sel); return }
                    "copy" -> { op = Op.NONE; target = null; h.onDuplicate(page, sel); return }
                    "edit" -> { op = Op.NONE; target = null; (sel as? ImageItem)?.let { h.onEditImage(page, it) }; return }
                    "rotate" -> {
                        op = Op.ROTATE
                        val (cx, cy) = EditRenderer.center(sel, w)
                        startAngle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                        startRotation = EditRenderer.rotation(sel)
                    }
                    "scale" -> {
                        op = Op.SCALE
                        val (cx, cy) = EditRenderer.center(sel, w)
                        startDist = hypot(x - cx, y - cy).coerceAtLeast(1f)
                        startAngle = Math.toDegrees(atan2((y - cy).toDouble(), (x - cx).toDouble())).toFloat()
                        startRotation = EditRenderer.rotation(sel)
                        startWidth = when (sel) { is TextItem -> sel.width; is ImageItem -> sel.width; else -> 0f }
                        startSize = (sel as? TextItem)?.size ?: 0f
                    }
                    "left" -> op = Op.WIDTH_L
                    "right" -> op = Op.WIDTH_R
                }
                return
            }
        }
        // 2) an item under the finger (topmost first)
        val hitItem = items.asReversed().firstOrNull { (it is TextItem || it is ImageItem) && contains(it, x, y) }
        if (hitItem != null) {
            wasSelected = hitItem === sel
            h.selected = hitItem
            h.onSelectionChanged()
            target = hitItem
            op = Op.MOVE
            parent?.requestDisallowInterceptTouchEvent(true)
            return
        }
        op = Op.TAP
    }

    private fun onTap(h: EditHost, x: Float, y: Float) {
        if (h.tool == EditTool.EDIT_TEXT) {
            val line = h.lines(page).firstOrNull { l ->
                x / w in (l.rect.left - 0.01f)..(l.rect.right + 0.01f) && y / w in (l.rect.top - 0.005f)..(l.rect.bottom + 0.005f)
            }
            if (line != null) { h.onLineTap(page, line); return }
        }
        if (h.selected != null) {
            h.selected = null
            h.onSelectionChanged()
            return
        }
        h.onTapEmpty(page, x / w, y / w)
    }

    private fun contains(item: EditItem, x: Float, y: Float): Boolean {
        val (bw, bh) = EditRenderer.boxSize(item, w)
        val (lx, ly) = EditRenderer.toLocal(item, w, x, y)
        val pad = 8 * d
        return abs(lx) <= bw / 2 + pad && abs(ly) <= bh / 2 + pad
    }

    /**
     * While a text / picture is dragged, its page is drawn above the neighbouring pages (and is
     * not clipped), so the item stays visible when it is pulled across the gap onto another page.
     */
    private fun lift(on: Boolean) {
        val pageRoot = (parent as? View)?.parent as? View ?: return
        pageRoot.translationZ = if (on) 8f * d else 0f
    }

    private fun moveBy(item: EditItem, dx: Float, dy: Float) {
        when (item) {
            is TextItem -> { item.cx += dx; item.cy += dy }
            is ImageItem -> { item.cx += dx; item.cy += dy }
            else -> Unit
        }
    }

    private fun setRotation(item: EditItem, deg: Float) {
        var r = ((deg % 360) + 360) % 360
        // snap near straight angles
        for (s in floatArrayOf(0f, 90f, 180f, 270f, 360f)) if (abs(r - s) < 4f) r = s % 360
        when (item) {
            is TextItem -> item.rotation = r
            is ImageItem -> item.rotation = r
            else -> Unit
        }
    }

    /** Highlight / underline / strike span: snaps to a text line when there is one under the finger. */
    private fun markAt(x0: Float, x1: Float, y: Float): RectF {
        val h = host
        val nx0 = min(x0, x1) / w
        val nx1 = max(x0, x1) / w
        val ny = y / w
        val line = h?.lines(page)?.firstOrNull { ny in it.rect.top..it.rect.bottom }
        return if (line != null) RectF(max(nx0, line.rect.left - 0.01f), line.rect.top, min(max(nx1, nx0 + 0.005f), line.rect.right + 0.01f), line.rect.bottom)
        else {
            val half = 0.016f
            RectF(nx0, ny - half, max(nx1, nx0 + 0.005f), ny + half)
        }
    }

    private fun eraseAt(x: Float, y: Float) {
        val h = host ?: return
        val r = 14 * d
        val items = h.items(page)
        val hit = items.filter { item ->
            when (item) {
                is InkItem -> {
                    var near = false
                    var i = 0
                    while (i + 1 < item.points.size && !near) {
                        near = hypot(item.points[i] * w - x, item.points[i + 1] * w - y) < r + item.width * w / 2
                        i += 2
                    }
                    near
                }
                is MarkItem -> RectF(item.rect.left * w - r, item.rect.top * w - r, item.rect.right * w + r, item.rect.bottom * w + r).contains(x, y)
                else -> false
            }
        }
        if (hit.isNotEmpty()) {
            items.removeAll(hit.toSet())
            changed = true
        }
    }
}
