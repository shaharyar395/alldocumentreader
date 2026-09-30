package com.theoccess.alldocreader.ui.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Picture pipeline for scanned / imported pages: upright decode → perspective crop →
 * rotation → look filter. Everything runs on a background thread.
 */
object ImageOps {

    const val PREVIEW_PX = 1400
    const val FINAL_PX = 2400
    const val THUMB_PX = 360

    // ------------------------------------------------------------------ loading

    /** Decodes [file] no larger than [maxPx] on its long side, turned upright using EXIF. */
    fun decode(file: File, maxPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        var bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val long = max(bmp.width, bmp.height)
        if (long > maxPx) {
            val s = maxPx.toFloat() / long
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true)
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        val deg = try {
            when (ExifInterface(file.absolutePath).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } catch (e: Exception) {
            0
        }
        if (deg != 0) bmp = rotate(bmp, deg)
        if (bmp.hasAlpha()) {
            // pages are JPEG: put transparent pictures on white
            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply { drawColor(Color.WHITE); drawBitmap(bmp, 0f, 0f, null) }
            bmp.recycle()
            bmp = flat
        }
        return bmp
    }

    fun rotate(bmp: Bitmap, deg: Int): Bitmap {
        val d = ((deg % 360) + 360) % 360
        if (d == 0) return bmp
        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(d.toFloat()) }, true)
        if (r !== bmp) bmp.recycle()
        return r
    }

    fun saveJpeg(bmp: Bitmap, file: File, quality: Int = 90) {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    /** The finished page: upright source → crop → rotation → filter. */
    fun render(page: ScanPage, maxPx: Int, filter: ScanFilter = page.filter): Bitmap? {
        var bmp = decode(page.source, maxPx) ?: return null
        page.quad?.let { q -> if (!isFull(q)) bmp = warp(bmp, q) }
        bmp = rotate(bmp, page.rotation)
        return applyFilter(bmp, filter)
    }

    // ------------------------------------------------------------------ crop

    val FULL = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)

    fun isFull(q: FloatArray) = q.indices.all { kotlin.math.abs(q[it] - FULL[it]) < 0.004f }

    /** Straightens the quadrilateral [q] (TL, TR, BR, BL fractions) into a rectangle. */
    fun warp(src: Bitmap, q: FloatArray): Bitmap {
        val w = src.width.toFloat()
        val h = src.height.toFloat()
        val p = FloatArray(8) { i -> q[i] * if (i % 2 == 0) w else h }
        val top = hypot(p[2] - p[0], p[3] - p[1])
        val bottom = hypot(p[4] - p[6], p[5] - p[7])
        val left = hypot(p[6] - p[0], p[7] - p[1])
        val right = hypot(p[4] - p[2], p[5] - p[3])
        val ow = max(top, bottom).roundToInt().coerceIn(8, 6000)
        val oh = max(left, right).roundToInt().coerceIn(8, 6000)
        val dst = floatArrayOf(0f, 0f, ow.toFloat(), 0f, ow.toFloat(), oh.toFloat(), 0f, oh.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(p, 0, dst, 0, 4)) return src
        val out = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, m, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }
        src.recycle()
        return out
    }

    /** Maps crop corners of the upright source into the picture as shown after [rotation]. */
    fun rotateQuad(q: FloatArray, rotation: Int): FloatArray {
        var r = q.copyOf()
        repeat((((rotation % 360) + 360) % 360) / 90) {
            val n = FloatArray(8)
            // 90° clockwise: (x, y) → (1 - y, x); corner order shifts by one
            for (i in 0 until 4) {
                val x = r[i * 2]
                val y = r[i * 2 + 1]
                val j = (i + 1) % 4
                n[j * 2] = 1f - y
                n[j * 2 + 1] = x
            }
            r = n
        }
        return r
    }

    /** Inverse of [rotateQuad]. */
    fun unrotateQuad(q: FloatArray, rotation: Int): FloatArray =
        rotateQuad(q, 360 - (((rotation % 360) + 360) % 360))

    /**
     * Finds a document (sheet of paper, receipt, card…) in the picture and returns its corners
     * TL, TR, BR, BL as fractions, or null if nothing clear stands out.
     *
     * The page is assumed to cover the middle of the picture (where the user aims). On a small
     * copy of the photo, four "paper" measures are built (brightness, whiteness, coolness — paper
     * is less warm than wood / tables — and a mix), each with the text wiped out by a closing
     * filter. For each measure and several tolerances, the region like the middle is grown, its
     * holes filled, and the largest 4-corner shape inside its outline taken. Every candidate is
     * scored by how well its four sides sit on real edges in the photo and how much it stands out
     * from its surroundings; the best one then has each side snapped onto the page's edge.
     */
    fun detectDocument(src: Bitmap): FloatArray? {
        val scale = 360f / max(src.width, src.height)
        val w = max(16, (src.width * scale).roundToInt())
        val h = max(16, (src.height * scale).roundToInt())
        val small = Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()
        val n = w * h
        val lum = IntArray(n); val white = IntArray(n); val cool = IntArray(n); val mix = IntArray(n)
        for (i in 0 until n) {
            val c = px[i]
            val r = Color.red(c); val g = Color.green(c); val b = Color.blue(c)
            val l = (r * 299 + g * 587 + b * 114) / 1000
            val sat = kotlin.math.max(r, kotlin.math.max(g, b)) - kotlin.math.min(r, kotlin.math.min(g, b))
            lum[i] = l
            white[i] = (l - 2 * sat).coerceIn(0, 255)
            cool[i] = (128 + 3 * (b - r)).coerceIn(0, 255)
            mix[i] = (l + 2 * (b - r)).coerceIn(0, 255)
        }
        val feats = listOf(lum, white, cool, mix).map { f ->
            var c = f
            repeat(3) { c = minMax(c, w, h, max = true) }    // closing: removes dark text / lines
            repeat(3) { c = minMax(c, w, h, max = false) }
            boxBlur(boxBlur(c, w, h), w, h)
        }
        val grad = IntArray(n)
        for (f in feats) {
            val g = gradient(f, w, h)
            for (i in 0 until n) if (g[i] > grad[i]) grad[i] = g[i]
        }
        val gThr = grad.copyOf().also { it.sort() }[(n * 0.85f).toInt().coerceIn(0, n - 1)].toFloat()

        val cy0 = (h * 0.4f).toInt(); val cy1 = (h * 0.6f).toInt()
        val cx0 = (w * 0.4f).toInt(); val cx1 = (w * 0.6f).toInt()
        var best: FloatArray? = null
        var bestScore = -1f
        var bestSupport = 0f
        for (f in feats) {
            val centre = IntArray((cy1 - cy0) * (cx1 - cx0))
            var k = 0
            for (y in cy0 until cy1) for (x in cx0 until cx1) centre[k++] = f[y * w + x]
            centre.sort()
            val v = centre[centre.size / 2]
            for (delta in intArrayOf(8, 14, 20, 28, 36, 46, 58, 72)) {
                val t = v - delta
                val region = growFrom(f, w, h, t, v, cx0, cx1, cy0, cy1) ?: continue
                var count = 0
                for (bit in region) if (bit) count++
                if (count < n * 0.08f || count > n * 0.96f) continue
                val filled = fillHoles(region, w, h)
                var filledCount = 0
                for (bit in filled) if (bit) filledCount++
                val q = maxQuad(hullOf(filled, w, h)) ?: continue
                val area = polyArea(q) / n
                if (area < 0.08f || area > 0.97f) continue
                if (filledCount / n.toFloat() / area < 0.88f || !goodShape(q)) continue
                val border = FloatArray(1)
                val support = sideSupport(q, grad, w, h, gThr, border)
                val contrast = contrast(f, filled, w, h)
                val score = support + kotlin.math.min(contrast, 40f) / 80f + 0.2f * area - 0.15f * border[0]
                if (score > bestScore) { bestScore = score; best = q; bestSupport = support }
            }
        }
        val q = best ?: return null
        if (bestSupport < 0.45f) return null
        val r = refineSides(q, grad, w, h)
        return FloatArray(8) { i -> if (i % 2 == 0) ((r[i] + 0.5f) / w).coerceIn(0f, 1f) else ((r[i] + 0.5f) / h).coerceIn(0f, 1f) }
    }

    /** 3×3 maximum (or minimum) filter. */
    private fun minMax(a: IntArray, w: Int, h: Int, max: Boolean): IntArray {
        val out = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var v = if (max) Int.MIN_VALUE else Int.MAX_VALUE
            for (dy in -1..1) {
                val yy = (y + dy).coerceIn(0, h - 1)
                for (dx in -1..1) {
                    val c = a[yy * w + (x + dx).coerceIn(0, w - 1)]
                    v = if (max) kotlin.math.max(v, c) else kotlin.math.min(v, c)
                }
            }
            out[y * w + x] = v
        }
        return out
    }

    private fun gradient(a: IntArray, w: Int, h: Int): IntArray {
        val g = IntArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            // Sobel
            val gx = (a[i - w + 1] + 2 * a[i + 1] + a[i + w + 1]) - (a[i - w - 1] + 2 * a[i - 1] + a[i + w - 1])
            val gy = (a[i + w - 1] + 2 * a[i + w] + a[i + w + 1]) - (a[i - w - 1] + 2 * a[i - w] + a[i - w + 1])
            g[i] = kotlin.math.abs(gx) + kotlin.math.abs(gy)
        }
        return g
    }

    /** Region of pixels ≥ [t] connected to the bright part (≥ [v]) of the central box. */
    private fun growFrom(f: IntArray, w: Int, h: Int, t: Int, v: Int, cx0: Int, cx1: Int, cy0: Int, cy1: Int): BooleanArray? {
        val seen = BooleanArray(w * h)
        val stack = IntArray(w * h)
        var sp = 0
        for (y in cy0 until cy1) for (x in cx0 until cx1) {
            val i = y * w + x
            if (f[i] >= v && !seen[i]) { seen[i] = true; stack[sp++] = i }
        }
        if (sp == 0) return null
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w; val y = i / w
            if (x > 0) { val j = i - 1; if (!seen[j] && f[j] >= t) { seen[j] = true; stack[sp++] = j } }
            if (x < w - 1) { val j = i + 1; if (!seen[j] && f[j] >= t) { seen[j] = true; stack[sp++] = j } }
            if (y > 0) { val j = i - w; if (!seen[j] && f[j] >= t) { seen[j] = true; stack[sp++] = j } }
            if (y < h - 1) { val j = i + w; if (!seen[j] && f[j] >= t) { seen[j] = true; stack[sp++] = j } }
        }
        return seen
    }

    /** The region plus every hole inside it (words, shadows, logos on the page). */
    private fun fillHoles(m: BooleanArray, w: Int, h: Int): BooleanArray {
        val outside = BooleanArray(m.size)
        val stack = IntArray(m.size)
        var sp = 0
        fun push(i: Int) { if (!m[i] && !outside[i]) { outside[i] = true; stack[sp++] = i } }
        for (x in 0 until w) { push(x); push((h - 1) * w + x) }
        for (y in 0 until h) { push(y * w); push(y * w + w - 1) }
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w; val y = i / w
            if (x > 0) push(i - 1)
            if (x < w - 1) push(i + 1)
            if (y > 0) push(i - w)
            if (y < h - 1) push(i + w)
        }
        return BooleanArray(m.size) { !outside[it] }
    }

    /** Convex hull (x, y pairs, counter-clockwise on screen) of the region, from its row ends. */
    private fun hullOf(m: BooleanArray, w: Int, h: Int): FloatArray {
        val pts = ArrayList<Long>()
        for (y in 0 until h) {
            var first = -1; var last = -1
            for (x in 0 until w) if (m[y * w + x]) { if (first < 0) first = x; last = x }
            if (first >= 0) { pts.add(first.toLong() shl 32 or y.toLong()); if (last != first) pts.add(last.toLong() shl 32 or y.toLong()) }
        }
        pts.sort()
        if (pts.size < 3) return FloatArray(0)
        fun px(p: Long) = (p shr 32).toFloat()
        fun py(p: Long) = (p and 0xFFFFFFFFL).toFloat()
        fun cross(o: Long, a: Long, b: Long) = (px(a) - px(o)) * (py(b) - py(o)) - (py(a) - py(o)) * (px(b) - px(o))
        val hull = ArrayList<Long>()
        for (p in pts) {
            while (hull.size >= 2 && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) hull.removeAt(hull.size - 1)
            hull.add(p)
        }
        val lower = hull.size + 1
        for (i in pts.size - 2 downTo 0) {
            val p = pts[i]
            while (hull.size >= lower && cross(hull[hull.size - 2], hull[hull.size - 1], p) <= 0) hull.removeAt(hull.size - 1)
            hull.add(p)
        }
        hull.removeAt(hull.size - 1)
        return FloatArray(hull.size * 2) { i -> if (i % 2 == 0) px(hull[i / 2]) else py(hull[i / 2]) }
    }

    /** Largest 4-corner shape with its corners on the hull, as TL, TR, BR, BL (pixels). */
    private fun maxQuad(hull: FloatArray): FloatArray? {
        val m = hull.size / 2
        if (m < 4) return null
        fun area(idx: IntArray): Float {
            var a = 0f
            for (k in 0 until 4) {
                val i = idx[k]; val j = idx[(k + 1) % 4]
                a += hull[i * 2] * hull[j * 2 + 1] - hull[j * 2] * hull[i * 2 + 1]
            }
            return kotlin.math.abs(a) / 2f
        }
        var idx = intArrayOf(0, m / 4, m / 2, 3 * m / 4)
        var best = area(idx)
        var improved = true
        var rounds = 0
        while (improved && rounds++ < 50) {
            improved = false
            for (k in 0 until 4) {
                val a = idx[(k + 3) % 4]; val b = idx[(k + 1) % 4]
                var j = (a + 1) % m
                while (j != b) {
                    val cand = idx.copyOf(); cand[k] = j
                    val ar = area(cand)
                    if (ar > best + 1e-4f) { best = ar; idx = cand; improved = true }
                    j = (j + 1) % m
                }
            }
        }
        // order TL, TR, BR, BL (clockwise on screen, starting at the smallest x + y)
        val cx = idx.sumOf { hull[it * 2].toDouble() }.toFloat() / 4f
        val cy = idx.sumOf { hull[it * 2 + 1].toDouble() }.toFloat() / 4f
        val sorted = idx.sortedBy { kotlin.math.atan2(hull[it * 2 + 1] - cy, hull[it * 2] - cx) }
        val start = (0 until 4).minByOrNull { hull[sorted[it] * 2] + hull[sorted[it] * 2 + 1] } ?: 0
        return FloatArray(8) { i -> val p = sorted[(start + i / 2) % 4]; if (i % 2 == 0) hull[p * 2] else hull[p * 2 + 1] }
    }

    private fun polyArea(q: FloatArray): Float {
        var a = 0f
        for (i in 0 until 4) { val j = (i + 1) % 4; a += q[i * 2] * q[j * 2 + 1] - q[j * 2] * q[i * 2 + 1] }
        return kotlin.math.abs(a) / 2f
    }

    /** Convex with corner angles between 45° and 135° (a page seen at a reasonable slant). */
    private fun goodShape(q: FloatArray): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val ax = q[((i + 3) % 4) * 2]; val ay = q[((i + 3) % 4) * 2 + 1]
            val bx = q[i * 2]; val by = q[i * 2 + 1]
            val cx = q[((i + 1) % 4) * 2]; val cy = q[((i + 1) % 4) * 2 + 1]
            val cross = (bx - ax) * (cy - by) - (by - ay) * (cx - bx)
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
            val v1x = ax - bx; val v1y = ay - by; val v2x = cx - bx; val v2y = cy - by
            val l = hypot(v1x, v1y) * hypot(v2x, v2y)
            if (l < 1e-3f) return false
            val ang = Math.toDegrees(kotlin.math.acos(((v1x * v2x + v1y * v2y) / l).coerceIn(-1f, 1f).toDouble()))
            if (ang < 45 || ang > 135) return false
        }
        return true
    }

    /**
     * Share of the four sides that runs along a real edge (0…1). Parts lying on the picture's
     * border (page running off the photo) count partly; their share is added to [border].
     */
    private fun sideSupport(q: FloatArray, g: IntArray, w: Int, h: Int, thr: Float, border: FloatArray): Float {
        var total = 0f
        for (s in 0 until 4) {
            val x0 = q[s * 2]; val y0 = q[s * 2 + 1]
            val x1 = q[((s + 1) % 4) * 2]; val y1 = q[((s + 1) % 4) * 2 + 1]
            val len = max(2, hypot(x1 - x0, y1 - y0).toInt())
            val nx = -(y1 - y0) / len; val ny = (x1 - x0) / len
            var hits = 0f; var onBorder = 0
            for (t in 0 until len) {
                val x = x0 + (x1 - x0) * t / len; val y = y0 + (y1 - y0) * t / len
                var b = 0
                for (d in -2..2) {
                    val xx = (x + nx * d).roundToInt(); val yy = (y + ny * d).roundToInt()
                    if (xx in 0 until w && yy in 0 until h) b = max(b, g[yy * w + xx])
                }
                if (b > thr) hits += 1f
                else if (x <= 2 || y <= 2 || x >= w - 3 || y >= h - 3) { hits += 0.6f; onBorder++ }
            }
            total += hits / len
            border[0] += onBorder.toFloat() / len
        }
        return total / 4f
    }

    /** How much brighter (paper-like) the region is than a thin ring around it. */
    private fun contrast(f: IntArray, m: BooleanArray, w: Int, h: Int): Float {
        var ring = m
        repeat(4) {
            val prev = ring
            ring = BooleanArray(prev.size) { i ->
                val x = i % w; val y = i / w
                prev[i] || (x > 0 && prev[i - 1]) || (x < w - 1 && prev[i + 1]) || (y > 0 && prev[i - w]) || (y < h - 1 && prev[i + w])
            }
        }
        var sIn = 0L; var nIn = 0; var sOut = 0L; var nOut = 0
        for (i in m.indices) {
            if (m[i]) { sIn += f[i]; nIn++ } else if (ring[i]) { sOut += f[i]; nOut++ }
        }
        if (nIn == 0 || nOut == 0) return 0f
        return sIn.toFloat() / nIn - sOut.toFloat() / nOut
    }

    /** Moves each side a few pixels (both ends independently) onto the strongest edge, then re-corners. */
    private fun refineSides(q: FloatArray, g: IntArray, w: Int, h: Int): FloatArray {
        fun segScore(x0: Float, y0: Float, x1: Float, y1: Float): Float {
            val len = max(4, hypot(x1 - x0, y1 - y0).toInt())
            var s = 0L; var c = 0
            for (t in (len * 0.1f).toInt() until (len * 0.9f).toInt()) {
                val x = (x0 + (x1 - x0) * t / len).roundToInt(); val y = (y0 + (y1 - y0) * t / len).roundToInt()
                if (x in 0 until w && y in 0 until h) { s += g[y * w + x]; c++ }
            }
            return if (c == 0) 0f else s.toFloat() / c
        }
        val lines = Array(4) { FloatArray(4) }
        for (s in 0 until 4) {
            val x0 = q[s * 2]; val y0 = q[s * 2 + 1]
            val x1 = q[((s + 1) % 4) * 2]; val y1 = q[((s + 1) % 4) * 2 + 1]
            val len = hypot(x1 - x0, y1 - y0) + 1e-6f
            val nx = -(y1 - y0) / len; val ny = (x1 - x0) / len
            var best = segScore(x0, y0, x1, y1); var ba = 0; var bb = 0
            for (da in -6..6) for (db in -6..6) {
                val sc = segScore(x0 + nx * da, y0 + ny * da, x1 + nx * db, y1 + ny * db)
                if (sc > best * 1.02f) { best = sc; ba = da; bb = db }
            }
            lines[s] = floatArrayOf(x0 + nx * ba, y0 + ny * ba, x1 + nx * bb, y1 + ny * bb)
        }
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val a = lines[(i + 3) % 4]; val b = lines[i]
            val d = (a[0] - a[2]) * (b[1] - b[3]) - (a[1] - a[3]) * (b[0] - b[2])
            if (kotlin.math.abs(d) < 1e-6f) return q
            val p = a[0] * a[3] - a[1] * a[2]; val r = b[0] * b[3] - b[1] * b[2]
            out[i * 2] = ((p * (b[0] - b[2]) - (a[0] - a[2]) * r) / d).coerceIn(0f, w - 1f)
            out[i * 2 + 1] = ((p * (b[1] - b[3]) - (a[1] - a[3]) * r) / d).coerceIn(0f, h - 1f)
        }
        return if (goodShape(out) && polyArea(out) > polyArea(q) * 0.7f) out else q
    }

    private fun boxBlur(a: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(a.size)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0; var n = 0
            for (dy in -1..1) {
                val yy = y + dy
                if (yy < 0 || yy >= h) continue
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx < 0 || xx >= w) continue
                    s += a[yy * w + xx]; n++
                }
            }
            out[y * w + x] = s / n
        }
        return out
    }

    /** Area of a quad given as fractions (shoelace). */
    fun quadArea(q: FloatArray): Float {
        var a = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            a += q[i * 2] * q[j * 2 + 1] - q[j * 2] * q[i * 2 + 1]
        }
        return kotlin.math.abs(a) / 2f
    }

    // ------------------------------------------------------------------ filters

    fun applyFilter(src: Bitmap, f: ScanFilter): Bitmap {
        if (f == ScanFilter.ORIGINAL) return src
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        when (f) {
            ScanFilter.ORIGINAL -> Unit
            ScanFilter.AUTO -> autoScan(src, px, w, h)
            ScanFilter.DOCS -> { flatten(src, px, w, h); curve(px, 70, 245); saturate(px, 0.55f) }
            ScanFilter.COLOR -> { flatten(src, px, w, h); curve(px, 40, 250); saturate(px, 1.45f) }
            ScanFilter.IMAGE -> { contrast(px, 1.12f, 0); saturate(px, 1.4f) }
            ScanFilter.SUPER -> { flatten(src, px, w, h); curve(px, 90, 235); saturate(px, 0.8f); sharpen(px, w, h) }
            ScanFilter.ENHANCE -> { contrast(px, 1.25f, 14); saturate(px, 1.15f) }
            ScanFilter.ENHANCE2 -> { contrast(px, 1.45f, -10); saturate(px, 1.3f); sharpen(px, w, h) }
            ScanFilter.BW -> { flatten(src, px, w, h); threshold(px, 185) }
            ScanFilter.BW2 -> { saturate(px, 0f); contrast(px, 2.1f, 10) }
            ScanFilter.GRAY -> saturate(px, 0f)
            ScanFilter.INVERT -> for (i in px.indices) px[i] = (px[i] and 0xFF000000.toInt()) or (px[i].inv() and 0x00FFFFFF)
        }
        val out = if (src.isMutable) src else src.copy(Bitmap.Config.ARGB_8888, true).also { src.recycle() }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v

    private inline fun map(px: IntArray, f: (Int, Int, Int) -> Int) {
        for (i in px.indices) {
            val c = px[i]
            px[i] = f((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)
        }
    }

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)

    private fun contrast(px: IntArray, k: Float, bright: Int) = map(px) { r, g, b ->
        rgb(((r - 128) * k + 128 + bright).toInt(), ((g - 128) * k + 128 + bright).toInt(), ((b - 128) * k + 128 + bright).toInt())
    }

    private fun saturate(px: IntArray, s: Float) = map(px) { r, g, b ->
        val l = (r * 299 + g * 587 + b * 114) / 1000f
        rgb((l + (r - l) * s).toInt(), (l + (g - l) * s).toInt(), (l + (b - l) * s).toInt())
    }

    /** Linear stretch so [lo] becomes black and [hi] white. */
    private fun curve(px: IntArray, lo: Int, hi: Int) {
        val k = 255f / max(1, hi - lo)
        map(px) { r, g, b -> rgb(((r - lo) * k).toInt(), ((g - lo) * k).toInt(), ((b - lo) * k).toInt()) }
    }

    private fun threshold(px: IntArray, t: Int) = map(px) { r, g, b ->
        val l = (r * 299 + g * 587 + b * 114) / 1000
        if (l > t) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    }

    /**
     * "Auto": a neat, clear scan. Shadows and uneven light are evened out so the paper turns
     * white, the ink is darkened and the text sharpened, while colours stay natural. Pictures
     * that are not paper (little white background) only get a gentle level + sharpness boost.
     */
    private fun autoScan(src: Bitmap, px: IntArray, w: Int, h: Int) {
        // how much of the picture is light, low-saturation "paper"?
        var paper = 0
        val step = max(1, px.size / 20000)
        var n = 0
        var i = 0
        while (i < px.size) {
            val c = px[i]
            val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
            val mx = max(r, max(g, b)); val mn = minOf(r, g, b)
            if (mx > 110 && mx - mn < 45) paper++
            n++
            i += step
        }
        if (paper.toFloat() / n > 0.35f) {
            flatten(src, px, w, h)       // paper → white, shadows gone
            curve(px, 35, 248)           // deeper ink, clean white
            saturate(px, 1.1f)
        } else {
            autoLevels(px, 1.1f)
        }
        sharpenMild(px, w, h)
    }

    /** Light unsharp mask (text edges crisper without halos). */
    private fun sharpenMild(px: IntArray, w: Int, h: Int) {
        val src = px.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            var out = 0xFF shl 24
            for (s in intArrayOf(16, 8, 0)) {
                val c = (src[i] shr s) and 0xFF
                val avg = (((src[i - 1] shr s) and 0xFF) + ((src[i + 1] shr s) and 0xFF) +
                    ((src[i - w] shr s) and 0xFF) + ((src[i + w] shr s) and 0xFF)) / 4
                out = out or (clamp(c + (c - avg) * 6 / 10) shl s)
            }
            px[i] = out
        }
    }

    /** Stretches the 1st–99th brightness percentiles to the full range. */
    private fun autoLevels(px: IntArray, sat: Float) {
        val hist = IntArray(256)
        for (c in px) hist[(((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000]++
        val cut = px.size / 100
        var lo = 0; var acc = 0
        while (lo < 255 && acc + hist[lo] <= cut) { acc += hist[lo]; lo++ }
        var hi = 255; acc = 0
        while (hi > 0 && acc + hist[hi] <= cut) { acc += hist[hi]; hi-- }
        if (hi - lo > 30) curve(px, lo, hi)
        saturate(px, sat)
    }

    /**
     * Evens out lighting and shadows: divides each pixel by an estimate of the paper colour
     * around it (a heavily blurred, text-free copy of the page), so the paper turns white.
     */
    private fun flatten(src: Bitmap, px: IntArray, w: Int, h: Int) {
        val sw = max(4, w / 24)
        val sh = max(4, h / 24)
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val sp = IntArray(sw * sh)
        small.getPixels(sp, 0, sw, 0, 0, sw, sh)
        if (small !== src) small.recycle()
        // dilate twice: text (dark) disappears, paper (light) remains
        var cur = sp
        repeat(2) {
            val n = IntArray(cur.size)
            for (y in 0 until sh) for (x in 0 until sw) {
                var r = 0; var g = 0; var b = 0
                for (dy in -1..1) {
                    val yy = (y + dy).coerceIn(0, sh - 1)
                    for (dx in -1..1) {
                        val c = cur[yy * sw + (x + dx).coerceIn(0, sw - 1)]
                        r = max(r, (c shr 16) and 0xFF); g = max(g, (c shr 8) and 0xFF); b = max(b, c and 0xFF)
                    }
                }
                n[y * sw + x] = rgb(r, g, b)
            }
            cur = n
        }
        val bgSmall = Bitmap.createBitmap(cur, sw, sh, Bitmap.Config.ARGB_8888)
        val bg = Bitmap.createScaledBitmap(bgSmall, w, h, true)
        bgSmall.recycle()
        val bp = IntArray(w * h)
        bg.getPixels(bp, 0, w, 0, 0, w, h)
        bg.recycle()
        for (i in px.indices) {
            val c = px[i]; val d = bp[i]
            val br = max(40, (d shr 16) and 0xFF); val bgc = max(40, (d shr 8) and 0xFF); val bb = max(40, d and 0xFF)
            px[i] = rgb(((c shr 16) and 0xFF) * 255 / br, ((c shr 8) and 0xFF) * 255 / bgc, (c and 0xFF) * 255 / bb)
        }
    }

    private fun sharpen(px: IntArray, w: Int, h: Int) {
        val src = px.copyOf()
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            fun ch(c: Int, s: Int) = (c shr s) and 0xFF
            var r = 0; var g = 0; var b = 0
            for (s in intArrayOf(16, 8, 0)) {
                val v = 5 * ch(src[i], s) - ch(src[i - 1], s) - ch(src[i + 1], s) - ch(src[i - w], s) - ch(src[i + w], s)
                when (s) { 16 -> r = v; 8 -> g = v; else -> b = v }
            }
            px[i] = rgb(r, g, b)
        }
    }
}
