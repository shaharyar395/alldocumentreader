package com.theoccess.alldocreader.ui.edit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Looping demo inside the "i" sheet: a phone showing how Edit PDF works (select a line,
 * format bar, colour change) or how Sign works (Add signature → sign → placed on the page).
 */
class TutorialDemoView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    enum class Demo { EDIT, SIGN }

    var demo = Demo.EDIT
        set(value) { field = value; t = 0f; invalidate() }

    /** Called each time a loop finishes (the sheet switches tabs). */
    var onLoop: (() -> Unit)? = null

    private val d = resources.displayMetrics.density
    private var t = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 5200
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedValue as Float; invalidate() }
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationRepeat(animation: android.animation.Animator) { onLoop?.invoke() }
        })
    }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val s = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sigPath = Path()
    private val sigPart = Path()

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // phone
        val pw = min(width * 0.62f, 230 * d)
        val left = (width - pw) / 2
        val top = 52 * d
        val phone = RectF(left, top, left + pw, height + 40 * d)
        p.color = 0xFF1F2126.toInt()
        canvas.drawRoundRect(phone, 26 * d, 26 * d, p)
        val screen = RectF(phone.left + 7 * d, phone.top + 7 * d, phone.right - 7 * d, phone.bottom)
        p.color = 0xFFFFFFFF.toInt()
        canvas.drawRoundRect(screen, 20 * d, 20 * d, p)
        canvas.save()
        canvas.clipRect(screen)
        if (demo == Demo.EDIT) drawEdit(canvas, screen) else drawSign(canvas, screen)
        canvas.restore()
    }

    private fun bars(canvas: Canvas, x: Float, y: Float, w: Float, n: Int, gap: Float = 7 * d) {
        p.color = 0xFFD9DDE4.toInt()
        for (i in 0 until n) {
            val lw = if (i % 3 == 2) w * 0.7f else w
            canvas.drawRoundRect(RectF(x, y + i * gap, x + lw, y + i * gap + 3 * d), 1.5f * d, 1.5f * d, p)
        }
    }

    private fun drawEdit(c: Canvas, sc: RectF) {
        val x = sc.left + 14 * d
        val w = sc.width() - 28 * d
        bars(c, x, sc.top + 18 * d, w, 5)
        // heading line
        val hy = sc.top + 66 * d
        txt.textSize = 8.5f * d
        txt.isFakeBoldText = true
        val red = seg(0.66f, 0.74f)
        txt.color = blend(0xFF30343C.toInt(), 0xFFE53935.toInt(), red)
        c.drawText("The Cognitive Load of Fixed Layouts", x, hy, txt)
        bars(c, x, hy + 12 * d, w, 6)
        // selection box + mini toolbar
        val sel = seg(0.18f, 0.26f)
        if (sel > 0f) {
            val r = RectF(x - 3 * d, hy - 10 * d, x + txt.measureText("The Cognitive Load of Fixed Layouts") + 3 * d, hy + 4 * d)
            s.color = 0xFF1E6FE6.toInt(); s.alpha = (255 * sel).toInt(); s.strokeWidth = 1.2f * d
            s.pathEffect = DashPathEffect(floatArrayOf(4 * d, 3 * d), 0f)
            c.drawRect(r, s)
            s.pathEffect = null
            p.color = 0xFFFFFFFF.toInt(); p.alpha = (255 * sel).toInt()
            val tb = RectF(r.left + 16 * d, r.top - 22 * d, r.left + 76 * d, r.top - 5 * d)
            p.setShadowLayer(4 * d, 0f, 1 * d, 0x33000000)
            c.drawRoundRect(tb, 5 * d, 5 * d, p)
            p.clearShadowLayer()
            p.color = 0xFF5B6475.toInt(); p.alpha = (255 * sel).toInt()
            for (i in 0 until 3) c.drawRoundRect(RectF(tb.left + 8 * d + i * 18 * d, tb.top + 5 * d, tb.left + 16 * d + i * 18 * d, tb.bottom - 5 * d), 2 * d, 2 * d, p)
            p.alpha = 255
        }
        // bottom: tools row, then the format panel sliding up
        val panel = seg(0.40f, 0.50f)
        val baseY = sc.bottom - 44 * d
        val panelH = 70 * d
        val py = baseY - panelH * panel
        p.color = 0xFFFFFFFF.toInt()
        p.setShadowLayer(6 * d, 0f, -1 * d, 0x22000000)
        c.drawRect(RectF(sc.left, py, sc.right, sc.bottom), p)
        p.clearShadowLayer()
        val iconsY = py + 16 * d
        val step = sc.width() / 5
        for (i in 0 until 5) {
            val cx = sc.left + step * (i + 0.5f)
            val active = i == 3 && red > 0f
            p.color = if (i == 0 && panel == 0f) 0xFF1E6FE6.toInt() else if (active) 0xFF1E6FE6.toInt() else 0xFF5B6475.toInt()
            c.drawRoundRect(RectF(cx - 6 * d, iconsY - 6 * d, cx + 6 * d, iconsY + 6 * d), 2 * d, 2 * d, p)
        }
        if (panel > 0f) {
            // size slider + B I S U + alignment
            s.color = 0xFF1E6FE6.toInt(); s.strokeWidth = 2 * d
            c.drawLine(sc.left + 14 * d, iconsY + 20 * d, sc.left + sc.width() * 0.6f, iconsY + 20 * d, s)
            p.color = 0xFF1E6FE6.toInt()
            c.drawCircle(sc.left + 40 * d, iconsY + 20 * d, 4 * d, p)
            txt.isFakeBoldText = true
            txt.textSize = 9 * d
            listOf("B", "I", "S", "U").forEachIndexed { i, l ->
                val cx = sc.left + step * (i + 0.8f)
                if (i == 0 && red > 0f) {
                    p.color = 0xFFE3EDFD.toInt()
                    c.drawCircle(cx, iconsY + 40 * d, 9 * d, p)
                }
                txt.color = 0xFF30343C.toInt()
                c.drawText(l, cx - txt.measureText(l) / 2, iconsY + 43 * d, txt)
            }
        }
        // finger tap
        tap(c, sc.left + sc.width() * 0.35f, hy - 3 * d, seg(0.10f, 0.20f))
        tap(c, sc.left + step * 0.8f, iconsY + 40 * d, seg(0.56f, 0.66f))
    }

    private fun drawSign(c: Canvas, sc: RectF) {
        val x = sc.left + 14 * d
        val w = sc.width() - 28 * d
        txt.textSize = 7 * d
        txt.isFakeBoldText = true
        txt.color = 0xFF30343C.toInt()
        c.drawText("2. DEFAULT & GOVERNING LAW", x, sc.top + 24 * d, txt)
        bars(c, x, sc.top + 32 * d, w, 3)
        txt.textSize = 6.5f * d
        c.drawText("LENDER", x, sc.top + 70 * d, txt)
        c.drawText("BORROWER", sc.right - 14 * d - txt.measureText("BORROWER"), sc.top + 70 * d, txt)
        // lender's signature already there
        s.color = 0xFF9AA3AF.toInt(); s.strokeWidth = 1 * d
        c.drawLine(x, sc.top + 92 * d, x + 60 * d, sc.top + 92 * d, s)
        c.drawLine(sc.right - 74 * d, sc.top + 92 * d, sc.right - 14 * d, sc.top + 92 * d, s)
        // "+ Add signature" bar
        val barY = sc.bottom - 34 * d
        p.color = 0xFFEAF1FE.toInt()
        c.drawRoundRect(RectF(x, barY - 14 * d, sc.right - 14 * d, barY + 10 * d), 10 * d, 10 * d, p)
        txt.color = 0xFF1E6FE6.toInt(); txt.textSize = 8 * d
        val label = "+  Add signature"
        c.drawText(label, sc.centerX() - txt.measureText(label) / 2, barY + 1 * d, txt)
        tap(c, sc.centerX(), barY - 2 * d, seg(0.06f, 0.18f))

        // signature pad
        val pad = seg(0.22f, 0.28f) * (1f - seg(0.62f, 0.68f))
        buildSignature(RectF(sc.left + 22 * d, sc.top + 36 * d, sc.right - 22 * d, sc.top + 96 * d))
        if (pad > 0f) {
            p.color = 0xFFFFFFFF.toInt(); p.alpha = (255 * pad).toInt()
            p.setShadowLayer(8 * d, 0f, 2 * d, 0x33000000)
            val r = RectF(sc.left + 8 * d, sc.top + 20 * d, sc.right - 8 * d, sc.top + 118 * d)
            c.drawRoundRect(r, 10 * d, 10 * d, p)
            p.clearShadowLayer(); p.alpha = 255
            txt.color = 0xFFD3D8E0.toInt(); txt.textSize = 16 * d; txt.isFakeBoldText = false
            txt.textSkewX = -0.2f
            if (seg(0.30f, 0.32f) == 0f) c.drawText("Sign here", r.centerX() - txt.measureText("Sign here") / 2, r.centerY() + 5 * d, txt)
            txt.textSkewX = 0f
            drawSignature(c, seg(0.30f, 0.56f), 0xFF1F2A8C.toInt(), (255 * pad).toInt())
            // colour dots
            val colors = intArrayOf(0xFF111111.toInt(), 0xFF5F6673.toInt(), 0xFF1F2A5C.toInt(), 0xFF1E5BE6.toInt(), 0xFF7A1F2B.toInt(), 0xFFE53935.toInt())
            colors.forEachIndexed { i, col ->
                p.color = col; p.alpha = (255 * pad).toInt()
                c.drawCircle(r.left + 14 * d + i * 12 * d, r.bottom - 10 * d, 3.5f * d, p)
            }
            p.alpha = 255
        }
        // placed on the page next to BORROWER
        val placed = seg(0.68f, 0.76f)
        if (placed > 0f) {
            c.save()
            val target = RectF(sc.right - 78 * d, sc.top + 74 * d, sc.right - 14 * d, sc.top + 92 * d)
            c.translate(target.left, target.top)
            c.scale(target.width() / (sc.width() - 44 * d), target.height() / (60 * d))
            c.translate(-(sc.left + 22 * d), -(sc.top + 36 * d))
            drawSignature(c, 1f, 0xFF1F2A8C.toInt(), (255 * placed).toInt())
            c.restore()
            s.color = 0xFF1E6FE6.toInt(); s.alpha = (255 * placed).toInt(); s.strokeWidth = 1 * d
            s.pathEffect = DashPathEffect(floatArrayOf(3 * d, 2 * d), 0f)
            c.drawRect(RectF(target.left - 3 * d, target.top - 3 * d, target.right + 3 * d, target.bottom + 3 * d), s)
            s.pathEffect = null; s.alpha = 255
        }
    }

    private fun buildSignature(r: RectF) {
        sigPath.reset()
        val w = r.width(); val h = r.height()
        sigPath.moveTo(r.left + w * 0.18f, r.top + h * 0.35f)
        sigPath.cubicTo(r.left + w * 0.24f, r.top + h * 0.05f, r.left + w * 0.30f, r.top + h * 0.95f, r.left + w * 0.20f, r.top + h * 0.85f)
        sigPath.cubicTo(r.left + w * 0.12f, r.top + h * 0.75f, r.left + w * 0.30f, r.top + h * 0.45f, r.left + w * 0.40f, r.top + h * 0.55f)
        sigPath.cubicTo(r.left + w * 0.46f, r.top + h * 0.62f, r.left + w * 0.50f, r.top + h * 0.35f, r.left + w * 0.55f, r.top + h * 0.20f)
        sigPath.cubicTo(r.left + w * 0.56f, r.top + h * 0.60f, r.left + w * 0.60f, r.top + h * 0.75f, r.left + w * 0.66f, r.top + h * 0.48f)
        sigPath.cubicTo(r.left + w * 0.70f, r.top + h * 0.40f, r.left + w * 0.74f, r.top + h * 0.75f, r.left + w * 0.84f, r.top + h * 0.50f)
    }

    private fun drawSignature(c: Canvas, progress: Float, color: Int, alpha: Int) {
        if (progress <= 0f) return
        val pm = PathMeasure(sigPath, false)
        sigPart.reset()
        pm.getSegment(0f, pm.length * progress, sigPart, true)
        s.color = color; s.alpha = alpha; s.strokeWidth = 1.6f * d
        c.drawPath(sigPart, s)
        s.alpha = 255
    }

    /** Blue touch ripple. */
    private fun tap(c: Canvas, x: Float, y: Float, k: Float) {
        if (k <= 0f || k >= 1f) return
        p.color = 0xFF4D8DFF.toInt()
        p.alpha = (200 * (1f - k)).toInt()
        c.drawCircle(x, y, (6 + 8 * k) * d, p)
        p.alpha = 255
    }

    private fun blend(a: Int, b: Int, k: Float): Int {
        fun ch(v: Int, sh: Int) = (v shr sh) and 0xFF
        val r = (ch(a, 16) + (ch(b, 16) - ch(a, 16)) * k).toInt()
        val g = (ch(a, 8) + (ch(b, 8) - ch(a, 8)) * k).toInt()
        val bl = (ch(a, 0) + (ch(b, 0) - ch(a, 0)) * k).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }
}
