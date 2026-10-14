package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File

/** Renders PDF pages with the platform PdfRenderer; text and its positions for search come from PDFBox. */
class PdfPageSource(private val file: File) : PageSource {

    private val fd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(fd)
    } catch (e: Exception) {
        fd.close()
        throw e
    }
    private val widths = FloatArray(renderer.pageCount)
    private val heights = FloatArray(renderer.pageCount)
    private var textDoc: PDDocument? = null
    private val texts = HashMap<Int, PageChars>()

    /** A page's text, and for every character the glyph it came from (null for added spaces / line breaks). */
    private class PageChars(val text: String, val pos: List<TextPosition?>)

    private class Collector : PDFTextStripper() {
        val sb = StringBuilder()
        val pos = ArrayList<TextPosition?>()
        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            for (tp in textPositions) {
                val u = tp.unicode ?: continue
                for (ch in u) { sb.append(ch); pos.add(tp) }
            }
        }
        override fun writeWordSeparator() { sb.append(' '); pos.add(null) }
        override fun writeLineSeparator() { sb.append('\n'); pos.add(null) }
    }

    /** Which page sizes have been read. Only page 1 is measured before the PDF is shown (long PDFs open at once); the rest in [finishLoading]. */
    private val known = BooleanArray(renderer.pageCount)

    init {
        if (renderer.pageCount > 0) measure(0)
    }

    private fun measure(i: Int) {
        renderer.openPage(i).use { p ->
            widths[i] = p.width.toFloat().coerceAtLeast(1f)
            heights[i] = p.height.toFloat().coerceAtLeast(1f)
        }
        known[i] = true
    }

    override val pageCount: Int get() = renderer.pageCount
    // pages not measured yet are assumed to be the size of the first page (most PDFs) until then
    override fun pageWidth(index: Int) = if (known[index]) widths[index] else widths[0]
    override fun pageHeight(index: Int) = if (known[index]) heights[index] else heights[0]

    @Synchronized
    override fun render(index: Int, targetWidth: Int): Bitmap {
        if (!known[index]) measure(index)
        val w = targetWidth.coerceAtLeast(1)
        val h = (w * heights[index] / widths[index]).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        renderer.openPage(index).use { it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        return bmp
    }

    /** Reads the remaining page sizes in small steps (rendering goes on in between). */
    override fun finishLoading(listener: PageSource.LoadListener) {
        val n = pageCount
        if (n == 0) return
        val w0 = widths[0]
        val h0 = heights[0]
        var i = 1
        while (i < n) {
            val changed = ArrayList<Int>()
            synchronized(this) {
                if (closed) return
                val end = minOf(n, i + 25)
                while (i < end) {
                    if (!known[i]) measure(i)
                    // a different size than page 1: its place in the list has to be re-measured
                    if (kotlin.math.abs(widths[i] - w0) > 0.5f || kotlin.math.abs(heights[i] - h0) > 0.5f) changed += i
                    i++
                }
            }
            if (changed.isNotEmpty()) listener.onPagesResized(changed)
        }
    }

    private var closed = false

    @Synchronized
    private fun chars(index: Int): PageChars {
        texts[index]?.let { return it }
        val doc = textDoc ?: try {
            PDDocument.load(file).also { textDoc = it }
        } catch (e: Exception) {
            return PageChars("", emptyList())
        }
        val pc = try {
            val c = Collector().apply {
                sortByPosition = true
                startPage = index + 1
                endPage = index + 1
            }
            c.getText(doc)
            PageChars(c.sb.toString(), c.pos)
        } catch (e: Exception) {
            PageChars("", emptyList())
        }
        texts[index] = pc
        return pc
    }

    override fun pageText(index: Int): String {
        val t = chars(index).text
        return if (t.isNotBlank()) t else PictureOcr.text(ocrPage(index))
    }

    // ------------------------------------------------------------------ scanned pages

    private val ocr = HashMap<Int, List<PictureOcr.Line>>()

    /** A page without any text (scanned): its words read from the page picture (cached). */
    private fun ocrPage(index: Int): List<PictureOcr.Line> {
        synchronized(ocr) { ocr[index]?.let { return it } }
        if (closed) return emptyList()
        val lines = try {
            val bmp = render(index, 1600)
            try {
                PictureOcr.read(bmp, RectF(0f, 0f, pageWidth(index), pageHeight(index)))
            } finally {
                bmp.recycle()
            }
        } catch (e: Exception) {
            return emptyList()   // recogniser not ready yet: tried again next search
        }
        synchronized(ocr) { ocr[index] = lines }
        return lines
    }

    /** Opening search: read the scanned pages in the background so results come fast. */
    override fun prepareSearch() {
        for (i in 0 until minOf(pageCount, 300)) {
            if (closed) return
            if (chars(i).text.isBlank()) ocrPage(i)
        }
    }

    /** Boxes around each occurrence, merged per text line, in page points (top-left origin). */
    override fun findMatches(index: Int, query: String): List<List<RectF>> {
        val pc = chars(index)
        if (pc.text.isBlank()) return PictureOcr.matches(ocrPage(index), query)
        val out = ArrayList<List<RectF>>()
        for (at in SearchText.find(pc.text, query)) {
            val len = SearchText.matchLength(pc.text, at, query)
            val boxes = ArrayList<RectF>()
            for (i in at until minOf(at + len, pc.pos.size)) {
                val tp = pc.pos[i] ?: continue
                // yDirAdj is the baseline: letters reach ~0.8 font size above it and ~0.2 below
                val fs = tp.fontSizeInPt.takeIf { it in 2f..400f } ?: (tp.heightDir / 0.7f).coerceAtLeast(4f)
                val h = fs
                val r = RectF(tp.xDirAdj, tp.yDirAdj - fs * 0.8f, tp.xDirAdj + maxOf(tp.widthDirAdj, 1f), tp.yDirAdj + fs * 0.22f)
                val last = boxes.lastOrNull()
                if (last != null && kotlin.math.abs(last.centerY() - r.centerY()) < h * 0.6f) last.union(r) else boxes += r
            }
            if (boxes.isNotEmpty()) out += boxes
        }
        return out
    }

    @Synchronized
    override fun close() {
        closed = true
        try { renderer.close() } catch (ignored: Exception) {}
        try { fd.close() } catch (ignored: Exception) {}
        try { textDoc?.close() } catch (ignored: Exception) {}
        textDoc = null
    }
}
