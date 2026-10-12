package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.RectF
import java.io.Closeable

/** Something the viewer can show page by page (a PDF, or a laid-out Word/TXT/Excel document). */
interface PageSource : Closeable {
    val pageCount: Int

    /** Page size in PDF points (1/72 inch). */
    fun pageWidth(index: Int): Float
    fun pageHeight(index: Int): Float

    /** Renders a page to a bitmap [targetWidth] pixels wide. Called from a single background thread. */
    fun render(index: Int, targetWidth: Int): Bitmap

    /** Plain text of a page, used by search. Called from a background thread. */
    fun pageText(index: Int): String

    /**
     * Where [query] appears on page [index]: one list per occurrence, each holding the boxes
     * (one per line it covers) in page points, origin top-left. Empty when positions are unknown.
     */
    fun findMatches(index: Int, query: String): List<List<RectF>> = emptyList()

    /** Search was opened: get ready (e.g. read text inside pictures). Background thread. */
    fun prepareSearch() {}

    /** False while pages are still being prepared in the background (see [finishLoading]). */
    val isComplete: Boolean get() = true

    /** Told (from a background thread) when more pages are ready or page sizes became known. */
    interface LoadListener {
        fun onPagesAdded(count: Int) {}
        fun onPagesResized(pages: List<Int>) {}
    }

    /**
     * The document is shown as soon as its first pages are ready; this prepares the rest.
     * Runs on a background thread and returns when everything is ready.
     */
    fun finishLoading(listener: LoadListener) {}
}

/** Text matching for search: case-insensitive, any space / tab / no-break space counts as a space, curly quotes as straight ones. */
object SearchText {
    /** Same length as [s], so indices stay valid. */
    fun normalize(s: CharSequence): String {
        val out = CharArray(s.length)
        for (i in s.indices) {
            val c = s[i]
            out[i] = when {
                c == '‘' || c == '’' || c == '‛' || c == '′' -> '\''
                c == '“' || c == '”' || c == '″' -> '"'
                c == '–' || c == '—' -> '-'
                Character.isWhitespace(c) || c == ' ' || c == ' ' || c == ' ' -> ' '
                else -> c.lowercaseChar()
            }
        }
        return String(out)
    }

    /** Start indices of every occurrence of [query] in [text] (both normalised here). */
    fun find(text: CharSequence, query: String): List<Int> {
        val t = normalize(text)
        val q = normalize(query.trim()).replace(Regex(" +"), " ")
        if (q.isEmpty()) return emptyList()
        val tt = t // spaces may be repeated in the page text: allow one or more
        if (!q.contains(' ')) {
            val out = ArrayList<Int>()
            var from = 0
            while (true) {
                val at = tt.indexOf(q, from)
                if (at < 0) break
                out += at
                from = at + q.length
            }
            return out
        }
        val rx = Regex(q.split(' ').joinToString(" +") { Regex.escape(it) })
        return rx.findAll(tt).map { it.range.first }.toList()
    }

    /** Length of the occurrence starting at [start] (spaces in the text may be longer than in the query). */
    fun matchLength(text: CharSequence, start: Int, query: String): Int {
        val t = normalize(text)
        val q = normalize(query.trim()).replace(Regex(" +"), " ")
        if (!q.contains(' ')) return q.length
        val rx = Regex(q.split(' ').joinToString(" +") { Regex.escape(it) })
        return rx.find(t, start)?.takeIf { it.range.first == start }?.value?.length ?: q.length
    }
}
