package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File

/** Renders PDF pages with the platform PdfRenderer; text for search comes from PDFBox. */
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
    private val texts = HashMap<Int, String>()

    init {
        for (i in 0 until renderer.pageCount) {
            renderer.openPage(i).use { p ->
                widths[i] = p.width.toFloat()
                heights[i] = p.height.toFloat()
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
    override fun pageText(index: Int): String {
        texts[index]?.let { return it }
        val doc = textDoc ?: try {
            PDDocument.load(file).also { textDoc = it }
        } catch (e: Exception) {
            return ""
        }
        val text = try {
            PDFTextStripper().apply {
                startPage = index + 1
                endPage = index + 1
            }.getText(doc)
        } catch (e: Exception) {
            ""
        }
        texts[index] = text
        return text
    }

    @Synchronized
    override fun close() {
        try { renderer.close() } catch (ignored: Exception) {}
        try { fd.close() } catch (ignored: Exception) {}
        try { textDoc?.close() } catch (ignored: Exception) {}
        textDoc = null
    }
}
