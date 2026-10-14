package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.RectF
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.databinding.ItemPageBinding
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Page list for the viewer. Pages are rendered lazily on a single background thread and cached. */
class PageAdapter(
    private val source: PageSource,
    private val scope: CoroutineScope,
    private val renderDispatcher: CoroutineDispatcher
) : RecyclerView.Adapter<PageAdapter.VH>() {

    var horizontal = false
        set(value) { field = value; notifyDataSetChanged() }
    var rotation = 0
        set(value) { field = value % 360; cache.evictAll(); notifyDataSetChanged() }
    var inverted = false
        set(value) { field = value; notifyDataSetChanged() }

    private val cache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    private val invertFilter = ColorMatrixColorFilter(
        ColorMatrix(floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        ))
    )

    /** Pages in the list; grows while a long document is still being laid out ([setPageCount]). */
    private var count = source.pageCount

    override fun getItemCount() = count

    /** More pages are ready (main thread). */
    fun setPageCount(n: Int) {
        if (n > count) {
            val old = count
            count = n
            notifyItemRangeInserted(old, n - old)
        }
    }

    /** These pages turned out to have another size than assumed: measure their rows again. */
    fun pagesResized(pages: List<Int>) {
        pages.filter { it in 0 until count }.forEach { cache.remove(it); notifyItemChanged(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(position)

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_HIGHLIGHT)) holder.bindHighlights(position) else holder.bind(position)
    }

    // ------------------------------------------------------------------ search highlights

    /** page → every match box on it; and the boxes of the current match. */
    private var highlights: Map<Int, List<RectF>> = emptyMap()
    private var currentPage = -1
    private var currentBoxes: List<RectF> = emptyList()

    fun setHighlights(all: Map<Int, List<RectF>>, page: Int, current: List<RectF>) {
        val changed = (highlights.keys + all.keys + setOf(currentPage, page)).filter { it in 0 until itemCount }
        highlights = all
        currentPage = page
        currentBoxes = current
        changed.forEach { notifyItemChanged(it, PAYLOAD_HIGHLIGHT) }
    }

    fun clearHighlights() = setHighlights(emptyMap(), -1, emptyList())

    /** Distance in pixels from the top of the page view to [yPt] (page points), for scrolling to a match. */
    fun offsetInPage(index: Int, yPt: Float, listWidth: Int, density: Float): Int {
        val padH = (16 * density)
        val padTop = (4 * density)
        val shownW = listWidth - padH
        return (padTop + yPt * shownW / source.pageWidth(index)).toInt()
    }

    override fun onViewRecycled(holder: VH) {
        holder.job?.cancel()
        holder.b.ivPage.setImageDrawable(null)
    }

    /** Page aspect (height / width) after the current rotation. */
    private fun aspect(index: Int): Float {
        val w = source.pageWidth(index)
        val h = source.pageHeight(index)
        return if (rotation % 180 == 0) h / w else w / h
    }

    inner class VH(val b: ItemPageBinding) : RecyclerView.ViewHolder(b.root) {
        var job: Job? = null

        fun bindHighlights(index: Int) {
            b.highlights.target = b.ivPage
            b.highlights.pageWidth = source.pageWidth(index)
            b.highlights.pageHeight = source.pageHeight(index)
            b.highlights.rotation90 = rotation / 90
            b.highlights.set(highlights[index].orEmpty(), if (index == currentPage) currentBoxes else emptyList())
        }

        fun bind(index: Int) {
            val parent = b.root.parent as? View
            val rvW = (parent?.width ?: b.root.resources.displayMetrics.widthPixels).takeIf { it > 0 }
                ?: b.root.resources.displayMetrics.widthPixels
            val rvH = (parent?.height ?: 0).takeIf { it > 0 } ?: b.root.resources.displayMetrics.heightPixels
            val pad = b.root.paddingStart + b.root.paddingEnd
            val lp = b.root.layoutParams
            if (horizontal) {
                lp.width = rvW
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT
            } else {
                lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                lp.height = ((rvW - pad) * aspect(index)).toInt() + b.root.paddingTop + b.root.paddingBottom
            }
            b.root.layoutParams = lp
            if (horizontal) {
                val maxW = rvW - pad
                val maxH = rvH - b.root.paddingTop - b.root.paddingBottom
                val a = aspect(index)
                val ivLp = b.ivPage.layoutParams as android.widget.FrameLayout.LayoutParams
                if (maxW * a <= maxH) { ivLp.width = maxW; ivLp.height = (maxW * a).toInt() }
                else { ivLp.height = maxH; ivLp.width = (maxH / a).toInt() }
                ivLp.gravity = android.view.Gravity.CENTER
                b.ivPage.layoutParams = ivLp
            } else {
                val ivLp = b.ivPage.layoutParams as android.widget.FrameLayout.LayoutParams
                ivLp.width = ViewGroup.LayoutParams.MATCH_PARENT
                ivLp.height = ViewGroup.LayoutParams.MATCH_PARENT
                b.ivPage.layoutParams = ivLp
            }
            b.ivPage.colorFilter = if (inverted) invertFilter else null
            bindHighlights(index)

            job?.cancel()
            val cached = cache.get(index)
            if (cached != null) {
                b.ivPage.setImageBitmap(cached)
                b.pbPage.visibility = View.GONE
                return
            }
            b.ivPage.setImageDrawable(null)
            b.pbPage.visibility = View.VISIBLE
            // Word pages are drawn at 1.5× the screen width (shown scaled down): small text and page
            // scans stay sharp, also when zoomed in a little. Other types as before.
            val target = if (source is DocPageSource) ((rvW - pad) * 3 / 2).coerceAtMost(2000)
            else (rvW - pad).coerceAtMost(1600)
            val rot = rotation
            job = scope.launch {
                val bmp = try {
                    withContext(renderDispatcher) {
                        val w = if (rot % 180 == 0) target else (target * source.pageWidth(index) / source.pageHeight(index)).toInt()
                        val raw = source.render(index, w)
                        if (rot == 0) raw else {
                            val m = Matrix().apply { postRotate(rot.toFloat()) }
                            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it != raw) raw.recycle() }
                        }
                    }
                } catch (e: Exception) {
                    null
                }
                if (bmp != null && rot == rotation) {
                    cache.put(index, bmp)
                    if (bindingAdapterPosition == index) {
                        b.ivPage.setImageBitmap(bmp)
                        b.pbPage.visibility = View.GONE
                    }
                }
            }
        }
    }

    fun clear() = cache.evictAll()

    companion object {
        private const val PAYLOAD_HIGHLIGHT = "highlight"
    }
}
