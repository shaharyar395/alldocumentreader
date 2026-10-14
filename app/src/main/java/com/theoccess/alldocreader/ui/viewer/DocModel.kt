package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.text.Layout
import android.util.LruCache
import java.io.File
import java.util.zip.ZipFile

/** Page size and margins in points (from the Word section properties). */
data class PageGeometry(
    val width: Float = 595f,
    val height: Float = 842f,
    val left: Float = 54f,
    val top: Float = 54f,
    val right: Float = 54f,
    val bottom: Float = 54f
) {
    companion object {
        val DEFAULT = PageGeometry()
    }
}

/** A picture positioned on the page instead of in the text flow (Word "anchor"). */
data class Anchor(
    val x: Float,
    val y: Float,
    /** page | margin | column | character */
    val relH: String,
    /** page | margin | paragraph | line */
    val relV: String,
    /** left | center | right (when Word uses alignment instead of an offset) */
    val alignH: String? = null,
    val alignV: String? = null,
    /** true = text flows over/under it (behind / in front, no wrapping) */
    val noWrap: Boolean = true,
    val behind: Boolean = false
)

/** Content blocks extracted from a Word or TXT file, laid out by [DocLayout]. */
sealed class DocBlock {
    class Para(
        val text: CharSequence,
        val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        val spaceBefore: Float = 0f,
        val spaceAfter: Float = 6f,
        val indent: Float = 0f,
        val lineMult: Float = 1.0f,
        val shading: Int? = null
    ) : DocBlock()

    class Image(
        val bitmap: LazyBitmap,
        val widthPt: Float,
        val heightPt: Float,
        val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        val anchor: Anchor? = null
    ) : DocBlock()

    /** [colWidths] are fractions of the content width (sum ≈ 1). */
    class Table(val rows: List<List<CharSequence>>, val colWidths: List<Float>? = null) : DocBlock()

    object PageBreak : DocBlock()
}

class ParsedDoc(val blocks: List<DocBlock>, val geometry: PageGeometry)

/**
 * A picture that is only decoded when its page is drawn (and dropped again from memory when
 * other pages need the space). Documents with many pictures therefore open without decoding
 * every picture first — this is what made long Word / PowerPoint files slow to open.
 */
class LazyBitmap private constructor(private val key: String?, private val strong: Bitmap?, private val load: (() -> Bitmap?)?) {

    @Volatile private var failed = false

    fun get(): Bitmap? {
        strong?.let { return it }
        val k = key ?: return null
        cache.get(k)?.let { return it }
        if (failed) return null
        val b = try { load?.invoke() } catch (t: Throwable) { null }
        if (b == null) failed = true else cache.put(k, b)
        return b
    }

    companion object {
        private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
            override fun sizeOf(key: String, value: Bitmap) = value.byteCount
        }

        fun of(bitmap: Bitmap) = LazyBitmap(null, bitmap, null)

        /** A picture inside a .docx / .pptx (zip) file, decoded at most [maxPx] on its longer side. */
        fun zipEntry(file: File, entry: String, maxPx: Int) =
            LazyBitmap("${file.absolutePath}|${file.lastModified()}|$entry|$maxPx", null) {
                ZipFile(file).use { zip ->
                    val e = zip.getEntry(entry) ?: return@use null
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    zip.getInputStream(e).use { BitmapFactory.decodeStream(it, null, o) }
                    if (o.outWidth <= 0 || o.outHeight <= 0) return@use null
                    // Halve only while the result stays at least [maxPx]: halving past it (e.g. a
                    // 2480×3508 page scan down to 620×877) made big picture pages blurry. The last
                    // step to [maxPx] is a smooth resize instead.
                    // Decoding with inSampleSize skips pixels, which breaks up thin letter strokes
                    // in page scans; so only skip while at least twice [maxPx] remains (and the
                    // picture fits in memory), then shrink by smooth halving and one last resize.
                    val longer = maxOf(o.outWidth, o.outHeight)
                    var sample = 1
                    while (longer / (sample * 2) >= maxPx * 2) sample *= 2
                    val budget = Runtime.getRuntime().maxMemory() / 8
                    while ((o.outWidth / sample).toLong() * (o.outHeight / sample) * 4 > budget && sample < 64) sample *= 2
                    var cur = zip.getInputStream(e).use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@use null
                    while (maxOf(cur.width, cur.height) >= maxPx * 2) {
                        val half = Bitmap.createScaledBitmap(cur, (cur.width / 2).coerceAtLeast(1), (cur.height / 2).coerceAtLeast(1), true)
                        if (half !== cur) cur.recycle()
                        cur = half
                    }
                    val curLonger = maxOf(cur.width, cur.height)
                    if (curLonger <= maxPx) return@use cur
                    val f = maxPx.toFloat() / curLonger
                    val scaled = Bitmap.createScaledBitmap(
                        cur, (cur.width * f).toInt().coerceAtLeast(1), (cur.height * f).toInt().coerceAtLeast(1), true
                    )
                    if (scaled !== cur) cur.recycle()
                    scaled
                }
            }

        /** Pixel size of a picture inside an open zip without decoding it (null when it isn't a picture). */
        fun bounds(zip: ZipFile, entry: String): Pair<Int, Int>? = try {
            val e = zip.getEntry(entry)
            if (e == null) null else {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                zip.getInputStream(e).use { BitmapFactory.decodeStream(it, null, o) }
                if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
