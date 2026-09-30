package com.theoccess.alldocreader.ui.pages

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.util.Matrix
import java.io.File

/** Writes the organizer's page list to a new PDF with PDFBox (original pages stay vector). */
object PdfEditor {

    private const val MAX_IMAGE_PX = 2200

    /** Builds [out] from [items]. Call from a background thread. */
    fun write(context: Context, src: File, items: List<PageItem>, out: File, progress: (Int) -> Unit = {}) {
        PDDocument.load(src).use { source ->
            source.isAllSecurityToBeRemoved = true
            PDDocument().use { dst ->
                items.forEachIndexed { i, item ->
                    val page = when (val k = item.kind) {
                        is PageKind.Original -> dst.importPage(source.getPage(k.index))
                            .also { applySetup(dst, it, item.size, item.bg) }
                        is PageKind.Blank -> {
                            val (w, h) = item.size?.let { sz ->
                                val short = minOf(sz.first, sz.second)
                                val long = maxOf(sz.first, sz.second)
                                if (k.spec.widthPt > k.spec.heightPt) long to short else short to long
                            } ?: (k.spec.widthPt to k.spec.heightPt)
                            blankPage(dst, k.spec.copy(widthPt = w, heightPt = h, color = item.bg ?: k.spec.color))
                        }
                        is PageKind.Picture -> imagePage(context, dst, Uri.parse(k.uri))
                            .also { applySetup(dst, it, item.size, item.bg) }
                    }
                    page.rotation = (((page.rotation + item.rotation) % 360) + 360) % 360
                    progress(((i + 1) * 90) / items.size)
                }
                dst.save(out)
            }
        }
        progress(100)
    }

    /**
     * Page setup: new page size (content scaled to fit and centred) and/or a background colour
     * painted under the page content.
     */
    private fun applySetup(doc: PDDocument, page: PDPage, size: Pair<Float, Float>?, bg: Int?) {
        if (size == null && bg == null) return
        val box = page.mediaBox
        val ow = box.width
        val oh = box.height
        var nw = ow
        var nh = oh
        if (size != null) {
            // keep the page's orientation: a landscape page stays landscape in the new size
            val rotated = page.rotation % 180 != 0
            val landscape = (ow > oh) != rotated
            val short = minOf(size.first, size.second)
            val long = maxOf(size.first, size.second)
            val dw = if (landscape) long else short
            val dh = if (landscape) short else long
            nw = if (rotated) dh else dw
            nh = if (rotated) dw else dh
        }
        val newBox = if (size != null) PDRectangle(nw, nh) else box
        val s = if (size != null) minOf(nw / ow, nh / oh) else 1f
        val tx = if (size != null) (nw - ow * s) / 2f - box.lowerLeftX * s else 0f
        val ty = if (size != null) (nh - oh * s) / 2f - box.lowerLeftY * s else 0f
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.PREPEND, true, false).use { cs ->
            cs.saveGraphicsState()
            if (bg != null) {
                cs.setNonStrokingColor(Color.red(bg) / 255f, Color.green(bg) / 255f, Color.blue(bg) / 255f)
                cs.addRect(newBox.lowerLeftX, newBox.lowerLeftY, newBox.width, newBox.height)
                cs.fill()
            }
            if (size != null) cs.transform(Matrix(s, 0f, 0f, s, tx, ty))
        }
        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, false).use { it.restoreGraphicsState() }
        if (size != null) {
            page.mediaBox = newBox
            page.cropBox = newBox
        }
    }

    /** New PDF made of [count] blank pages (Create PDF). */
    fun createBlank(out: File, spec: BlankSpec, count: Int) {
        PDDocument().use { doc ->
            repeat(count.coerceAtLeast(1)) { blankPage(doc, spec) }
            doc.save(out)
        }
    }

    private fun blankPage(doc: PDDocument, spec: BlankSpec): PDPage {
        val w = spec.widthPt
        val h = spec.heightPt
        val page = PDPage(PDRectangle(w, h))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            cs.setNonStrokingColor(Color.red(spec.color) / 255f, Color.green(spec.color) / 255f, Color.blue(spec.color) / 255f)
            cs.addRect(0f, 0f, w, h)
            cs.fill()
            val lc = Templates.lineColor(spec.color)
            Templates.segments(spec.template, w, h).forEach { s ->
                val c = if (s.red) 0xFFE57373.toInt() else lc
                cs.setStrokingColor(Color.red(c) / 255f, Color.green(c) / 255f, Color.blue(c) / 255f)
                cs.setLineWidth(s.width)
                cs.moveTo(s.x1, h - s.y1)
                cs.lineTo(s.x2, h - s.y2)
                cs.stroke()
            }
        }
        return page
    }

    /** A4 page (portrait or landscape to match the picture) with the image fitted inside. */
    private fun imagePage(context: Context, doc: PDDocument, uri: Uri): PDPage {
        val bmp = decode(context, uri) ?: throw IllegalStateException("Cannot read image")
        val landscape = bmp.width > bmp.height
        val pw = if (landscape) PDRectangle.A4.height else PDRectangle.A4.width
        val ph = if (landscape) PDRectangle.A4.width else PDRectangle.A4.height
        val page = PDPage(PDRectangle(pw, ph))
        doc.addPage(page)
        val margin = 18f
        val scale = minOf((pw - 2 * margin) / bmp.width, (ph - 2 * margin) / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        val img = JPEGFactory.createFromImage(doc, bmp)
        PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(img, (pw - w) / 2, (ph - h) / 2, w, h)
        }
        bmp.recycle()
        return page
    }

    fun decode(context: Context, uri: Uri, maxPx: Int = MAX_IMAGE_PX): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) null else upright(context, uri, bmp)
    } catch (e: Exception) {
        null
    }

    /** Turns camera photos the right way up (they often store the turn in EXIF instead of the pixels). */
    private fun upright(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val deg = try {
            context.contentResolver.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (deg == 0f) return bmp
        val m = android.graphics.Matrix().apply { postRotate(deg) }
        val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (out !== bmp) bmp.recycle()
        return out
    }
}
