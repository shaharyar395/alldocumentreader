package com.theoccess.alldocreader.ui.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.R

/**
 * The page slider (≡ handle) on the right edge of the reader, like the original: it appears while
 * scrolling, fades out after a moment and can be dragged to move through the document quickly.
 *
 * Unlike RecyclerView's built-in fast scroller, its track ends where this view ends, so the
 * reader can keep the handle's lowest position just above the Edit button (see [setBottomGap]).
 * Touches outside the handle go through to the pages.
 */
class PageScrollBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private val thumb = ImageView(context).apply {
        setImageResource(R.drawable.ic_page_slider_thumb)
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var rv: RecyclerView? = null
    private var dragging = false
    private var grabOffset = 0f
    private var shownOnce = false
    private val hide = Runnable { if (!dragging) thumb.animate().alpha(0f).setDuration(250).start() }

    init {
        val d = resources.displayMetrics.density
        // big enough to see and grab: 30 × 46 dp (the old handle was 22 × 26 dp)
        addView(thumb, LayoutParams((30 * d).toInt(), (46 * d).toInt(), Gravity.TOP or Gravity.END).apply {
            marginEnd = (2 * d).toInt()
        })
        thumb.alpha = 0f
    }

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dragging) return   // the handle follows the finger while dragging
            placeThumb()
            when {
                dy != 0 && scrollable() -> reveal()
                // a long document has just opened: show the slider for a moment so people find it
                !shownOnce && scrollable() -> { shownOnce = true; reveal() }
            }
        }
    }

    fun attach(list: RecyclerView) {
        rv?.removeOnScrollListener(scrollListener)
        rv = list
        list.addOnScrollListener(scrollListener)
        post { placeThumb() }
    }

    /** Space kept free under the handle's lowest position (e.g. for the Edit button). */
    fun setBottomGap(px: Int) {
        val lp = layoutParams as? MarginLayoutParams ?: return
        if (lp.bottomMargin == px) return
        lp.bottomMargin = px
        layoutParams = lp
        post { placeThumb() }
    }

    private fun range(): Int {
        val list = rv ?: return 0
        return list.computeVerticalScrollRange() - list.computeVerticalScrollExtent()
    }

    /**
     * The same rule for every file type (PDF, Word, Excel, PowerPoint, TXT): the slider is there
     * whenever the document has more than one page and does not fit on the screen.
     */
    private fun scrollable(): Boolean {
        val list = rv ?: return false
        val pages = list.adapter?.itemCount ?: 0
        return pages > 1 && list.computeVerticalScrollRange() > list.computeVerticalScrollExtent()
    }

    private fun travel(): Float = (height - thumb.height).coerceAtLeast(0).toFloat()

    private fun placeThumb() {
        val list = rv ?: return
        val r = range()
        val f = if (r <= 0) 0f else (list.computeVerticalScrollOffset().toFloat() / r).coerceIn(0f, 1f)
        thumb.translationY = f * travel()
    }

    private fun reveal() {
        removeCallbacks(hide)
        thumb.animate().cancel()
        thumb.alpha = 1f
        if (!dragging) postDelayed(hide, 2000)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (changed) placeThumb()
    }

    private fun onThumb(e: MotionEvent): Boolean {
        val slop = 12 * resources.displayMetrics.density
        return thumb.alpha > 0.1f &&
            e.x >= thumb.left - slop * 2 &&
            e.y >= thumb.translationY - slop && e.y <= thumb.translationY + thumb.height + slop
    }

    /** On the slider's strip at the right edge while the slider is showing. */
    private fun onTrack(e: MotionEvent): Boolean {
        val strip = 36 * resources.displayMetrics.density
        return thumb.alpha > 0.1f && e.x >= width - strip
    }

    /**
     * Shows the part of the document at [f] (0 = start, 1 = end) by page position. Scrolling by
     * pixels (the old way) had to lay out every page in between — very slow and jumpy in long
     * documents (e.g. 1700 pages) — and the estimated pixel range was off for them.
     */
    private fun jumpTo(list: RecyclerView, f: Float) {
        val lm = list.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        val n = list.adapter?.itemCount ?: return
        if (n <= 0) return
        if (f >= 0.999f) {
            // the very end: last page, scrolled to its bottom
            lm.scrollToPositionWithOffset(n - 1, 0)
            list.post { list.scrollBy(0, list.height * 4) }
            return
        }
        val pos = f * n
        val page = pos.toInt().coerceIn(0, n - 1)
        val pageHeight = (lm.findViewByPosition(page) ?: lm.getChildAt(0))?.height ?: list.height
        lm.scrollToPositionWithOffset(page, -((pos - page) * pageHeight).toInt())
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val list = rv ?: return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!scrollable()) return false
                when {
                    // grab the handle where the finger is on it, then swipe up / down
                    onThumb(e) -> grabOffset = e.y - thumb.translationY
                    // slider showing: a tap on its track jumps there (and the finger can keep
                    // swiping from there); while it is hidden, taps go to the pages as usual
                    onTrack(e) -> {
                        grabOffset = thumb.height / 2f
                        val t = travel()
                        val f = if (t <= 0f) 0f else ((e.y - grabOffset) / t).coerceIn(0f, 1f)
                        list.stopScroll()
                        jumpTo(list, f)
                        thumb.translationY = f * t
                    }
                    else -> return false
                }
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                list.stopScroll()
                reveal()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val t = travel()
                val f = if (t <= 0f) 0f else ((e.y - grabOffset) / t).coerceIn(0f, 1f)
                jumpTo(list, f)
                thumb.translationY = f * t
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                reveal()
                return true
            }
        }
        return dragging
    }
}
