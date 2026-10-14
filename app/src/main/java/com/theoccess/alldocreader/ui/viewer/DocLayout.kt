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
class DocLayout(
    private val blocks: List<DocBlock>,
    val geometry: PageGeometry = PageGeometry.DEFAULT,
    eager: Boolean = true
) {

    constructor(doc: ParsedDoc) : this(doc.blocks, doc.geometry)

    /**
     * Progressive layout for the viewer: only the first pages are laid out before the document
     * is shown; [layoutMore] does the rest in the background while the user is already reading.
     */
    constructor(doc: ParsedDoc, eager: Boolean) : this(doc.blocks, doc.geometry, eager)

    sealed class Item {
        class Text(val layout: StaticLayout, val startLine: Int, val endLine: Int, val x: Float, val y: Float, val shading: Int?) : Item()
        class Picture(val bitmap: LazyBitmap, val rect: RectF, val behind: Boolean = false) : Item()
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

    private var next = 0

    /** True once every block has been placed. */
    @Volatile var complete = false
        private set

    init {
        pages += page
        if (eager) layoutAll()
    }

    /** Pages that are finished (the page still being filled is not counted until the end). */
    val readyPages: Int
        @Synchronized get() = if (complete) pages.size else (pages.size - 1).coerceAtLeast(0)

    /** Places up to [maxBlocks] more blocks; false when the whole document is laid out. */
    @Synchronized
    fun layoutMore(maxBlocks: Int = 40): Boolean {
        if (complete) return false
        val end = if (maxBlocks >= blocks.size - next) blocks.size else next + maxBlocks
        while (next < end) {
            when (val b = blocks[next]) {
                is DocBlock.Para -> addPara(b)
                is DocBlock.Image -> if (b.anchor != null) addAnchored(b, b.anchor) else addImage(b)
                is DocBlock.Table -> addTable(b)
                DocBlock.PageBreak -> if (page.items.isNotEmpty()) newPage()
            }
            next++
        }
        if (next >= blocks.size) {
            if (pages.size > 1 && pages.last().items.isEmpty()) pages.removeAt(pages.lastIndex)
            complete = true
        }
        return !complete
    }

    /** Lays out until at least [n] pages are ready (or the document ends). */
    fun layoutUntil(n: Int) {
        while (readyPages < n && layoutMore(20)) Unit
    }

    fun layoutAll() {
        while (layoutMore(Int.MAX_VALUE)) Unit
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

    /** Pictures on page [index] big enough to hold text (for reading text inside pictures). */
    @Synchronized
    fun pictures(index: Int): List<Item.Picture> =
        pages[index].items.filterIsInstance<Item.Picture>().filter { it.rect.width() > 60f && it.rect.height() > 40f }

    /** Search: every occurrence of [query] on page [index] as line boxes in page points. */
    @Synchronized
    fun matches(index: Int, query: String): List<List<RectF>> {
        val out = ArrayList<List<RectF>>()
        for (item in pages[index].items) when (item) {
            is Item.Text -> {
                val l = item.layout
                val segStart = l.getLineStart(item.startLine)
                val segEnd = l.getLineEnd(item.endLine - 1)
                val seg = l.text.subSequence(segStart, segEnd)
                val dy = item.y - l.getLineTop(item.startLine)
                for (at in SearchText.find(seg, query)) {
                    val len = SearchText.matchLength(seg, at, query)
                    out += boxes(l, segStart + at, segStart + at + len, item.startLine, item.endLine, item.x, dy)
                }
            }
            is Item.Row -> {
                var cx = item.x
                item.cells.forEachIndexed { i, cell ->
                    for (at in SearchText.find(cell.text, query)) {
                        val len = SearchText.matchLength(cell.text, at, query)
                        val r = boxes(cell, at, at + len, 0, cell.lineCount, cx + CELL_PAD, item.y + CELL_PAD)
                            .filter { it.top < item.y + item.height }
                        if (r.isNotEmpty()) out += r
                    }
                    cx += item.colWidths[i]
                }
            }
            else -> Unit
        }
        return out
    }

    private fun boxes(l: StaticLayout, a: Int, b: Int, firstLine: Int, endLine: Int, ox: Float, oy: Float): List<RectF> {
        val r = ArrayList<RectF>()
        for (line in firstLine until endLine) {
            val ls = maxOf(a, l.getLineStart(line))
            val le = minOf(b, l.getLineEnd(line))
            if (ls >= le) continue
            val x1 = l.getPrimaryHorizontal(ls)
            val x2 = if (le >= l.getLineEnd(line)) l.getLineRight(line) else l.getPrimaryHorizontal(le)
            // around the letters only: line spacing (added below each line) is left out
            val base = l.getLineBaseline(line).toFloat()
            val ascent = -l.getLineAscent(line).toFloat()
            val descent = minOf(l.getLineDescent(line).toFloat(), ascent * 0.3f)
            r += RectF(ox + minOf(x1, x2), oy + base - ascent, ox + maxOf(x1, x2), oy + base + descent)
        }
        return r
    }

    /** Draws page [index] on a canvas whose units are points (caller scales). */
    @Synchronized // viewer render thread and PDF conversion (IO) share the same StaticLayouts/paints
    fun draw(canvas: Canvas, index: Int) {
        canvas.drawColor(Color.WHITE)
        val items = pages[index].items
        // pictures placed behind the text first
        for (item in items) if (item is Item.Picture && item.behind) item.bitmap.get()?.let { canvas.drawBitmap(it, null, item.rect, imagePaint) }
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
            is Item.Picture -> if (!item.behind) item.bitmap.get()?.let { canvas.drawBitmap(it, null, item.rect, imagePaint) }
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
    override val pageCount: Int get() = doc.readyPages
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

    override fun pageText(index: Int): String {
        val text = synchronized(doc) { doc.pages[index].text.toString() }
        val ocr = ocrPage(index)
        return if (ocr.isEmpty()) text else text + "\n" + PictureOcr.text(ocr)
    }

    // ------------------------------------------------------------------ text inside pictures

    private val ocr = HashMap<Int, List<PictureOcr.Line>>()
    /** One lock per page, so the background reading and a search never read the same page twice. */
    private val ocrLocks = java.util.concurrent.ConcurrentHashMap<Int, Any>()

    /** Words read from the pictures on page [index] (cached; empty when it has none). */
    private fun ocrPage(index: Int): List<PictureOcr.Line> {
        synchronized(ocr) { ocr[index]?.let { return it } }
        synchronized(ocrLocks.getOrPut(index) { Any() }) {
            synchronized(ocr) { ocr[index]?.let { return it } }
            return readPage(index)
        }
    }

    private fun readPage(index: Int): List<PictureOcr.Line> {
        val pics = doc.pictures(index)
        if (pics.isEmpty()) {
            synchronized(ocr) { ocr[index] = emptyList() }
            return emptyList()
        }
        val lines = ArrayList<PictureOcr.Line>()
        try {
            for (p in pics) {
                val bmp = p.bitmap.get() ?: continue
                lines += PictureOcr.read(bmp, p.rect)
            }
        } catch (e: Exception) {
            return lines   // recogniser not ready (first use): not cached, tried again next search
        }
        synchronized(ocr) { ocr[index] = lines }
        return lines
    }

    /**
     * Started when the document opens: reads the pictures of every page in the background so a
     * search finds their words at once. Follows the layout of a long document as it grows (the
     * old loop stopped at the pages that were ready when it started).
     */
    override fun prepareSearch() {
        var i = 0
        while (!closed) {
            if (i < pageCount) {
                ocrPage(i++)
            } else if (doc.complete) {
                return
            } else {
                Thread.sleep(100)
            }
        }
    }

    override val isComplete: Boolean get() = doc.complete

    override fun finishLoading(listener: PageSource.LoadListener) {
        var shown = doc.readyPages
        while (!closed && doc.layoutMore(30)) {
            val n = doc.readyPages
            if (n != shown) { shown = n; listener.onPagesAdded(n) }
        }
        if (doc.readyPages != shown) listener.onPagesAdded(doc.readyPages)
    }

    /** Real text, plus the words inside pictures (Word files made of page pictures). */
    override fun findMatches(index: Int, query: String): List<List<RectF>> {
        val inText = doc.matches(index, query)
        val lines = ocrPage(index)
        return if (lines.isEmpty()) inText else inText + PictureOcr.matches(lines, query)
    }

    @Volatile private var closed = false

    /** The full layout (finishes it first if the viewer is still laying out pages). */
    val layout: DocLayout get() = doc.also { it.layoutAll() }

    override fun close() { closed = true }
}
