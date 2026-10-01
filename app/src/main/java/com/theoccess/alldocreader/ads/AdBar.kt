package com.theoccess.alldocreader.ads

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.lifecycle.Observer
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.theoccess.alldocreader.premium.Billing

/**
 * The ad bar used above the bottom tabs, under file lists and under the reader's toolbar
 * (an anchored adaptive banner, full screen width). Stays hidden until an ad has loaded, and
 * for premium users.
 */
class AdBar @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {

    private var adView: AdView? = null
    private val premiumObserver = Observer<Boolean> { premium -> if (premium) clear() else load() }

    init {
        visibility = View.GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Billing.premium.observeForever(premiumObserver)
    }

    override fun onDetachedFromWindow() {
        Billing.premium.removeObserver(premiumObserver)
        clear()
        super.onDetachedFromWindow()
    }

    private fun load() {
        if (adView != null || isInEditMode) return
        Ads.whenReady {
            if (!isAttachedToWindow || adView != null || !Ads.enabled) return@whenReady
            val dm = resources.displayMetrics
            val widthPx = if (width > 0) width else dm.widthPixels
            val av = AdView(context).apply {
                adUnitId = AdIds.BANNER
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, (widthPx / dm.density).toInt()))
                adListener = object : AdListener() {
                    override fun onAdLoaded() { this@AdBar.visibility = View.VISIBLE }
                    override fun onAdFailedToLoad(e: LoadAdError) { this@AdBar.visibility = View.GONE }
                }
            }
            adView = av
            addView(av, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            av.loadAd(Ads.request())
        }
    }

    private fun clear() {
        adView?.let { removeView(it); it.destroy() }
        adView = null
        visibility = View.GONE
    }

    fun pause() = adView?.pause()
    fun resume() = adView?.resume()
}
