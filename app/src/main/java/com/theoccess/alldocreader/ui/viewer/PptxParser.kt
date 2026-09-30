package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * Minimal PowerPoint (.pptx) reader: slide size, background colour, positioned text boxes
 * (size, bold, italic, underline, colour, alignment, bullets), filled rectangles and pictures.
 * Themes, layouts/masters, charts and animations are not rendered.
 */
object PptxParser {

    private const val EMU_PER_PT = 12700f

    sealed class Element(val box: RectF) {
        class Text(box: RectF, val text: CharSequence, val fill: Int?, val placeholder: String?) : Element(box)
        class Picture(box: RectF, val bitmap: Bitmap) : Element(box)
    }

    class Slide(val background: Int, val elements: List<Element>) {
        val text: String get() = elements.filterIsInstance<Element.Text>().joinToString("\n") { it.text.toString() }
    }

    class Deck(val widthPt: Float, val heightPt: Float, val slides: List<Slide>)

    fun parse(file: File): Deck = ZipFile(file).use { zip ->
        val presentation = zip.getEntry("ppt/presentation.xml") ?: throw IllegalArgumentException("Not a PowerPoint file")
        var w = 720f
        var h = 405f
        val order = ArrayList<String>()
        zip.getInputStream(presentation).use { input ->
            val p = parser(input)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType != XmlPullParser.START_TAG) continue
                when (p.name) {
                    "p:sldSz" -> {
                        w = (p.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 9144000f) / EMU_PER_PT
                        h = (p.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 5143500f) / EMU_PER_PT
                    }
                    "p:sldId" -> p.getAttributeValue(null, "r:id")?.let { order += it }
                }
            }
        }
        val rels = readRels(zip, "ppt/_rels/presentation.xml.rels", "ppt/")
        val slidePaths = order.mapNotNull { rels[it] }.ifEmpty {
            // fall back to slide1.xml, slide2.xml… when there is no order list
            zip.entries().asSequence().map { it.name }
                .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                .sortedBy { it.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0 }
                .toList()
        }
        val slides = slidePaths.mapNotNull { path -> zip.getEntry(path)?.let { parseSlide(zip, path, w, h) } }
        Deck(w, h, slides)
    }

    private fun parser(input: java.io.InputStream): XmlPullParser = Xml.newPullParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(input, "UTF-8")
    }

    /** Maps relationship ids to zip paths, resolving "../media/x.png" against [baseDir]. */
    private fun readRels(zip: ZipFile, relsPath: String, baseDir: String): Map<String, String> {
        val entry = zip.getEntry(relsPath) ?: return emptyMap()
        val map = HashMap<String, String>()
        zip.getInputStream(entry).use { input ->
            val p = parser(input)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.getAttributeValue(null, "Id") ?: continue
                    val target = p.getAttributeValue(null, "Target") ?: continue
                    map[id] = resolve(baseDir, target)
                }
            }
        }
        return map
    }

    private fun resolve(baseDir: String, target: String): String {
        if (target.startsWith("/")) return target.removePrefix("/")
        val parts = (baseDir + target).split('/').toMutableList()
        val out = ArrayList<String>()
        for (part in parts) when (part) {
            ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
            ".", "" -> Unit
            else -> out += part
        }
        return out.joinToString("/")
    }

    private fun schemeColor(v: String?): Int? = when (v) {
        "tx1", "dk1" -> Color.BLACK
        "bg1", "lt1" -> Color.WHITE
        "tx2", "dk2" -> 0xFF44546A.toInt()
        "bg2", "lt2" -> 0xFFE7E6E6.toInt()
        "accent1" -> 0xFF4472C4.toInt()
        "accent2" -> 0xFFED7D31.toInt()
        "accent3" -> 0xFFA5A5A5.toInt()
        "accent4" -> 0xFFFFC000.toInt()
        "accent5" -> 0xFF5B9BD5.toInt()
        "accent6" -> 0xFF70AD47.toInt()
        else -> null
    }

    private fun hex(v: String?): Int? =
        if (v == null || v.length != 6) null else try { Color.parseColor("#$v") } catch (e: Exception) { null }

    private fun parseSlide(zip: ZipFile, path: String, slideW: Float, slideH: Float): Slide {
        val dir = path.substringBeforeLast('/') + "/"
        val rels = readRels(zip, dir + "_rels/" + path.substringAfterLast('/') + ".rels", dir)
        val elements = ArrayList<Element>()
        var background = Color.WHITE

        // group transforms: (offX, offY, scaleX, scaleY, chOffX, chOffY)
        val groups = ArrayList<FloatArray>()

        // current shape state
        var inShape = false; var inPic = false; var inBg = false; var inRPr = false; var inSpPr = false; var inTx = false
        var depthGrpSpPr = false
        var x = 0f; var y = 0f; var cx = 0f; var cy = 0f; var hasXfrm = false
        var chX = 0f; var chY = 0f; var chCx = 0f; var chCy = 0f
        var fill: Int? = null
        var ph: String? = null
        var blip: String? = null
        var text = SpannableStringBuilder()
        var para = SpannableStringBuilder()
        var algn = Layout.Alignment.ALIGN_NORMAL
        var bullet = false
        var sz = 18f; var bold = false; var italic = false; var underline = false; var color: Int? = null
        var runColorTarget = false

        fun mapX(v: Float): Float {
            var r = v
            for (g in groups.asReversed()) r = g[0] + (r - g[4]) * g[2]
            return r
        }
        fun mapY(v: Float): Float {
            var r = v
            for (g in groups.asReversed()) r = g[1] + (r - g[5]) * g[3]
            return r
        }
        fun scaleX(): Float = groups.fold(1f) { a, g -> a * g[2] }
        fun scaleY(): Float = groups.fold(1f) { a, g -> a * g[3] }

        fun box(): RectF = if (hasXfrm) {
            val l = mapX(x); val t = mapY(y)
            RectF(l, t, l + cx * scaleX(), t + cy * scaleY())
        } else when (ph) {
            "title", "ctrTitle" -> RectF(slideW * 0.06f, slideH * 0.06f, slideW * 0.94f, slideH * 0.24f)
            "subTitle" -> RectF(slideW * 0.1f, slideH * 0.55f, slideW * 0.9f, slideH * 0.8f)
            else -> RectF(slideW * 0.06f, slideH * 0.27f, slideW * 0.94f, slideH * 0.92f)
        }

        zip.getInputStream(zip.getEntry(path)).use { input ->
            val p = parser(input)
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                val name = p.name
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (name) {
                        "p:bg" -> inBg = true
                        "p:grpSpPr" -> depthGrpSpPr = true
                        "p:sp" -> {
                            inShape = true; hasXfrm = false; fill = null; ph = null
                            text = SpannableStringBuilder()
                        }
                        "p:pic" -> { inPic = true; hasXfrm = false; blip = null }
                        "p:spPr" -> inSpPr = true
                        "p:ph" -> if (inShape) ph = p.getAttributeValue(null, "type") ?: "body"
                        "a:off" -> {
                            val ox = (p.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                            val oy = (p.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                            if (depthGrpSpPr) { chX = ox; chY = oy; x = ox; y = oy } else { x = ox; y = oy; hasXfrm = true }
                        }
                        "a:ext" -> {
                            val ex = (p.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                            val ey = (p.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                            if (depthGrpSpPr) { cx = ex; cy = ey } else { cx = ex; cy = ey }
                        }
                        "a:chOff" -> if (depthGrpSpPr) {
                            chX = (p.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                            chY = (p.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                        }
                        "a:chExt" -> if (depthGrpSpPr) {
                            chCx = (p.getAttributeValue(null, "cx")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                            chCy = (p.getAttributeValue(null, "cy")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                        }
                        "a:srgbClr", "a:schemeClr" -> {
                            val c = if (name == "a:srgbClr") hex(p.getAttributeValue(null, "val"))
                            else schemeColor(p.getAttributeValue(null, "val"))
                            when {
                                inBg && c != null -> background = c
                                inRPr && runColorTarget && c != null -> color = c
                                inSpPr && inShape && c != null && fill == null -> fill = c
                            }
                        }
                        "a:solidFill" -> if (inRPr) runColorTarget = true
                        "a:ln" -> if (inSpPr) inSpPr = false // ignore outline colours
                        "a:blip" -> if (inPic) blip = p.getAttributeValue(null, "r:embed")
                        "p:txBody" -> inTx = true
                        "a:p" -> if (inTx) {
                            para = SpannableStringBuilder(); algn = Layout.Alignment.ALIGN_NORMAL; bullet = false
                        }
                        "a:pPr" -> if (inTx) {
                            algn = when (p.getAttributeValue(null, "algn")) {
                                "ctr" -> Layout.Alignment.ALIGN_CENTER
                                "r" -> Layout.Alignment.ALIGN_OPPOSITE
                                else -> Layout.Alignment.ALIGN_NORMAL
                            }
                        }
                        "a:buChar" -> if (inTx) bullet = true
                        "a:r", "a:fld" -> {
                            sz = if (ph == "title" || ph == "ctrTitle") 36f else 18f
                            bold = false; italic = false; underline = false; color = null
                        }
                        "a:rPr" -> {
                            inRPr = true
                            p.getAttributeValue(null, "sz")?.toFloatOrNull()?.let { sz = it / 100f }
                            bold = p.getAttributeValue(null, "b") == "1"
                            italic = p.getAttributeValue(null, "i") == "1"
                            underline = p.getAttributeValue(null, "u").let { it != null && it != "none" }
                        }
                        "a:t" -> if (inTx) {
                            val t = p.nextText() ?: ""
                            if (para.isEmpty() && bullet) para.append("•  ")
                            val s = para.length
                            para.append(t)
                            val e = para.length
                            if (e > s) {
                                para.setSpan(AbsoluteSizeSpan(sz.toInt().coerceAtLeast(4)), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                val style = when {
                                    bold && italic -> Typeface.BOLD_ITALIC
                                    bold -> Typeface.BOLD
                                    italic -> Typeface.ITALIC
                                    else -> Typeface.NORMAL
                                }
                                if (style != Typeface.NORMAL) para.setSpan(StyleSpan(style), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                if (underline) para.setSpan(UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                                color?.let { para.setSpan(ForegroundColorSpan(it), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                            }
                        }
                        "a:br" -> if (inTx) para.append('\n')
                    }
                    XmlPullParser.END_TAG -> when (name) {
                        "p:bg" -> inBg = false
                        "p:grpSpPr" -> {
                            depthGrpSpPr = false
                            val sx = if (chCx > 0) cx / chCx else 1f
                            val sy = if (chCy > 0) cy / chCy else 1f
                            groups += floatArrayOf(x, y, sx, sy, chX, chY)
                        }
                        "p:grpSp" -> if (groups.isNotEmpty()) groups.removeAt(groups.lastIndex)
                        "p:spPr" -> inSpPr = false
                        "a:rPr" -> { inRPr = false; runColorTarget = false }
                        "a:p" -> if (inTx) {
                            if (text.isNotEmpty()) text.append('\n')
                            val start = text.length
                            text.append(para)
                            if (algn != Layout.Alignment.ALIGN_NORMAL && text.length > start) {
                                text.setSpan(android.text.style.AlignmentSpan.Standard(algn), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                        "p:txBody" -> inTx = false
                        "p:sp" -> {
                            inShape = false
                            val b = box()
                            if (text.isNotBlank() || fill != null) elements += Element.Text(b, text, fill, ph)
                        }
                        "p:pic" -> {
                            inPic = false
                            val target = blip?.let { rels[it] }
                            val bmp = target?.let { decode(zip, it) }
                            if (bmp != null) elements += Element.Picture(box(), bmp)
                        }
                    }
                }
            }
        }
        return Slide(background, elements)
    }

    private fun decode(zip: ZipFile, path: String): Bitmap? {
        val entry = zip.getEntry(path) ?: return null
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / s > 1600 || o.outHeight / s > 1600) s *= 2
            zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s }) }
        } catch (e: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------ drawing

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        color = Color.BLACK
        textSize = 18f
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    /** Draws a slide on a canvas whose units are points. */
    @Synchronized
    fun draw(canvas: Canvas, deck: Deck, index: Int) {
        val slide = deck.slides[index]
        canvas.drawColor(slide.background)
        for (e in slide.elements) when (e) {
            is Element.Picture -> canvas.drawBitmap(e.bitmap, null, e.box, imagePaint)
            is Element.Text -> {
                e.fill?.let { fillPaint.color = it; canvas.drawRect(e.box, fillPaint) }
                if (e.text.isNotBlank()) {
                    val inset = 7f
                    val width = (e.box.width() - 2 * inset).toInt().coerceAtLeast(20)
                    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        StaticLayout.Builder.obtain(e.text, 0, e.text.length, textPaint, width)
                            .setLineSpacing(0f, 1.05f).setIncludePad(false).build()
                    } else {
                        @Suppress("DEPRECATION")
                        StaticLayout(e.text, textPaint, width, Layout.Alignment.ALIGN_NORMAL, 1.05f, 0f, false)
                    }
                    canvas.save()
                    // titles are vertically centred like PowerPoint's default
                    val dy = if (e.placeholder == "title" || e.placeholder == "ctrTitle")
                        ((e.box.height() - layout.height) / 2).coerceAtLeast(0f) else inset
                    canvas.translate(e.box.left + inset, e.box.top + dy)
                    layout.draw(canvas)
                    canvas.restore()
                }
            }
        }
    }
}

/** PowerPoint slides for the viewer. */
class PptxPageSource(val deck: PptxParser.Deck) : PageSource {
    override val pageCount: Int get() = deck.slides.size
    override fun pageWidth(index: Int) = deck.widthPt
    override fun pageHeight(index: Int) = deck.heightPt

    override fun render(index: Int, targetWidth: Int): Bitmap {
        val w = targetWidth.coerceAtLeast(1)
        val scale = w / deck.widthPt
        val bmp = Bitmap.createBitmap(w, (deck.heightPt * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(scale, scale)
        PptxParser.draw(c, deck, index)
        return bmp
    }

    override fun pageText(index: Int): String = deck.slides[index].text
    override fun close() = Unit
}
