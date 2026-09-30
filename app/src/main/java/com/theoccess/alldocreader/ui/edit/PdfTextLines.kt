package com.theoccess.alldocreader.ui.edit

import android.graphics.RectF
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.abs

/** Finds the lines of real text on every page (scanned PDFs have none → Edit text is off). */
object PdfTextLines {

    fun extract(file: File, maxPages: Int = 300): Map<Int, List<TextLine>> {
        val out = HashMap<Int, MutableList<TextLine>>()
        PDDocument.load(file).use { doc ->
            val n = minOf(doc.numberOfPages, maxPages)
            for (i in 0 until n) {
                val page = doc.getPage(i)
                val box = page.cropBox
                val rot = ((page.rotation % 360) + 360) % 360
                val shownW = if (rot % 180 == 0) box.width else box.height
                if (shownW <= 0f) continue
                val lines = ArrayList<TextLine>()
                // PDFTextStripper hands over one word (gap-separated run) per writeString call;
                // join the runs of one visual line and emit it at each line break.
                val stripper = object : PDFTextStripper() {
                    private val buf = StringBuilder()
                    private var lnLeft = Float.MAX_VALUE
                    private var lnRight = 0f
                    private var lnBase = 0f
                    private var lnSize = 0f

                    fun flushLine() {
                        val t = buf.toString().trim()
                        if (t.isNotEmpty() && lnRight > lnLeft) {
                            val size = if (lnSize > 0f) lnSize else 10f
                            val top = lnBase - size * 0.92f
                            val bottom = lnBase + size * 0.26f
                            lines += TextLine(t, RectF(lnLeft / shownW, top / shownW, lnRight / shownW, bottom / shownW), size / shownW)
                        }
                        buf.setLength(0)
                        lnLeft = Float.MAX_VALUE; lnRight = 0f; lnBase = 0f; lnSize = 0f
                    }

                    override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                        val tps = textPositions ?: return
                        if (tps.isEmpty()) return
                        val first = tps[0]
                        if (buf.isNotEmpty()) {
                            val s = maxOf(lnSize, first.fontSizeInPt, first.heightDir, 1f)
                            // another baseline or a wide gap (next column) → new line
                            if (abs(first.yDirAdj - lnBase) > s * 0.5f || first.xDirAdj - lnRight > s * 1.5f || first.xDirAdj < lnLeft - s) flushLine()
                            else buf.append(' ')
                        }
                        buf.append(text.orEmpty())
                        tps.forEach { p ->
                            lnLeft = minOf(lnLeft, p.xDirAdj)
                            lnRight = maxOf(lnRight, p.xDirAdj + p.widthDirAdj)
                            lnBase = maxOf(lnBase, p.yDirAdj)
                            lnSize = maxOf(lnSize, p.fontSizeInPt, p.heightDir)
                        }
                    }

                    override fun writeLineSeparator() {
                        flushLine()
                    }
                }
                stripper.sortByPosition = true
                stripper.startPage = i + 1
                stripper.endPage = i + 1
                try { stripper.getText(doc) } catch (ignored: Exception) {}
                stripper.flushLine()
                if (lines.isNotEmpty()) out[i] = lines
            }
        }
        return out
    }
}
