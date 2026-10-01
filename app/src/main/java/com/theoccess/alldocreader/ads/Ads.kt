package com.theoccess.alldocreader.ads

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.theoccess.alldocreader.premium.Billing
import com.theoccess.alldocreader.ui.settings.PremiumActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ads, placed like the original app:
 *  - a small ad bar above the bottom tabs, at the bottom of file lists and under the
 *    reader's toolbar ([AdBar]);
 *  - a full-screen (interstitial) ad after a file is converted or saved, and when a file
 *    category is opened from the home screen — that one is followed by the "Get Premium" page.
 * Premium users never see ads. Full-screen ads are at most one every [GAP_MS].
 */
object Ads {

    private const val TAG = "Ads"
    private const val GAP_MS = 45_000L

    private val started = AtomicBoolean(false)
    @Volatile
    private var ready = false
    private var interstitial: InterstitialAd? = null
    private var interstitialLoadedAt = 0L
    /** Full-screen ads older than this are thrown away (old ads can show up black). */
    private const val INTERSTITIAL_MAX_AGE_MS = 50 * 60_000L
    private var loading = false
    private var lastShownAt = 0L
    private var premiumOfferedThisRun = false
    private var appContext: Context? = null
    private val readyListeners = ArrayList<() -> Unit>()

    /** True when ads may be requested: not premium, consent handled and the SDK started. */
    val enabled: Boolean get() = ready && !Billing.isPremium

    /**
     * Call from the first screen. Asks Google's consent form when the user is in a region that
     * needs it (EEA / UK), then starts the ads SDK and preloads a full-screen ad.
     */
    fun init(activity: Activity) {
        appContext = activity.applicationContext
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                if (err != null) Log.w(TAG, "consent form: ${err.message}")
                if (consent.canRequestAds()) start(activity.applicationContext)
            }
        }, { err ->
            Log.w(TAG, "consent info: ${err.message}")
            if (consent.canRequestAds()) start(activity.applicationContext)
        })
        // consent given in an earlier run: no need to wait for the update
        if (consent.canRequestAds()) start(activity.applicationContext)
    }

    private fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        Thread {
            MobileAds.initialize(context) {
                ready = true
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    loadInterstitial(context)
                    loadAppOpen(context)
                    loadNative(context)
                    val l = ArrayList(readyListeners); readyListeners.clear()
                    l.forEach { it() }
                }
            }
        }.start()
    }

    /** Runs [block] once ads can be requested (right away when they already can). */
    fun whenReady(block: () -> Unit) {
        if (ready) block() else readyListeners += block
    }

    fun request(): AdRequest = AdRequest.Builder().build()

    // ------------------------------------------------------------------ full-screen ads

    private fun loadInterstitial(context: Context) {
        if (!enabled || loading || interstitial != null) return
        loading = true
        InterstitialAd.load(context, AdIds.INTERSTITIAL, request(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad; interstitialLoadedAt = SystemClock.elapsedRealtime(); loading = false }
            override fun onAdFailedToLoad(e: LoadAdError) { interstitial = null; loading = false; Log.w(TAG, "interstitial: ${e.message}") }
        })
    }

    /**
     * Shows a full-screen ad if one is ready (and none was shown in the last [GAP_MS]); [then]
     * runs when it is closed — or right away when no ad is shown. Returns true if an ad was shown.
     */
    fun showInterstitial(activity: Activity, force: Boolean = false, then: () -> Unit = {}): Boolean {
        val ad = if (hasInterstitial) interstitial else null
        val now = SystemClock.elapsedRealtime()
        if (!enabled || ad == null || activity.isFinishing || activity.isDestroyed ||
            (!force && lastShownAt > 0 && now - lastShownAt < GAP_MS)
        ) {
            if (enabled) loadInterstitial(activity.applicationContext)
            then()
            return false
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastShownAt = SystemClock.elapsedRealtime()
                loadInterstitial(activity.applicationContext)
                then()
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                loadInterstitial(activity.applicationContext)
                then()
            }
        }
        lastShownAt = now
        ad.show(activity)
        return true
    }

    /** True while a full-screen ad is loaded and waiting. */
    val hasInterstitial: Boolean get() {
        if (interstitial != null && SystemClock.elapsedRealtime() - interstitialLoadedAt > INTERSTITIAL_MAX_AGE_MS) interstitial = null
        return interstitial != null
    }

    // ------------------------------------------------------------------ "Continue to app" ads

    private const val APP_OPEN_MAX_AGE_MS = 4 * 3600_000L   // Google: app open ads expire after 4 h
    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var appOpenLoading = false
    private var launchCount = 0

    private val hasAppOpen: Boolean
        get() = appOpen != null && SystemClock.elapsedRealtime() - appOpenLoadedAt < APP_OPEN_MAX_AGE_MS

    private fun loadAppOpen(context: Context) {
        if (!enabled || appOpenLoading || hasAppOpen) return
        appOpenLoading = true
        AppOpenAd.load(context, AdIds.APP_OPEN, request(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpen = ad; appOpenLoadedAt = SystemClock.elapsedRealtime(); appOpenLoading = false
            }
            override fun onAdFailedToLoad(e: LoadAdError) { appOpen = null; appOpenLoading = false; Log.w(TAG, "app open: ${e.message}") }
        })
    }

    private fun showAppOpen(activity: Activity, then: () -> Unit) {
        val ad = appOpen ?: return then()
        appOpen = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                lastShownAt = SystemClock.elapsedRealtime()
                loadAppOpen(activity.applicationContext)
                then()
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                loadAppOpen(activity.applicationContext)
                then()
            }
        }
        ad.show(activity)
    }

    /** True while a launch ad could still arrive (ads allowed and one is loading / loaded). */
    private val launchAdComing: Boolean
        get() = !Billing.isPremium && (!ready || loading || appOpenLoading || interstitial != null || hasAppOpen)

    private fun isInFront(activity: Activity): Boolean {
        val resumed = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState
            ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ?: true
        return resumed && activity.hasWindowFocus()
    }

    /**
     * Starts the ads SDK straight away when the user's consent is already known from an earlier
     * run (called from App.onCreate), so the first full-screen ad has more time to load.
     */
    fun warmUp(context: Context) {
        try {
            if (UserMessagingPlatform.getConsentInformation(context).canRequestAds()) start(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "warm up: ${e.message}")
        }
    }

    /**
     * App start (after the "All Document Reader" splash) and "Welcome back": waits up to
     * [maxWaitMs] for an ad, then shows — taking turns, like the original — either the
     * "Continue to app ›" ad or the full-screen ad (whichever is ready if only one is), and runs
     * [then] when it closes, or straight away when there is no ad (premium, no internet…).
     */
    fun showOnLaunch(activity: Activity, maxWaitMs: Long, then: () -> Unit) {
        if (Billing.isPremium) { then(); return }
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val until = SystemClock.elapsedRealtime() + maxWaitMs
        val preferAppOpen = launchCount++ % 2 == 1
        if (ready) { loadInterstitial(activity.applicationContext); loadAppOpen(activity.applicationContext) }
        val check = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                // never open the ad window while this screen is still appearing / not in front:
                // that is what makes a full-screen ad come up black with only "Test Ad" on it
                if (!isInFront(activity)) { handler.postDelayed(this, 150); return }
                val wanted = if (preferAppOpen) hasAppOpen else hasInterstitial
                val timeUp = SystemClock.elapsedRealtime() >= until
                when {
                    // the preferred kind is ready, or time is up / nothing more is coming: use what we have
                    wanted || timeUp || !launchAdComing -> when {
                        preferAppOpen && hasAppOpen -> showAppOpen(activity, then)
                        hasInterstitial -> showInterstitial(activity, force = true, then = then)
                        hasAppOpen -> showAppOpen(activity, then)
                        else -> then()
                    }
                    else -> handler.postDelayed(this, 150)
                }
            }
        }
        check.run()
    }

    /**
     * Opening a file category from the home screen: full-screen ad → [open] → the "Get Premium"
     * page on top of it (once per app run, only after an ad was actually shown), like the original.
     */
    fun beforeCategory(activity: Activity, open: () -> Unit) {
        var adShown = false
        // when no ad is shown the callback runs straight away (adShown is still false)
        adShown = showInterstitial(activity) {
            open()
            if (adShown && !premiumOfferedThisRun && !Billing.isPremium && !activity.isFinishing) {
                premiumOfferedThisRun = true
                activity.startActivity(PremiumActivity.intent(activity))
            }
        }
    }

    // ------------------------------------------------------------------ native ads ("Explore more apps")

    private const val NATIVE_MAX_AGE_MS = 50 * 60_000L
    private val natives = ArrayList<NativeAd>()
    private var nativeLoadedAt = 0L
    private var nativeLoading = false
    private val nativeWaiters = ArrayList<(List<NativeAd>) -> Unit>()

    /** Loads up to 5 native ads in the background, for the rows of the thank-you / explore sheet. */
    fun loadNative(context: Context) {
        if (!enabled || nativeLoading) return
        if (natives.isNotEmpty() && SystemClock.elapsedRealtime() - nativeLoadedAt < NATIVE_MAX_AGE_MS) return
        nativeLoading = true
        val fresh = ArrayList<NativeAd>()
        lateinit var loader: AdLoader
        loader = AdLoader.Builder(context, AdIds.NATIVE)
            .forNativeAd { ad ->
                fresh += ad
                if (!loader.isLoading) finishNative(fresh)
            }
            .withAdListener(object : com.google.android.gms.ads.AdListener() {
                override fun onAdFailedToLoad(e: LoadAdError) {
                    Log.w(TAG, "native: ${e.message}")
                    if (!loader.isLoading) finishNative(fresh)
                }
            })
            .withNativeAdOptions(NativeAdOptions.Builder().setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_LEFT).build())
            .build()
        loader.loadAds(request(), 5)
    }

    private fun finishNative(fresh: List<NativeAd>) {
        nativeLoading = false
        if (fresh.isNotEmpty()) {
            natives.forEach { it.destroy() }
            natives.clear(); natives += fresh
            nativeLoadedAt = SystemClock.elapsedRealtime()
        }
        val w = ArrayList(nativeWaiters); nativeWaiters.clear()
        w.forEach { it(natives.toList()) }
    }

    /**
     * Gives the native ads to [done]: right away when they are loaded, otherwise once they have
     * loaded (or failed). Empty for premium users. Must be called on the main thread.
     */
    fun withNativeAds(context: Context, done: (List<NativeAd>) -> Unit) {
        if (Billing.isPremium) return done(emptyList())
        if (natives.isNotEmpty()) { loadNative(context); return done(natives.toList()) }
        nativeWaiters += done
        if (ready) {
            loadNative(context)
            if (!nativeLoading) finishNative(emptyList())   // nothing could be requested
        } else if (!started.get()) {
            finishNative(emptyList())   // ads never started (no consent): nothing will come
        }
        // not ready yet but starting: loadNative() runs when the SDK is ready and answers the waiters
    }

    /** Native ads ready to show (empty for premium users or when none loaded). */
    fun nativeAds(context: Context): List<NativeAd> {
        if (!enabled) return emptyList()
        loadNative(context)   // refreshes old ones for next time
        return natives.toList()
    }
}
