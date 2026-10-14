package com.theoccess.alldocreader.ui.pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Environment
import android.os.ParcelFileDescriptor
import com.theoccess.alldocreader.data.SavedFiles
import com.theoccess.alldocreader.ui.viewer.Converters
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Merge PDF / Split PDF, like the original app: "Merged_20261007_1239.pdf" in
 * Documents/AllDocumentReader/merged and "Split_20261007_1240.pdf" in …/split. Pages are copied as
 * they are (text stays text). Call from a background thread. Password-protected PDFs throw.
 */
object PdfTools {

    private fun base() = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AllDocumentReader")

    fun mergedDir(): File = File(base(), "merged").apply { mkdirs() }
    fun splitDir(): File = File(base(), "split").apply { mkdirs() }

    /** "Merged_20261007_1239" / "Split_20261007_1240". */
    fun stamp(prefix: String): String = prefix + "_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())

    /** [files] one after the other into one new PDF. */
    fun merge(context: Context, files: List<File>, progress: (Int) -> Unit = {}): File {
        require(files.size >= 2) { "need two PDFs" }
        val out = Converters.uniqueFile(mergedDir(), stamp("Merged"), "pdf")
        val sources = ArrayList<PDDocument>()
        try {
            PDDocument().use { dst ->
                files.forEachIndexed { i, f ->
                    val src = PDDocument.load(f)
                    sources += src   // must stay open until the merged file is saved
                    src.isAllSecurityToBeRemoved = true
                    for (p in 0 until src.numberOfPages) dst.importPage(src.getPage(p))
                    progress(((i + 1) * 90) / files.size)
                }
                dst.save(out)
            }
        } catch (e: Throwable) {
            out.delete()
            throw e
        } finally {
            sources.forEach { try { it.close() } catch (ignored: Exception) {} }
        }
        progress(100)
        SavedFiles.onSaved(context.applicationContext, out)
        return out
    }

    /** One output PDF of a split: its file name (without .pdf) and pages (1-based, in this order). */
    class Part(var name: String, val pages: List<Int>)

    /** Writes every part as its own PDF into …/split. */
    fun split(context: Context, file: File, parts: List<Part>, progress: (Int) -> Unit = {}): List<File> {
        require(parts.isNotEmpty())
        val outs = ArrayList<File>()
        try {
            PDDocument.load(file).use { src ->
                src.isAllSecurityToBeRemoved = true
                val n = src.numberOfPages
                parts.forEachIndexed { i, part ->
                    val out = Converters.uniqueFile(splitDir(), clean(part.name), "pdf")
                    PDDocument().use { dst ->
                        for (p in part.pages) if (p in 1..n) dst.importPage(src.getPage(p - 1))
                        dst.save(out)
                    }
                    outs += out
                    progress(((i + 1) * 100) / parts.size)
                }
            }
        } catch (e: Throwable) {
            outs.forEach { it.delete() }
            throw e
        }
        outs.forEach { SavedFiles.onSaved(context.applicationContext, it) }
        return outs
    }

    private fun clean(name: String) = name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").removeSuffix(".pdf").ifEmpty { stamp("Split") }

    /** Fixed range: groups of [size] pages → parts "Split_…", "Split_…(1)", … */
    fun fixedParts(pages: Int, size: Int): List<Part> {
        val name = stamp("Split")
        return (1..pages step size.coerceAtLeast(1)).mapIndexed { i, start ->
            Part(if (i == 0) name else "$name($i)", (start..minOf(pages, start + size - 1)).toList())
        }
    }

    /** "1,2,5" for a part's page list. */
    fun pagesLabel(pages: List<Int>): String = pages.joinToString(",")

    /** Number of pages (fast, platform renderer), or -1 when the PDF can't be opened. */
    fun pageCount(file: File): Int = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { it.pageCount } }
    } catch (e: Throwable) {
        -1
    }

    /** First page as a picture [width] px wide (for the "notebook" preview), or null. */
    fun firstPage(file: File, width: Int): Bitmap? = try {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) null else r.openPage(0).use { p ->
                    val h = (width * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(Color.WHITE)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    } catch (e: Throwable) {
        null
    }
}
