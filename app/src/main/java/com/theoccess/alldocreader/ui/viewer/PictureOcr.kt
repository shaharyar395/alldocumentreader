package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Text inside pictures, for search: Word files whose pages are pictures (made from PDFs / scans)
 * and scanned PDFs have no real text, so "Search in Document" found nothing. Google ML Kit
 * reads the words on the phone (no upload); search then finds and highlights them like normal text.
 */
object PictureOcr {

    /** One recognised line: its text and, for every word, the character range and box (page points). */
    class Line(val text: String, val words: List<Pair<IntRange, RectF>>)

    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /**
     * Lines found in [bmp], mapped into page points: a picture drawn at [dest] (page points).
     * Background thread only. Throws when the recogniser is not available yet.
     */
    fun read(bmp: Bitmap, dest: RectF): List<Line> {
        if (bmp.width < 40 || bmp.height < 40) return emptyList()
        val result = Tasks.await(client.process(InputImage.fromBitmap(bmp, 0)), 30, TimeUnit.SECONDS)
        val sx = dest.width() / bmp.width
        val sy = dest.height() / bmp.height
        val out = ArrayList<Line>()
        for (block in result.textBlocks) for (line in block.lines) {
            val sb = StringBuilder()
            val words = ArrayList<Pair<IntRange, RectF>>()
            for (e in line.elements) {
                val b = e.boundingBox ?: continue
                if (sb.isNotEmpty()) sb.append(' ')
                val start = sb.length
                sb.append(e.text)
                words += (start until sb.length) to RectF(
                    dest.left + b.left * sx, dest.top + b.top * sy,
                    dest.left + b.right * sx, dest.top + b.bottom * sy
                )
            }
            if (words.isNotEmpty()) out += Line(sb.toString(), words)
        }
        return out
    }

    /** Boxes (one per line) of every occurrence of [query] in [lines]: exactly the matching letters. */
    fun matches(lines: List<Line>, query: String): List<List<RectF>> {
        val out = ArrayList<List<RectF>>()
        for (line in lines) {
            for (at in SearchText.find(line.text, query)) {
                val end = at + SearchText.matchLength(line.text, at, query)
                var box: RectF? = null
                for ((range, r) in line.words) {
                    val s = maxOf(at, range.first)
                    val e = minOf(end, range.last + 1)
                    if (s >= e) continue
                    // the matching part of this word, by its share of the word's letters
                    val len = (range.last + 1 - range.first).coerceAtLeast(1)
                    val x1 = r.left + r.width() * (s - range.first) / len
                    val x2 = r.left + r.width() * (e - range.first) / len
                    val part = RectF(x1, r.top, x2, r.bottom)
                    if (box == null) box = part else box.union(part)
                }
                box?.let { out += listOf(it) }
            }
        }
        return out
    }

    fun text(lines: List<Line>): String = lines.joinToString("\n") { it.text }
}
