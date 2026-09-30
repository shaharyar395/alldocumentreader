package com.theoccess.alldocreader.ui.viewer

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.media.MediaScannerConnection
import android.os.Environment
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Word/TXT → PDF and PDF → Word conversions that keep the look. Call from a background thread. */
object Converters {

    private const val EMU_PER_PT = 12700f
    private const val RENDER_SCALE = 2f
    private const val MAX_RENDER_PX = 2200f

    /** Documents/AllDocumentReader/convert, like the original app's output folder. */
    fun outputDir(): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "AllDocumentReader/convert")
            .apply { mkdirs() }

    /** "name.pdf", or "name (1).pdf" if that exists. */
    fun uniqueFile(dir: File, base: String, ext: String): File {
        var f = File(dir, "$base.$ext")
        var i = 1
        while (f.exists()) f = File(dir, "$base ($i).$ext").also { i++ }
        return f
    }

    fun docToPdf(context: Context, layout: DocLayout, baseName: String, progress: (Int) -> Unit): File {
        val out = uniqueFile(outputDir(), baseName, "pdf")
        val pdf = PdfDocument()
        try {
            layout.pages.indices.forEach { i ->
                val info = PdfDocument.PageInfo.Builder(layout.pageWidth.toInt(), layout.pageHeight.toInt(), i + 1).create()
                val page = pdf.startPage(info)
                layout.draw(page.canvas, i)
                pdf.finishPage(page)
                progress(((i + 1) * 90) / layout.pages.size)
            }
            FileOutputStream(out).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        progress(100)
        scan(context, out)
        return out
    }

    /** Word (.docx) or TXT file → PDF. */
    fun wordToPdf(context: Context, file: File, baseName: String, progress: (Int) -> Unit): File {
        progress(5)
        val layout = if (file.extension.equals("txt", true)) DocLayout(TxtParser.parse(file)) else DocLayout(DocxParser.parse(file))
        progress(20)
        return docToPdf(context, layout, baseName) { p -> progress(20 + p * 80 / 100) }
    }

    /** PowerPoint (.pptx) → PDF, one page per slide at the slide size. */
    fun pptToPdf(context: Context, file: File, baseName: String, progress: (Int) -> Unit): File {
        progress(5)
        val deck = PptxParser.parse(file)
        if (deck.slides.isEmpty()) throw IllegalStateException("No slides")
        progress(25)
        return deckToPdf(context, deck, baseName) { p -> progress(25 + p * 75 / 100) }
    }

    fun deckToPdf(context: Context, deck: PptxParser.Deck, baseName: String, progress: (Int) -> Unit): File {
        val out = uniqueFile(outputDir(), baseName, "pdf")
        val pdf = PdfDocument()
        try {
            deck.slides.indices.forEach { i ->
                val info = PdfDocument.PageInfo.Builder(deck.widthPt.toInt(), deck.heightPt.toInt(), i + 1).create()
                val page = pdf.startPage(info)
                PptxParser.draw(page.canvas, deck, i)
                pdf.finishPage(page)
                progress(((i + 1) * 90) / deck.slides.size)
            }
            FileOutputStream(out).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        progress(100)
        scan(context, out)
        return out
    }

    /**
     * PDF → Word that keeps every page looking exactly like the PDF: each page is rendered
     * and placed as a full-page picture on its own page of the .docx, at the PDF's page size
     * with zero margins. (Only the file format changes, like the original app.)
     */
    fun pdfToWord(context: Context, pdf: File, baseName: String, progress: (Int) -> Unit): File {
        val out = uniqueFile(outputDir(), baseName, "docx")
        val tmp = File(out.parentFile, ".${out.name}.part")
        val pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            PdfRenderer(pfd).use { renderer ->
                val n = renderer.pageCount
                if (n == 0) throw IllegalStateException("Empty PDF")
                // section size = first page (points → twips)
                val (pw, ph) = renderer.openPage(0).use { it.width.toFloat() to it.height.toFloat() }
                val body = StringBuilder()
                val rels = StringBuilder()
                ZipOutputStream(FileOutputStream(tmp)).use { zip ->
                    for (i in 0 until n) {
                        var pageW = pw
                        var pageH = ph
                        val bytes = renderer.openPage(i).use { page ->
                            pageW = page.width.toFloat()
                            pageH = page.height.toFloat()
                            val scale = RENDER_SCALE.coerceAtMost(MAX_RENDER_PX / maxOf(page.width, page.height).toFloat())
                            val bw = (page.width * scale).toInt().coerceAtLeast(1)
                            val bh = (page.height * scale).toInt().coerceAtLeast(1)
                            val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                            val bos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 88, bos)
                            bmp.recycle()
                            bos.toByteArray()
                        }
                        zip.putNextEntry(ZipEntry("word/media/page${i + 1}.jpeg"))
                        zip.write(bytes)
                        zip.closeEntry()
                        val rid = "rIdImg${i + 1}"
                        rels.append("<Relationship Id=\"$rid\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/page${i + 1}.jpeg\"/>")
                        // pages of another size are fitted (not stretched) on the document's page
                        val fit = minOf(pw / pageW, ph / pageH)
                        val dw = pageW * fit
                        val dh = pageH * fit
                        body.append(pagePicture(
                            i, rid, (dw * EMU_PER_PT).toLong(), (dh * EMU_PER_PT).toLong(),
                            ((pw - dw) / 2 * EMU_PER_PT).toLong(), ((ph - dh) / 2 * EMU_PER_PT).toLong()
                        ))
                        progress(((i + 1) * 90) / n)
                    }
                    fun put(name: String, content: String) {
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(content.toByteArray(Charsets.UTF_8))
                        zip.closeEntry()
                    }
                    put("[Content_Types].xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="jpeg" ContentType="image/jpeg"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
                    put("_rels/.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
                    put("word/_rels/document.xml.rels", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$rels</Relationships>""")
                    val tw = (pw * 20).toInt()
                    val th = (ph * 20).toInt()
                    val orient = if (pw > ph) " w:orient=\"landscape\"" else ""
                    put("word/document.xml", """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"><w:body>$body<w:sectPr><w:pgSz w:w="$tw" w:h="$th"$orient/><w:pgMar w:top="0" w:right="0" w:bottom="0" w:left="0" w:header="0" w:footer="0" w:gutter="0"/></w:sectPr></w:body></w:document>""")
                }
            }
        } catch (e: Throwable) {
            tmp.delete()
            throw e
        } finally {
            try { pfd.close() } catch (ignored: Exception) {}
        }
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
        progress(100)
        scan(context, out)
        return out
    }

    /** One paragraph holding a picture anchored at the page's top-left, behind text, no wrapping. */
    private fun pagePicture(index: Int, rid: String, cx: Long, cy: Long, offX: Long, offY: Long): String {
        val id = index + 1
        val breakBefore = if (index > 0) "<w:pPr><w:pageBreakBefore/><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr>" else "<w:pPr><w:spacing w:before=\"0\" w:after=\"0\"/></w:pPr>"
        return "<w:p>$breakBefore<w:r><w:drawing>" +
            "<wp:anchor distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\" simplePos=\"0\" relativeHeight=\"$id\" behindDoc=\"1\" locked=\"1\" layoutInCell=\"1\" allowOverlap=\"1\">" +
            "<wp:simplePos x=\"0\" y=\"0\"/>" +
            "<wp:positionH relativeFrom=\"page\"><wp:posOffset>$offX</wp:posOffset></wp:positionH>" +
            "<wp:positionV relativeFrom=\"page\"><wp:posOffset>$offY</wp:posOffset></wp:positionV>" +
            "<wp:extent cx=\"$cx\" cy=\"$cy\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/><wp:wrapNone/>" +
            "<wp:docPr id=\"$id\" name=\"Page $id\"/><wp:cNvGraphicFramePr><a:graphicFrameLocks noChangeAspect=\"1\"/></wp:cNvGraphicFramePr>" +
            "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><pic:pic>" +
            "<pic:nvPicPr><pic:cNvPr id=\"$id\" name=\"page$id.jpeg\"/><pic:cNvPicPr/></pic:nvPicPr>" +
            "<pic:blipFill><a:blip r:embed=\"$rid\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
            "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"$cx\" cy=\"$cy\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
            "</pic:pic></a:graphicData></a:graphic></wp:anchor></w:drawing></w:r></w:p>"
    }

    fun scan(context: Context, file: File) {
        try {
            MediaScannerConnection.scanFile(context.applicationContext, arrayOf(file.absolutePath), null, null)
        } catch (ignored: Exception) {
        }
    }
}
