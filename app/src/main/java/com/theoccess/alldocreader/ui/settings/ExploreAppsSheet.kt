package com.theoccess.alldocreader.ui.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.ads.Ads
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.ItemExploreAppBinding
import com.theoccess.alldocreader.databinding.ItemExploreNativeBinding
import com.theoccess.alldocreader.databinding.SheetExploreAppsBinding
import com.theoccess.alldocreader.util.Links
import com.theoccess.alldocreader.util.dp

/**
 * "Thank you for your support! Don't miss out on our top-rated apps 👇" — like the original app:
 * rows of apps, 3 per page, swiping (and turning by itself every few seconds), page dots, an
 * "AD" tag and Open on every row.
 *
 * Rows are your own apps first ([APPS], cross-promotion → Google Play), then Google native ads
 * (never for premium users) — a spinner shows while they load. Opened from Settings → "Explore
 * more apps", and sometimes when the user leaves the app with Back ([shouldShowOnExit]); closing
 * it then closes the app.
 */
object ExploreAppsSheet {

    /** name, short description, Play package name, icon. Fill in your own apps. */
    data class App(val name: String, val description: String, val packageName: String, val icon: Int)

    /**
     * YOUR OTHER APPS (shown first). Example:
     *   App("PDF Scanner", "Scan documents to PDF in seconds", "com.theoccess.pdfscanner", <your icon drawable>)
     * Leave empty to fill the sheet with ads only.
     */
    val APPS: List<App> = emptyList()

    private const val PER_PAGE = 3
    private const val AUTO_TURN_MS = 3500L
    private const val KEY_EXITS = "explore_exit_count"
    /** Shown on every [EXIT_EVERY]-th time the user leaves the app with Back. */
    private const val EXIT_EVERY = 3

    private sealed class Row {
        class Own(val app: App) : Row()
        class Ad(val ad: NativeAd) : Row()
    }

    private fun rows(context: Context): List<Row> =
        APPS.map { Row.Own(it) } + Ads.nativeAds(context).map { Row.Ad(it) }

    /** Back on the home screen: should the sheet be shown before the app closes? */
    fun shouldShowOnExit(context: Context): Boolean {
        if (rows(context).isEmpty()) return false
        val n = Prefs.raw.getInt(KEY_EXITS, 0) + 1
        Prefs.raw.edit().putInt(KEY_EXITS, n).apply()
        return n % EXIT_EVERY == 0
    }

    /** [onClosed] runs when the sheet is closed (X, Back, swipe down or tap outside). */
    fun show(context: Context, onClosed: (() -> Unit)? = null) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetExploreAppsBinding.inflate(LayoutInflater.from(context))
        val handler = Handler(Looper.getMainLooper())
        var turn: Runnable? = null
        var closed = false

        fun fill(list: List<Row>) {
            if (closed) return
            b.pagerLoading.visibility = View.GONE
            val pages = list.chunked(PER_PAGE)
            if (pages.isEmpty()) {
                b.tvEmpty.visibility = View.VISIBLE   // no own apps listed and no ad (offline)
                return
            }
            b.pager.adapter = PageAdapter(pages)
            (b.pager.getChildAt(0) as? RecyclerView)?.overScrollMode = View.OVER_SCROLL_NEVER
            buildDots(context, b.dots, pages.size)
            b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) = selectDot(context, b.dots, position)
            })
            selectDot(context, b.dots, 0)
            // turn the pages by themselves, like the original
            if (pages.size > 1) {
                val r = object : Runnable {
                    override fun run() {
                        val n = b.pager.adapter?.itemCount ?: 0
                        if (n > 1) b.pager.setCurrentItem((b.pager.currentItem + 1) % n, true)
                        handler.postDelayed(this, AUTO_TURN_MS)
                    }
                }
                turn = r
                handler.postDelayed(r, AUTO_TURN_MS)
            }
        }

        // your own apps right away; the ad rows as soon as Google has delivered them
        val own = APPS.map { Row.Own(it) }
        b.pagerLoading.visibility = View.VISIBLE
        Ads.withNativeAds(context) { ads -> fill(own + ads.map { Row.Ad(it) }) }

        b.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            closed = true
            turn?.let { handler.removeCallbacks(it) }
            onClosed?.invoke()
        }
        dialog.setContentView(b.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    // ------------------------------------------------------------------ pages

    private class PageAdapter(val pages: List<List<Row>>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val page = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            return object : RecyclerView.ViewHolder(page) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val page = holder.itemView as LinearLayout
            page.removeAllViews()
            val inf = LayoutInflater.from(page.context)
            pages[position].forEach { row ->
                when (row) {
                    is Row.Own -> page.addView(ownRow(inf, page, row.app))
                    is Row.Ad -> page.addView(adRow(inf, page, row.ad))
                }
            }
        }
    }

    private fun ownRow(inf: LayoutInflater, parent: ViewGroup, app: App): View {
        val r = ItemExploreAppBinding.inflate(inf, parent, false)
        val context = parent.context
        r.ivIcon.setImageResource(app.icon)
        r.tvName.text = app.name
        r.tvDesc.text = app.description
        val open = { Links.open(context, "https://play.google.com/store/apps/details?id=${app.packageName}") }
        r.btnOpen.setOnClickListener { open() }
        r.root.setOnClickListener { open() }
        return r.root
    }

    private fun adRow(inf: LayoutInflater, parent: ViewGroup, ad: NativeAd): View {
        val r = ItemExploreNativeBinding.inflate(inf, parent, false)
        val v = r.root
        r.tvName.text = ad.headline
        r.tvDesc.text = ad.body ?: ""
        val icon = ad.icon?.drawable
        if (icon != null) r.ivIcon.setImageDrawable(icon) else r.ivIcon.setImageResource(R.drawable.ic_logo)
        r.btnOpen.text = ad.callToAction ?: parent.context.getString(R.string.open)
        v.headlineView = r.tvName
        v.bodyView = r.tvDesc
        v.iconView = r.ivIcon
        v.callToActionView = r.btnOpen
        v.setNativeAd(ad)
        return v
    }

    // ------------------------------------------------------------------ dots

    private fun buildDots(context: Context, dots: LinearLayout, n: Int) {
        dots.removeAllViews()
        if (n < 2) return
        repeat(n) {
            dots.addView(View(context), LinearLayout.LayoutParams(context.dp(6), context.dp(6)).apply {
                marginStart = context.dp(3); marginEnd = context.dp(3)
            })
        }
    }

    private fun selectDot(context: Context, dots: LinearLayout, selected: Int) {
        for (i in 0 until dots.childCount) {
            val on = i == selected
            dots.getChildAt(i).apply {
                layoutParams = (layoutParams as LinearLayout.LayoutParams).apply { width = context.dp(if (on) 14 else 6) }
                background = GradientDrawable().apply {
                    cornerRadius = context.dp(3).toFloat()
                    setColor(if (on) context.getColor(R.color.primary) else context.getColor(R.color.dot_off))
                }
            }
        }
    }
}
