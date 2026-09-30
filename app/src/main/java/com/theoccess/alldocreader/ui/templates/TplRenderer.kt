package com.theoccess.alldocreader.ui.templates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max

/** Fonts offered in the editor's Fonts list (system families, so they work offline everywhere). */
object TplFonts {
    const val DEFAULT = "Roboto"

    class Font(val name: String, val family: String, val style: Int = Typeface.NORMAL)

    val ALL: List<Font> = listOf(
        Font("Carrois Gothic SC", "sans-serif-smallcaps"),
        Font("Coming Soon", "casual"),
        Font("Cutive Mono", "serif-monospace"),
        Font("Dancing Script", "cursive"),
        Font("Droid Sans Mono", "monospace"),
        Font("Noto Serif", "serif"),
        Font("Noto Serif Bold", "serif", Typeface.BOLD),
        Font("Roboto", "sans-serif"),
        Font("Roboto Black", "sans-serif-black"),
        Font("Roboto Condensed", "sans-serif-condensed"),
        Font("Roboto Light", "sans-serif-light"),
        Font("Roboto Medium", "sans-serif-medium"),
        Font("Roboto Thin", "sans-serif-thin")
    ).sortedBy { it.name }

    private val cache = HashMap<String, Typeface>()

    @Synchronized
    fun typeface(name: String, bold: Boolean, italic: Boolean): Typeface {
        val key = "$name|$bold|$italic"
        return cache.getOrPut(key) {
            val f = ALL.firstOrNull { it.name == name } ?: ALL.first { it.name == DEFAULT }
            var style = f.style
            if (bold) style = style or Typeface.BOLD
            if (italic) style = style or Typeface.ITALIC
            Typeface.create(f.family, style)
        }
    }
}

/** Draws template pages on any canvas whose units are PDF points (screen, bitmap or PDF). */
object TplRenderer {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val images = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    @Synchronized
    fun draw(ctx: Context, c: Canvas, page: TPage) {
        c.drawColor(page.bg)
        page.elements.forEach { drawEl(ctx, c, it) }
    }

    @Synchronized
    fun drawEl(ctx: Context, c: Canvas, el: TEl) {
        val (cx, cy) = center(el)
        c.save()
        if (el.rotation != 0f) c.rotate(el.rotation, cx, cy)
        when (el) {
            is TShape -> drawShape(c, el)
            is TText -> {
                c.translate(el.x, el.y)
                layout(el).draw(c)
            }
            is TImage -> drawImage(ctx, c, el)
        }
        c.restore()
    }

    private fun drawShape(c: Canvas, s: TShape) {
        val r = RectF(s.x, s.y, s.x + s.w, s.y + s.h)
        val p = if (s.stroke > 0f) stroke.apply { strokeWidth = s.stroke; color = s.color; shader = null } else fill
        if (s.stroke <= 0f) {
            p.color = s.color
            p.shader = s.color2?.let {
                if (s.horizontal) LinearGradient(r.left, 0f, r.right, 0f, s.color, it, Shader.TileMode.CLAMP)
                else LinearGradient(0f, r.top, 0f, r.bottom, s.color, it, Shader.TileMode.CLAMP)
            }
        }
        when (s.kind) {
            TShape.Kind.RECT -> if (s.radius > 0f) c.drawRoundRect(r, s.radius, s.radius, p) else c.drawRect(r, p)
            TShape.Kind.OVAL -> c.drawOval(r, p)
            TShape.Kind.LINE -> {
                stroke.strokeWidth = max(0.6f, s.h)
                stroke.color = s.color
                stroke.shader = null
                c.drawLine(s.x, s.y, s.x + s.w, s.y, stroke)
            }
        }
        fill.shader = null
    }

    private fun drawImage(ctx: Context, c: Canvas, im: TImage) {
        val bmp = bitmap(ctx, im) ?: return
        val src = Rect(
            (im.crop.left * bmp.width).toInt(), (im.crop.top * bmp.height).toInt(),
            (im.crop.right * bmp.width).toInt().coerceAtLeast(1), (im.crop.bottom * bmp.height).toInt().coerceAtLeast(1)
        )
        val dst = RectF(im.x, im.y, im.x + im.w, im.y + im.h)
        c.save()
        if (im.flipH || im.flipV) c.scale(if (im.flipH) -1f else 1f, if (im.flipV) -1f else 1f, dst.centerX(), dst.centerY())
        if (im.circle || im.corner > 0f) {
            val clip = Path()
            if (im.circle) clip.addOval(dst, Path.Direction.CW) else clip.addRoundRect(dst, im.corner, im.corner, Path.Direction.CW)
            c.clipPath(clip)
        }
        c.drawBitmap(bmp, src, dst, bmpPaint)
        c.restore()
    }

    @Synchronized
    fun bitmap(ctx: Context, im: TImage): Bitmap? {
        val key = im.path ?: im.res ?: return null
        images.get(key)?.let { return it }
        val b: Bitmap? = if (im.path != null) {
            decodeFile(im.path!!, 1400)
        } else {
            val id = ctx.resources.getIdentifier(im.res, "drawable", ctx.packageName)
            if (id == 0) null else ContextCompat.getDrawable(ctx, id)?.let { d ->
                val w = max(1, d.intrinsicWidth); val h = max(1, d.intrinsicHeight)
                val s = 900f / max(w, h)
                d.toBitmap((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            }
        }
        if (b != null) images.put(key, b)
        return b
    }

    private fun decodeFile(path: String, maxPx: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (o.outWidth / (sample * 2) >= maxPx || o.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    // ------------------------------------------------------------------ text

    private val layoutCache = LruCache<String, StaticLayout>(256)

    /** The text laid out in its box (list markers, style, alignment, spacing). */
    @Synchronized
    fun layout(t: TText): StaticLayout {
        val key = "${t.text}|${t.w}|${t.size}|${t.color}|${t.font}|${t.bold}|${t.italic}|${t.underline}|${t.strike}|${t.list}|${t.align}|${t.lineMult}"
        layoutCache.get(key)?.let { return it }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = t.size
            color = t.color
            typeface = TplFonts.typeface(t.font, t.bold, t.italic)
            isUnderlineText = t.underline
            isStrikeThruText = t.strike
            if (t.italic && typeface?.isItalic != true) textSkewX = -0.22f
            if (t.bold && typeface?.isBold != true) isFakeBoldText = true
        }
        val text = when (t.list) {
            1 -> t.text.split('\n').joinToString("\n") { "•  $it" }
            2 -> t.text.split('\n').mapIndexed { i, s -> "${i + 1}.  $s" }.joinToString("\n")
            else -> t.text
        }
        val align = when (t.align) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL }
        val width = max(1, t.w.toInt())
        val l = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(align)
                .setLineSpacing(0f, t.lineMult)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width, align, t.lineMult, 0f, false)
        }
        layoutCache.put(key, l)
        return l
    }

    /** Width / height of an element's box in points. */
    fun size(el: TEl): Pair<Float, Float> = when (el) {
        is TText -> el.w to max(el.size, layout(el).height.toFloat())
        is TImage -> el.w to el.h
        is TShape -> el.w to el.h
    }

    fun center(el: TEl): Pair<Float, Float> {
        val (w, h) = size(el)
        return (el.x + w / 2f) to (el.y + h / 2f)
    }

    /** Thumbnail of a page, [width] pixels wide. */
    fun thumbnail(ctx: Context, page: TPage, width: Int): Bitmap {
        val s = width / PAGE_W
        val bmp = Bitmap.createBitmap(width, (PAGE_H * s).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(s, s)
        draw(ctx, c, page)
        return bmp
    }
}
