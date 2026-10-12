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

/** Renders PDF pages with the platform PdfRenderer; text and positions for search come from PDFBox. */
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
    private val ocr = HashMap<Int, List<PictureOcr.Line>>()
    @Volatile private var closed = false

    private class PageChars(val text: String, val pos: List<TextPosition?>)

    private class Collector : PDFTextStripper() {
        val sb = StringBuilder()
        val pos = ArrayList<TextPosition?>()
        override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
            for (tp in textPositions) {
                val u = tp.unicode ?: continue
                for (ch in u) {
                    sb.append(ch)
                    pos.add(tp)
                }
            }
        }
        override fun writeWordSeparator() {
            sb.append(' ')
            pos.add(null)
        }
        override fun writeLineSeparator() {
            sb.append('\n')
            pos.add(null)
        }
    }

    init {
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { p ->
                widths[i] = p.width.toFloat().coerceAtLeast(1f)
                heights[i] = p.height.toFloat().coerceAtLeast(1f)
            }
        }
    }

    override val pageCount: Int get() = renderer.pageCount
    override fun pageWidth(index: Int) = widths[index]
    override fun pageHeight(index: Int) = heights[index]

    @Synchronized
    override fun render(index: Int, targetWidth: Int): Bitmap {
        val w = targetWidth.coerceAtLeast(1)
        val h = (w * heights[index] / widths[index]).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        renderer.openPage(index).use { it.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
        return bmp
    }

    @Synchronized
    private fun chars(index: Int): PageChars {
        texts[index]?.let { return it }
        val doc = textDoc ?: try {
            PDDocument.load(file).also { textDoc = it }
        } catch (_: Exception) {
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
        } catch (_: Exception) {
            PageChars("", emptyList())
        }
        texts[index] = pc
        return pc
    }

    override fun pageText(index: Int): String {
        val t = chars(index).text
        return if (t.isNotBlank()) t else PictureOcr.text(ocrPage(index))
    }

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
        } catch (_: Exception) {
            return emptyList()
        }
        synchronized(ocr) { ocr[index] = lines }
        return lines
    }

    override fun prepareSearch() {
        for (i in 0 until minOf(pageCount, 300)) {
            if (closed) return
            if (chars(i).text.isBlank()) ocrPage(i)
        }
    }

    override fun findMatches(index: Int, query: String): List<List<RectF>> {
        val pc = chars(index)
        if (pc.text.isBlank()) return PictureOcr.matches(ocrPage(index), query)
        val out = ArrayList<List<RectF>>()
        for (at in SearchText.find(pc.text, query)) {
            val len = SearchText.matchLength(pc.text, at, query)
            val boxes = ArrayList<RectF>()
            for (i in at until minOf(at + len, pc.pos.size)) {
                val tp = pc.pos[i] ?: continue
                val fs = tp.fontSizeInPt.takeIf { it in 2f..400f } ?: (tp.heightDir / 0.7f).coerceAtLeast(4f)
                val r = RectF(
                    tp.xDirAdj,
                    tp.yDirAdj - fs * 0.8f,
                    tp.xDirAdj + maxOf(tp.widthDirAdj, 1f),
                    tp.yDirAdj + fs * 0.22f
                )
                val last = boxes.lastOrNull()
                if (last != null && kotlin.math.abs(last.centerY() - r.centerY()) < fs * 0.6f) last.union(r)
                else boxes += r
            }
            if (boxes.isNotEmpty()) out += boxes
        }
        return out
    }

    @Synchronized
    override fun close() {
        closed = true
        try { renderer.close() } catch (_: Exception) {}
        try { fd.close() } catch (_: Exception) {}
        try { textDoc?.close() } catch (_: Exception) {}
        textDoc = null
    }
}
