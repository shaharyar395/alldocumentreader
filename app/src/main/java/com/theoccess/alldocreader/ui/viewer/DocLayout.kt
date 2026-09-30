package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Lays [DocBlock]s out on pages of the document's own size and margins (units = PDF points)
 * so the same pages are drawn on screen and written to a PDF — the converted PDF therefore
 * looks exactly like the Word file in the viewer.
 */
class DocLayout(blocks: List<DocBlock>, val geometry: PageGeometry = PageGeometry.DEFAULT) {

    constructor(doc: ParsedDoc) : this(doc.blocks, doc.geometry)

    sealed class Item {
        class Text(val layout: StaticLayout, val startLine: Int, val endLine: Int, val x: Float, val y: Float, val shading: Int?) : Item()
        class Picture(val bitmap: Bitmap, val rect: RectF, val behind: Boolean = false) : Item()
        class Row(val cells: List<StaticLayout>, val x: Float, val y: Float, val colWidths: List<Float>, val height: Float) : Item()
    }

    class Page {
        val items = ArrayList<Item>()
        val text = StringBuilder()
    }

    val pages = ArrayList<Page>()
    val pageWidth: Float get() = geometry.width
    val pageHeight: Float get() = geometry.height

    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
        color = Color.BLACK
        textSize = 11f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = 0xFF000000.toInt()
    }
    private val fillPaint = Paint()
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private val left = geometry.left.coerceIn(0f, geometry.width / 3)
    private val top = geometry.top.coerceIn(0f, geometry.height / 3)
    private val bottom = geometry.height - geometry.bottom.coerceIn(0f, geometry.height / 3)
    private val contentW = (geometry.width - left - geometry.right.coerceIn(0f, geometry.width / 3)).coerceAtLeast(72f)
    private var page = Page()
    private var y = top

    init {
        pages += page
        for (b in blocks) when (b) {
            is DocBlock.Para -> addPara(b)
            is DocBlock.Image -> if (b.anchor != null) addAnchored(b, b.anchor) else addImage(b)
            is DocBlock.Table -> addTable(b)
            DocBlock.PageBreak -> if (page.items.isNotEmpty()) newPage()
        }
        if (pages.size > 1 && pages.last().items.isEmpty()) pages.removeAt(pages.lastIndex)
    }

    private fun newPage() {
        page = Page()
        pages += page
        y = top
    }

    private fun staticLayout(text: CharSequence, width: Int, align: Layout.Alignment, mult: Float = 1.08f): StaticLayout =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
                .setAlignment(align)
                .setLineSpacing(0f, mult)
                .setIncludePad(false)
                .build()
        } else {
            @Suppress("DEPRECATION")
            StaticLayout(text, paint, width.coerceAtLeast(1), align, mult, 0f, false)
        }

    private fun addPara(b: DocBlock.Para) {
        if (y > top) y += b.spaceBefore
        val layout = staticLayout(b.text, (contentW - b.indent).toInt(), b.align, b.lineMult)
        var start = 0
        while (start < layout.lineCount) {
            val avail = bottom - y
            var end = start
            while (end < layout.lineCount && layout.getLineBottom(end) - layout.getLineTop(start) <= avail) end++
            if (end == start) {
                if (y <= top) end = start + 1 // a single line taller than a page: place it anyway
                else { newPage(); continue }
            }
            page.items += Item.Text(layout, start, end, left + b.indent, y, b.shading)
            page.text.append(layout.text.subSequence(layout.getLineStart(start), layout.getLineEnd(end - 1))).append('\n')
            y += (layout.getLineBottom(end - 1) - layout.getLineTop(start)).toFloat()
            start = end
            if (start < layout.lineCount) newPage()
        }
        y += b.spaceAfter
    }

    private fun addImage(b: DocBlock.Image) {
        var w = b.widthPt
        var h = b.heightPt
        if (w > contentW) { h *= contentW / w; w = contentW }
        val maxH = bottom - top
        if (h > maxH) { w *= maxH / h; h = maxH }
        if (y + h > bottom && y > top) newPage()
        val x = when (b.align) {
            Layout.Alignment.ALIGN_CENTER -> left + (contentW - w) / 2
            Layout.Alignment.ALIGN_OPPOSITE -> left + contentW - w
            else -> left
        }
        page.items += Item.Picture(b.bitmap, RectF(x, y, x + w, y + h))
        y += h + 2f
    }

    /** Pictures Word positions on the page (e.g. full-page scans, logos, backgrounds). */
    private fun addAnchored(b: DocBlock.Image, a: Anchor) {
        val w = b.widthPt
        val h = b.heightPt
        val (areaX, areaW) = when (a.relH) {
            "page" -> 0f to geometry.width
            else -> left to contentW
        }
        val (areaY, areaH) = when (a.relV) {
            "page" -> 0f to geometry.height
            "margin", "topMargin" -> top to (bottom - top)
            else -> y to (bottom - y) // the paragraph that holds the picture starts here
        }
        val x = when (a.alignH) {
            "center" -> areaX + (areaW - w) / 2
            "right", "outside" -> areaX + areaW - w
            "left", "inside" -> areaX
            else -> areaX + a.x
        }
        val yy = when (a.alignV) {
            "center" -> areaY + (areaH - h) / 2
            "bottom", "outside" -> areaY + areaH - h
            "top", "inside" -> areaY
            else -> areaY + a.y
        }
        page.items += Item.Picture(b.bitmap, RectF(x, yy, x + w, yy + h), a.behind)
        if (!a.noWrap && a.relV != "page") {
            // square / top-and-bottom wrapping: keep following text below the picture
            y = maxOf(y, (yy + h + 2f).coerceAtMost(bottom))
        }
    }

    private fun addTable(b: DocBlock.Table) {
        val cols = b.rows.maxOf { it.size }.coerceAtLeast(1)
        val fr = b.colWidths?.takeIf { it.size == cols } ?: List(cols) { 1f / cols }
        val widths = fr.map { it * contentW }
        if (y > top) y += 2f
        for (r in b.rows) {
            val cells = (0 until cols).map { i ->
                staticLayout(r.getOrNull(i) ?: "", (widths[i] - 2 * CELL_PAD).toInt(), Layout.Alignment.ALIGN_NORMAL)
            }
            val h = (cells.maxOf { it.height } + 2 * CELL_PAD).coerceAtMost(bottom - top)
            if (y + h > bottom && y > top) newPage()
            page.items += Item.Row(cells, left, y, widths, h)
            r.forEach { page.text.append(it).append('\t') }
            page.text.append('\n')
            y += h
        }
        y += 6f
    }

    /** Draws page [index] on a canvas whose units are points (caller scales). */
    @Synchronized // viewer render thread and PDF conversion (IO) share the same StaticLayouts/paints
    fun draw(canvas: Canvas, index: Int) {
        canvas.drawColor(Color.WHITE)
        val items = pages[index].items
        // pictures placed behind the text first
        for (item in items) if (item is Item.Picture && item.behind) canvas.drawBitmap(item.bitmap, null, item.rect, imagePaint)
        for (item in items) when (item) {
            is Item.Text -> {
                val l = item.layout
                val top = l.getLineTop(item.startLine).toFloat()
                val h = l.getLineBottom(item.endLine - 1) - top
                item.shading?.let {
                    fillPaint.color = it
                    canvas.drawRect(item.x, item.y, item.x + l.width, item.y + h, fillPaint)
                }
                canvas.save()
                canvas.clipRect(item.x - 2, item.y, item.x + l.width + 2, item.y + h)
                canvas.translate(item.x, item.y - top)
                l.draw(canvas)
                canvas.restore()
            }
            is Item.Picture -> if (!item.behind) canvas.drawBitmap(item.bitmap, null, item.rect, imagePaint)
            is Item.Row -> {
                var cx = item.x
                item.cells.forEachIndexed { i, cell ->
                    val cw = item.colWidths[i]
                    canvas.drawRect(cx, item.y, cx + cw, item.y + item.height, borderPaint)
                    canvas.save()
                    canvas.clipRect(cx, item.y, cx + cw, item.y + item.height)
                    canvas.translate(cx + CELL_PAD, item.y + CELL_PAD)
                    cell.draw(canvas)
                    canvas.restore()
                    cx += cw
                }
            }
        }
    }

    companion object {
        private const val CELL_PAD = 4f
    }
}

/** Word / TXT pages for the viewer. */
class DocPageSource(private val doc: DocLayout) : PageSource {
    override val pageCount: Int get() = doc.pages.size
    override fun pageWidth(index: Int) = doc.pageWidth
    override fun pageHeight(index: Int) = doc.pageHeight

    override fun render(index: Int, targetWidth: Int): Bitmap {
        val w = targetWidth.coerceAtLeast(1)
        val scale = w / doc.pageWidth
        val bmp = Bitmap.createBitmap(w, (doc.pageHeight * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        doc.draw(canvas, index)
        return bmp
    }

    override fun pageText(index: Int): String = doc.pages[index].text.toString()

    val layout: DocLayout get() = doc

    override fun close() = Unit
}
