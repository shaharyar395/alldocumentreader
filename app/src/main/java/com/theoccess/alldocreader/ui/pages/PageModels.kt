package com.theoccess.alldocreader.ui.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.theoccess.alldocreader.R

/** A blank page to insert: template + size (points) + background colour. */
data class BlankSpec(val template: Int, val widthPt: Float, val heightPt: Float, val color: Int)

sealed class PageKind {
    data class Original(val index: Int) : PageKind()
    data class Blank(val spec: BlankSpec) : PageKind()
    data class Picture(val uri: String) : PageKind()
}

/**
 * One page in the organizer; [rotation] is extra rotation added by the user,
 * [size] (points, portrait) and [bg] come from "Page setup" (null = keep the page's own).
 */
data class PageItem(
    val id: Long,
    val kind: PageKind,
    val rotation: Int = 0,
    val size: Pair<Float, Float>? = null,
    val bg: Int? = null
)

data class PageSize(@StringRes val label: Int, val widthMm: Float, val heightMm: Float) {
    val widthPt get() = mmToPt(widthMm)
    val heightPt get() = mmToPt(heightMm)
    val dims get() = "${widthMm.toInt()}x${heightMm.toInt()}mm"

    companion object {
        fun mmToPt(mm: Float) = mm * 72f / 25.4f
        fun ptToMm(pt: Float) = pt * 25.4f / 72f

        val ALL = listOf(
            PageSize(R.string.size_a5, 148f, 210f),
            PageSize(R.string.size_a4, 210f, 297f),
            PageSize(R.string.size_a3, 297f, 420f),
            PageSize(R.string.size_b5, 176f, 250f),
            PageSize(R.string.size_b4, 250f, 353f),
            PageSize(R.string.size_letter, 216f, 279f),
            PageSize(R.string.size_legal, 216f, 356f)
        )
    }
}

/** Blank-page templates shown in "Insert blank pages". */
object Templates {
    data class Template(@StringRes val name: Int, @DrawableRes val icon: Int)

    val ALL = listOf(
        Template(R.string.tpl_blank, R.drawable.ic_tpl_blank),
        Template(R.string.tpl_line1, R.drawable.ic_tpl_line1),
        Template(R.string.tpl_line2, R.drawable.ic_tpl_line2),
        Template(R.string.tpl_notebook, R.drawable.ic_tpl_notebook),
        Template(R.string.tpl_cornell, R.drawable.ic_tpl_cornell),
        Template(R.string.tpl_grid, R.drawable.ic_tpl_grid),
        Template(R.string.tpl_graph, R.drawable.ic_tpl_graph)
    )

    /** Page colours: white, grey, blue, pink, yellow, green. */
    val COLORS = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFEDEEF0.toInt(), 0xFFE3EEFB.toInt(),
        0xFFFCE3E3.toInt(), 0xFFFFF4D0.toInt(), 0xFFE2F3E5.toInt()
    )

    /** A line in page points, origin top-left. */
    data class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val width: Float, val red: Boolean = false)

    fun segments(template: Int, w: Float, h: Float): List<Seg> {
        val out = ArrayList<Seg>()
        val side = w * 0.05f
        fun hLines(step: Float, from: Float, to: Float, x1: Float = side, x2: Float = w - side, width: Float = 0.6f) {
            var y = from
            while (y <= to) { out += Seg(x1, y, x2, y, width); y += step }
        }
        fun vLines(step: Float, width: Float) {
            var x = step
            while (x < w) { out += Seg(x, 0f, x, h, width); x += step }
        }
        when (template) {
            1 -> hLines(h / 34f, h * 0.08f, h * 0.95f)
            2 -> hLines(h / 22f, h * 0.08f, h * 0.95f)
            3 -> {
                hLines(h / 34f, h * 0.1f, h * 0.97f, 0f, w)
                out += Seg(w * 0.12f, 0f, w * 0.12f, h, 0.8f, red = true)
            }
            4 -> {
                out += Seg(0f, h * 0.1f, w, h * 0.1f, 0.9f)
                out += Seg(w * 0.3f, h * 0.1f, w * 0.3f, h * 0.78f, 0.9f)
                out += Seg(0f, h * 0.78f, w, h * 0.78f, 0.9f)
            }
            5 -> { val s = w / 12f; vLines(s, 0.6f); var y = s; while (y < h) { out += Seg(0f, y, w, y, 0.6f); y += s } }
            6 -> { val s = w / 30f; vLines(s, 0.35f); var y = s; while (y < h) { out += Seg(0f, y, w, y, 0.35f); y += s } }
        }
        return out
    }

    /** Ruling colour that stays visible on any page colour. */
    fun lineColor(bg: Int): Int = blend(bg, 0xFF6B7686.toInt(), 0.35f)

    private fun blend(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) * (1 - t) + Color.red(b) * t).toInt(),
        (Color.green(a) * (1 - t) + Color.green(b) * t).toInt(),
        (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt()
    )

    /** Draws a blank page on a canvas whose units are points. */
    fun draw(canvas: Canvas, spec: BlankSpec) {
        canvas.drawColor(spec.color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val lc = lineColor(spec.color)
        segments(spec.template, spec.widthPt, spec.heightPt).forEach { s ->
            paint.color = if (s.red) 0xFFE57373.toInt() else lc
            paint.strokeWidth = s.width
            canvas.drawLine(s.x1, s.y1, s.x2, s.y2, paint)
        }
    }

    fun render(spec: BlankSpec, widthPx: Int): Bitmap {
        val scale = widthPx / spec.widthPt
        val bmp = Bitmap.createBitmap(widthPx.coerceAtLeast(1), (spec.heightPt * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        draw(c, spec)
        return bmp
    }
}
