package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * .docx reader that keeps the document's own look as far as possible:
 * page size and margins, paragraph styles (styles.xml), alignment, spacing, line spacing,
 * indents, lists, run formatting (size, bold, italic, underline, strike, colour, highlight,
 * font family, super/subscript), tables with their column widths, and inline or positioned
 * (anchored) pictures. Headers/footers, text boxes and charts are not drawn.
 */
object DocxParser {

    private const val EMU_PER_PT = 12700f
    private const val MAX_IMAGE_PX = 2000

    /** Resolved paragraph / run defaults of a style. null = not set here. */
    private class Style(
        var basedOn: String? = null,
        var size: Float? = null,
        var bold: Boolean? = null,
        var italic: Boolean? = null,
        var color: Int? = null,
        var font: String? = null,
        var align: Layout.Alignment? = null,
        var before: Float? = null,
        var after: Float? = null,
        var line: Float? = null,
        var indLeft: Float? = null,
        var indFirst: Float? = null
    )

    fun parse(file: File): ParsedDoc = ZipFile(file).use { zip ->
        val rels = readRels(zip)
        val (styles, defaults) = readStyles(zip)
        val entry = zip.getEntry("word/document.xml") ?: throw IllegalArgumentException("Not a Word document")
        zip.getInputStream(entry).use { input ->
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, "UTF-8")
            Walker(parser, zip, rels, styles, defaults).run()
        }
    }

    private fun readRels(zip: ZipFile): Map<String, String> {
        val entry = zip.getEntry("word/_rels/document.xml.rels") ?: return emptyMap()
        val map = HashMap<String, String>()
        zip.getInputStream(entry).use { input ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, "UTF-8")
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                    val id = p.getAttributeValue(null, "Id")
                    val target = p.getAttributeValue(null, "Target")
                    if (id != null && target != null) {
                        map[id] = if (target.startsWith("/")) target.removePrefix("/") else "word/$target"
                    }
                }
            }
        }
        return map
    }

    private fun onOff(v: String?) = v == null || (v != "0" && v != "false" && v != "none")

    private fun hex(v: String?): Int? {
        if (v == null || v.length != 6) return null
        return try { Color.parseColor("#$v") } catch (e: Exception) { null }
    }

    private fun parseAlign(v: String?): Layout.Alignment? = when (v) {
        "center" -> Layout.Alignment.ALIGN_CENTER
        "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
        "left", "start", "both", "distribute" -> Layout.Alignment.ALIGN_NORMAL
        else -> null
    }

    /** Paragraph styles plus the document defaults ("" key = docDefaults, "" + Normal merged). */
    private fun readStyles(zip: ZipFile): Pair<Map<String, Style>, Style> {
        val styles = HashMap<String, Style>()
        val defaults = Style(size = 11f, after = 0f, line = 1f)
        var defaultParagraphStyle: String? = null
        val entry = zip.getEntry("word/styles.xml") ?: return styles to defaults
        try {
            zip.getInputStream(entry).use { input ->
                val p = Xml.newPullParser()
                p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                p.setInput(input, "UTF-8")
                var cur: Style? = null
                var inDefaults = false
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType == XmlPullParser.END_TAG) {
                        if (p.name == "w:docDefaults") inDefaults = false
                        if (p.name == "w:style") cur = null
                        continue
                    }
                    if (p.eventType != XmlPullParser.START_TAG) continue
                    val target = if (inDefaults) defaults else cur
                    when (p.name) {
                        "w:docDefaults" -> inDefaults = true
                        "w:style" -> {
                            val type = p.getAttributeValue(null, "w:type")
                            val id = p.getAttributeValue(null, "w:styleId")
                            cur = if (type == "paragraph" && id != null) Style().also { styles[id] = it } else null
                            if (cur != null && p.getAttributeValue(null, "w:default") == "1") defaultParagraphStyle = id
                        }
                        "w:basedOn" -> cur?.basedOn = p.getAttributeValue(null, "w:val")
                        "w:sz" -> target?.size = p.getAttributeValue(null, "w:val")?.toFloatOrNull()?.div(2f)
                        "w:b" -> target?.bold = onOff(p.getAttributeValue(null, "w:val"))
                        "w:i" -> target?.italic = onOff(p.getAttributeValue(null, "w:val"))
                        "w:color" -> target?.color = hex(p.getAttributeValue(null, "w:val"))
                        "w:rFonts" -> target?.font = p.getAttributeValue(null, "w:ascii") ?: p.getAttributeValue(null, "w:hAnsi")
                        "w:jc" -> target?.align = parseAlign(p.getAttributeValue(null, "w:val"))
                        "w:spacing" -> target?.let { s ->
                            p.getAttributeValue(null, "w:before")?.toFloatOrNull()?.let { s.before = it / 20f }
                            p.getAttributeValue(null, "w:after")?.toFloatOrNull()?.let { s.after = it / 20f }
                            val rule = p.getAttributeValue(null, "w:lineRule")
                            p.getAttributeValue(null, "w:line")?.toFloatOrNull()?.let { if (rule == null || rule == "auto") s.line = it / 240f }
                        }
                        "w:ind" -> target?.let { s ->
                            (p.getAttributeValue(null, "w:left") ?: p.getAttributeValue(null, "w:start"))?.toFloatOrNull()?.let { s.indLeft = it / 20f }
                            p.getAttributeValue(null, "w:firstLine")?.toFloatOrNull()?.let { s.indFirst = it / 20f }
                            p.getAttributeValue(null, "w:hanging")?.toFloatOrNull()?.let { s.indFirst = -it / 20f }
                        }
                    }
                }
            }
        } catch (ignored: Exception) {
        }
        // Normal style values become part of the defaults
        defaultParagraphStyle?.let { styles[it] }?.let { n -> merge(defaults, n) }
        return styles to defaults
    }

    private fun merge(into: Style, from: Style) {
        from.size?.let { into.size = it }; from.bold?.let { into.bold = it }; from.italic?.let { into.italic = it }
        from.color?.let { into.color = it }; from.font?.let { into.font = it }; from.align?.let { into.align = it }
        from.before?.let { into.before = it }; from.after?.let { into.after = it }; from.line?.let { into.line = it }
        from.indLeft?.let { into.indLeft = it }; from.indFirst?.let { into.indFirst = it }
    }

    /** Defaults + basedOn chain + the style itself. */
    private fun resolve(styles: Map<String, Style>, defaults: Style, id: String?): Style {
        val chain = ArrayList<Style>()
        var cur = id?.let { styles[it] }
        var guard = 0
        while (cur != null && guard++ < 10) {
            chain += cur
            cur = cur.basedOn?.let { styles[it] }
        }
        val out = Style()
        merge(out, defaults)
        chain.asReversed().forEach { merge(out, it) }
        // Headings without explicit sizes still look like headings
        when (id?.lowercase()) {
            "title" -> { if (out.size == null || out.size == defaults.size) out.size = 26f; out.bold = out.bold ?: true }
            "heading1", "1" -> { if (out.size == null || out.size == defaults.size) out.size = 16f; out.bold = out.bold ?: true }
            "heading2", "2" -> { if (out.size == null || out.size == defaults.size) out.size = 13f; out.bold = out.bold ?: true }
            "heading3", "3" -> { if (out.size == null || out.size == defaults.size) out.size = 12f; out.bold = out.bold ?: true }
        }
        return out
    }

    private fun typefaceFor(font: String?): String? {
        val f = font?.lowercase() ?: return null
        return when {
            listOf("courier", "consolas", "mono", "lucida console").any { f.contains(it) } -> "monospace"
            listOf("times", "cambria", "georgia", "garamond", "book antiqua", "serif", "palatino", "century").any { f.contains(it) } -> "serif"
            else -> "sans-serif"
        }
    }

    private val SKIPPED = setOf("mc:Fallback", "w:txbxContent", "w:del", "w:delText", "w:instrText", "w:commentReference")

    private class Walker(
        val p: XmlPullParser,
        val zip: ZipFile,
        val rels: Map<String, String>,
        val styles: Map<String, Style>,
        val defaults: Style
    ) {
        val blocks = ArrayList<DocBlock>()
        var geometry = PageGeometry.DEFAULT
        var bodySectPr = false

        // paragraph state
        var para = SpannableStringBuilder()
        var pStyle: Style = resolve(styles, defaults, null)
        var align: Layout.Alignment? = null
        var isList = false
        var spaceBefore: Float? = null
        var spaceAfter: Float? = null
        var line: Float? = null
        var indLeft: Float? = null
        var indFirst: Float? = null
        var shading: Int? = null
        var inPPr = false
        var inSectPr = false

        // run state
        var bold: Boolean? = null; var italic: Boolean? = null; var underline = false; var strike = false
        var size: Float? = null; var color: Int? = null; var highlight: Int? = null; var font: String? = null
        var vanish = false; var vertAlign: String? = null
        var inRPr = false

        // drawing state
        var inDrawing = false
        var imgW = 0f; var imgH = 0f; var imgRel: String? = null
        var anchorMode = false; var anchorBehind = false; var anchorNoWrap = false
        var posH = 0f; var posV = 0f; var relH = "column"; var relV = "paragraph"; var alignH: String? = null; var alignV: String? = null
        var inPosH = false; var inPosV = false

        // tables (nested tables are flattened into the outer cell)
        var tableDepth = 0
        var rows = ArrayList<List<CharSequence>>()
        var row = ArrayList<CharSequence>()
        var cell = SpannableStringBuilder()
        var grid = ArrayList<Float>()

        fun run(): ParsedDoc {
            // Skipped subtrees: mc:Fallback repeats the mc:Choice content (would duplicate
            // pictures), text boxes / comments / footnote refs are not laid out in the flow.
            var skip = 0
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> {
                        if (skip > 0 || p.name in SKIPPED) skip++
                        else start(p.name)
                    }
                    XmlPullParser.END_TAG -> if (skip > 0) skip-- else end(p.name)
                }
            }
            return ParsedDoc(blocks, geometry)
        }

        private fun attr(name: String): String? = p.getAttributeValue(null, name)

        private fun start(name: String) {
            when (name) {
                "w:p" -> {
                    para = SpannableStringBuilder()
                    pStyle = resolve(styles, defaults, null)
                    align = null; isList = false; spaceBefore = null; spaceAfter = null; line = null
                    indLeft = null; indFirst = null; shading = null
                }
                "w:pPr" -> inPPr = true
                "w:sectPr" -> { inSectPr = true; bodySectPr = !inPPr }
                "w:pgSz" -> if (inSectPr) {
                    val w = attr("w:w")?.toFloatOrNull()?.div(20f) ?: geometry.width
                    val h = attr("w:h")?.toFloatOrNull()?.div(20f) ?: geometry.height
                    if (bodySectPr || blocks.isEmpty()) geometry = geometry.copy(width = w, height = h)
                }
                "w:pgMar" -> if (inSectPr && (bodySectPr || blocks.isEmpty())) {
                    fun m(n: String, d: Float) = attr(n)?.toFloatOrNull()?.div(20f) ?: d
                    geometry = geometry.copy(
                        left = m("w:left", geometry.left), right = m("w:right", geometry.right),
                        top = m("w:top", geometry.top), bottom = m("w:bottom", geometry.bottom)
                    )
                }
                "w:pStyle" -> pStyle = resolve(styles, defaults, attr("w:val"))
                "w:jc" -> if (inPPr) align = parseAlign(attr("w:val"))
                "w:numPr" -> if (inPPr) isList = true
                "w:pageBreakBefore" -> if (inPPr && tableDepth == 0 && onOff(attr("w:val"))) blocks += DocBlock.PageBreak
                "w:spacing" -> if (inPPr && !inRPr) {
                    attr("w:before")?.toFloatOrNull()?.let { spaceBefore = it / 20f }
                    attr("w:after")?.toFloatOrNull()?.let { spaceAfter = it / 20f }
                    val rule = attr("w:lineRule")
                    attr("w:line")?.toFloatOrNull()?.let { if (rule == null || rule == "auto") line = it / 240f }
                }
                "w:ind" -> if (inPPr) {
                    (attr("w:left") ?: attr("w:start"))?.toFloatOrNull()?.let { indLeft = it / 20f }
                    attr("w:firstLine")?.toFloatOrNull()?.let { indFirst = it / 20f }
                    attr("w:hanging")?.toFloatOrNull()?.let { indFirst = -it / 20f }
                }
                "w:shd" -> {
                    val fill = hex(attr("w:fill"))
                    if (inRPr && !inPPr) highlight = fill ?: highlight
                    else if (inPPr && !inRPr) shading = fill
                }
                "w:r" -> {
                    bold = null; italic = null; underline = false; strike = false
                    size = null; color = null; highlight = null; font = null; vanish = false; vertAlign = null
                }
                "w:rPr" -> inRPr = true
                "w:b" -> if (inRPr && !inPPr) bold = onOff(attr("w:val"))
                "w:i" -> if (inRPr && !inPPr) italic = onOff(attr("w:val"))
                "w:u" -> if (inRPr && !inPPr) underline = onOff(attr("w:val"))
                "w:strike", "w:dstrike" -> if (inRPr && !inPPr) strike = onOff(attr("w:val"))
                "w:sz" -> if (inRPr && !inPPr) size = attr("w:val")?.toFloatOrNull()?.div(2f)
                "w:color" -> if (inRPr && !inPPr) color = hex(attr("w:val"))
                "w:highlight" -> if (inRPr && !inPPr) highlight = highlightColor(attr("w:val"))
                "w:rFonts" -> if (inRPr && !inPPr) font = attr("w:ascii") ?: attr("w:hAnsi")
                "w:vanish" -> if (inRPr && !inPPr) vanish = onOff(attr("w:val"))
                "w:vertAlign" -> if (inRPr && !inPPr) vertAlign = attr("w:val")
                "w:t" -> appendText(p.nextText())
                "w:tab" -> if (!inPPr) appendText("\t")
                "w:br" -> if (attr("w:type") == "page") {
                    flushParagraph(keepEmpty = false)
                    if (tableDepth == 0) blocks += DocBlock.PageBreak
                } else appendText("\n")
                "w:cr" -> appendText("\n")
                "w:drawing", "w:pict" -> {
                    inDrawing = true; imgW = 0f; imgH = 0f; imgRel = null
                    anchorMode = false; anchorBehind = false; anchorNoWrap = false
                    posH = 0f; posV = 0f; relH = "column"; relV = "paragraph"; alignH = null; alignV = null
                }
                "wp:anchor" -> if (inDrawing) {
                    anchorMode = true
                    anchorBehind = attr("behindDoc") == "1"
                }
                "wp:positionH" -> if (inDrawing) { inPosH = true; relH = attr("relativeFrom") ?: "column" }
                "wp:positionV" -> if (inDrawing) { inPosV = true; relV = attr("relativeFrom") ?: "paragraph" }
                "wp:posOffset" -> if (inDrawing) {
                    val v = (p.nextText()?.trim()?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                    if (inPosH) posH = v else if (inPosV) posV = v
                    // nextText() consumed the end tag
                }
                "wp:align" -> if (inDrawing) {
                    val v = p.nextText()?.trim()
                    if (inPosH) alignH = v else if (inPosV) alignV = v
                }
                "wp:wrapNone" -> if (inDrawing) anchorNoWrap = true
                "wp:extent" -> if (inDrawing) {
                    imgW = (attr("cx")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                    imgH = (attr("cy")?.toFloatOrNull() ?: 0f) / EMU_PER_PT
                }
                "a:blip" -> if (inDrawing && imgRel == null) imgRel = attr("r:embed")
                "v:imagedata" -> if (inDrawing && imgRel == null) imgRel = attr("r:id")
                "w:tbl" -> {
                    if (tableDepth == 0) {
                        flushParagraph(keepEmpty = false)
                        rows = ArrayList()
                        grid = ArrayList()
                    }
                    tableDepth++
                }
                "w:gridCol" -> if (tableDepth == 1) attr("w:w")?.toFloatOrNull()?.let { grid.add(it) }
                "w:tr" -> if (tableDepth == 1) row = ArrayList()
                "w:tc" -> if (tableDepth == 1) cell = SpannableStringBuilder()
            }
        }

        private fun end(name: String) {
            when (name) {
                "w:pPr" -> inPPr = false
                "w:rPr" -> inRPr = false
                "w:sectPr" -> inSectPr = false
                "wp:positionH" -> inPosH = false
                "wp:positionV" -> inPosV = false
                "w:p" -> {
                    if (tableDepth > 0) {
                        if (cell.isNotEmpty()) cell.append('\n')
                        cell.append(para)
                    } else {
                        flushParagraph(keepEmpty = true)
                    }
                }
                "w:drawing", "w:pict" -> {
                    inDrawing = false
                    emitImage()
                }
                "w:tc" -> if (tableDepth == 1) row.add(cell)
                "w:tr" -> if (tableDepth == 1 && row.isNotEmpty()) rows.add(row)
                "w:tbl" -> {
                    tableDepth--
                    if (tableDepth == 0 && rows.isNotEmpty()) {
                        val total = grid.sum()
                        val widths = if (total > 0) grid.map { it / total } else null
                        blocks += DocBlock.Table(rows, widths)
                    }
                }
            }
        }

        private fun appendText(text: String?) {
            if (text.isNullOrEmpty() || vanish) return
            if (para.isEmpty() && isList && tableDepth == 0) para.append("•  ")
            val s = para.length
            para.append(text)
            val e = para.length
            val sz = size ?: pStyle.size ?: 11f
            val b = bold ?: pStyle.bold ?: false
            val it = italic ?: pStyle.italic ?: false
            para.setSpan(AbsoluteSizeSpan(sz.toInt().coerceAtLeast(4)), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            val style = when {
                b && it -> Typeface.BOLD_ITALIC
                b -> Typeface.BOLD
                it -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) para.setSpan(StyleSpan(style), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            typefaceFor(font ?: pStyle.font)?.let { para.setSpan(TypefaceSpan(it), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            if (underline) para.setSpan(UnderlineSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (strike) para.setSpan(StrikethroughSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            (color ?: pStyle.color)?.let { c -> para.setSpan(ForegroundColorSpan(c), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            highlight?.let { h -> para.setSpan(BackgroundColorSpan(h), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            when (vertAlign) {
                "superscript" -> para.setSpan(SuperscriptSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                "subscript" -> para.setSpan(SubscriptSpan(), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        private fun flushParagraph(keepEmpty: Boolean) {
            if (tableDepth > 0) return
            if (para.isEmpty() && !keepEmpty) return
            val baseSize = pStyle.size ?: 11f
            val text: SpannableStringBuilder = if (para.isEmpty()) {
                SpannableStringBuilder(" ").apply {
                    setSpan(AbsoluteSizeSpan(baseSize.toInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else para
            val left = indLeft ?: pStyle.indLeft ?: 0f
            val first = indFirst ?: pStyle.indFirst ?: 0f
            if (left != 0f || first != 0f) {
                text.setSpan(
                    LeadingMarginSpan.Standard((left + first).toInt().coerceAtLeast(0), left.toInt().coerceAtLeast(0)),
                    0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE
                )
            }
            blocks += DocBlock.Para(
                text,
                align ?: pStyle.align ?: Layout.Alignment.ALIGN_NORMAL,
                spaceBefore ?: pStyle.before ?: 0f,
                spaceAfter ?: pStyle.after ?: 0f,
                0f,
                (line ?: pStyle.line ?: 1f).coerceIn(0.6f, 4f),
                shading
            )
            para = SpannableStringBuilder()
        }

        private fun emitImage() {
            val rel = imgRel ?: return
            val path = rels[rel] ?: return
            val bmp = decode(path) ?: return
            if (tableDepth > 0) return
            val w = if (imgW > 0) imgW else bmp.width * 0.75f
            val h = if (imgH > 0) imgH else bmp.height * 0.75f
            if (anchorMode) {
                val anchor = Anchor(posH, posV, relH, relV, alignH, alignV, anchorNoWrap || anchorBehind, anchorBehind)
                blocks += DocBlock.Image(bmp, w, h, Layout.Alignment.ALIGN_NORMAL, anchor)
                return
            }
            // inline picture: text before it stays its own paragraph
            if (para.isNotEmpty()) {
                blocks += DocBlock.Para(SpannableStringBuilder(para), align ?: pStyle.align ?: Layout.Alignment.ALIGN_NORMAL,
                    spaceBefore ?: pStyle.before ?: 0f, 2f, 0f, line ?: pStyle.line ?: 1f)
                para = SpannableStringBuilder()
            }
            blocks += DocBlock.Image(bmp, w, h, align ?: pStyle.align ?: Layout.Alignment.ALIGN_NORMAL)
        }

        private fun decode(path: String): Bitmap? {
            val entry = zip.getEntry(path) ?: return null
            val lower = path.lowercase()
            if (!listOf(".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp").any { lower.endsWith(it) }) return null
            return try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > MAX_IMAGE_PX || bounds.outHeight / sample > MAX_IMAGE_PX) sample *= 2
                zip.getInputStream(entry).use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun highlightColor(v: String?): Int? = when (v) {
            "yellow" -> 0xFFFFFF00.toInt()
            "green" -> 0xFF00FF00.toInt()
            "cyan" -> 0xFF00FFFF.toInt()
            "magenta" -> 0xFFFF00FF.toInt()
            "blue" -> 0xFF0000FF.toInt()
            "red" -> 0xFFFF0000.toInt()
            "darkYellow" -> 0xFF808000.toInt()
            "lightGray" -> 0xFFC0C0C0.toInt()
            "darkGray" -> 0xFF808080.toInt()
            else -> null
        }
    }
}

/** Plain-text files: groups lines into blocks so very long files stay fast. */
object TxtParser {
    private const val MAX_CHARS = 3_000_000
    private const val LINES_PER_BLOCK = 120

    fun parse(file: File): List<DocBlock> {
        val text = file.inputStream().use { input ->
            val bytes = input.readBytes().let { if (it.size > MAX_CHARS) it.copyOf(MAX_CHARS) else it }
            String(bytes, Charsets.UTF_8)
        }
        val lines = text.replace("\r\n", "\n").split('\n')
        return lines.chunked(LINES_PER_BLOCK).map { chunk ->
            val s = SpannableStringBuilder(chunk.joinToString("\n").ifEmpty { " " })
            s.setSpan(AbsoluteSizeSpan(11), 0, s.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            DocBlock.Para(s, spaceAfter = 0f, lineMult = 1.1f)
        }
    }
}
