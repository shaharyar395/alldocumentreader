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
    /** True while Google's consent check / form (EEA / UK) is running: the launch ad waits for it. */
    @Volatile
    private var consentBusy = false

    /** True when ads may be requested: not premium, consent handled and the SDK started. */
    val enabled: Boolean get() = ready && !Billing.isPremium

    /**
     * Call from the first screen. Asks Google's consent form when the user is in a region that
     * needs it (EEA / UK), then starts the ads SDK and preloads a full-screen ad.
     */
    fun init(activity: Activity) {
        appContext = activity.applicationContext
        val consent = UserMessagingPlatform.getConsentInformation(activity)
        consentBusy = true
        consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { err ->
                consentBusy = false
                if (err != null) Log.w(TAG, "consent form: ${err.message}")
                if (consent.canRequestAds()) start(activity.applicationContext)
            }
        }, { err ->
            consentBusy = false
            Log.w(TAG, "consent info: ${err.message}")
            if (consent.canRequestAds()) start(activity.applicationContext)
        })
        // consent given in an earlier run: no need to wait for the update
        if (consent.canRequestAds()) start(activity.applicationContext)
    }

    /** Why an ad did not show: Logcat only (tag "Ads"), never on screen. */
    @Suppress("UNUSED_PARAMETER")
    private fun debugNote(context: Context, msg: String) {
        Log.d(TAG, msg)
    }

    private fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        // Premium switched off (expired / restore found nothing): load the ads right away
        main.post {
            Billing.premium.observeForever { premium ->
                if (premium == false && ready) { loadAppOpen(context); loadInterstitial(context) }
            }
        }
        Thread {
            // the App Open ad is requested right away, not after the SDK has finished starting
            // (that can take several seconds on a cold start, so the ad came too late for the
            // loading screen); without mediation Google allows loading before initialization ends
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ loadAppOpen(context) }, 50)
            MobileAds.initialize(context) {
                ready = true
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    loadAppOpen(context)   // first: it is the ad shown at app start / "Welcome back"
                    loadInterstitial(context)
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

    // ------------------------------------------------------------------ App Open ads ("Continue to app ›")

    private const val APP_OPEN_MAX_AGE_MS = 4 * 3600_000L   // Google: app open ads expire after 4 h
    private var appOpen: AppOpenAd? = null
    private var appOpenLoadedAt = 0L
    private var appOpenLoading = false
    private var appOpenFailures = 0
    /** True while an App Open / launch ad is on screen (never two at once). */
    @Volatile
    private var showingLaunchAd = false
    private val main by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }

    private val hasAppOpen: Boolean
        get() {
            if (appOpen != null && SystemClock.elapsedRealtime() - appOpenLoadedAt >= APP_OPEN_MAX_AGE_MS) appOpen = null
            return appOpen != null
        }

    private fun loadAppOpen(context: Context) {
        // started (consent OK) is enough: no need to wait for the SDK's "ready"
        if (!started.get() || Billing.isPremium || appOpenLoading || hasAppOpen) return
        appOpenLoading = true
        val app = context.applicationContext
        AppOpenAd.load(app, AdIds.APP_OPEN, request(), object : AppOpenAd.AppOpenAdLoadCallback() {
            override fun onAdLoaded(ad: AppOpenAd) {
                appOpen = ad; appOpenLoadedAt = SystemClock.elapsedRealtime(); appOpenLoading = false
                appOpenFailures = 0
                Log.d(TAG, "app open: loaded")
            }
            override fun onAdFailedToLoad(e: LoadAdError) {
                appOpen = null; appOpenLoading = false
                if (appOpenFailures == 0) debugNote(app, "App Open ad failed to load (code ${e.code}${if (e.code == 3) " = no ad to show" else ""}): ${e.message}")
                // try again a little later (5 s, 15 s, 45 s …, at most 2 min), so the next
                // app start / "Welcome back" has an ad ready
                val wait = minOf(120_000L, 5_000L * Math.pow(3.0, appOpenFailures.toDouble()).toLong())
                appOpenFailures++
                main.postDelayed({ loadAppOpen(app) }, wait)
            }
        })
    }

    /** The app went to the background: make sure an App Open ad is ready for when the user comes back. */
    fun onAppBackground(context: Context) {
        if (ready) loadAppOpen(context)
    }

    private fun showAppOpen(activity: Activity, then: () -> Unit) {
        val ad = appOpen ?: return then()
        appOpen = null
        showingLaunchAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() { Log.d(TAG, "app open: showing") }
            override fun onAdDismissedFullScreenContent() {
                showingLaunchAd = false
                lastShownAt = SystemClock.elapsedRealtime()
                loadAppOpen(activity.applicationContext)
                then()
            }
            override fun onAdFailedToShowFullScreenContent(e: AdError) {
                showingLaunchAd = false
                debugNote(activity, "App Open ad could not show: ${e.message}")
                loadAppOpen(activity.applicationContext)
                then()
            }
        }
        ad.show(activity)
    }

    private fun isInFront(activity: Activity): Boolean {
        val resumed = (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.currentState
            ?.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) ?: true
        return resumed && activity.hasWindowFocus()
    }

    /**
     * Starts the ads SDK straight away when the user's consent is already known from an earlier
     * run (called from App.onCreate), so the App Open ad has more time to load.
     */
    fun warmUp(context: Context) {
        try {
            if (UserMessagingPlatform.getConsentInformation(context).canRequestAds()) start(context.applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "warm up: ${e.message}")
        }
    }

    /**
     * App start (after the "All Document Reader" splash) and "Welcome back…": shows the App Open
     * ad ("Continue to app ›"), waiting up to [maxWaitMs] for it to load. Only if no App Open ad
     * arrives in time is a ready full-screen ad shown instead. [then] runs once, when the ad
     * closes, or straight away when there is no ad (premium, no internet…).
     */
    fun showOnLaunch(activity: Activity, maxWaitMs: Long, then: () -> Unit) {
        var done = false
        val finish = { if (!done) { done = true; then() } }
        // Premium: no ad and no message about it
        if (Billing.isPremium) { finish(); return }
        if (showingLaunchAd) { finish(); return }
        var until = SystemClock.elapsedRealtime() + maxWaitMs
        var asked = started.get()
        var retried = false
        if (asked) { loadAppOpen(activity.applicationContext); loadInterstitial(activity.applicationContext) }
        val check = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                val now = SystemClock.elapsedRealtime()
                // the consent form is up (or being checked): wait for the user, then for the ad
                if (consentBusy && !ready) { until = now + maxWaitMs; main.postDelayed(this, 150); return }
                val timeUp = now >= until
                // never open the ad window while this screen is still appearing / not in front:
                // that is what makes a full-screen ad come up black with only "Test Ad" on it.
                // Give up after a while so the app never gets stuck on this screen.
                if (!isInFront(activity)) {
                    if (now >= until + 3_000L) { debugNote(activity, "no App Open ad: screen not in front"); finish() } else main.postDelayed(this, 150)
                    return
                }
                if (Billing.isPremium) { finish(); return }
                // SDK started later (consent just given): ask for the ad now
                if (started.get() && !asked) { asked = true; loadAppOpen(activity.applicationContext); loadInterstitial(activity.applicationContext) }
                // the request failed (network hiccup …) and there is still time: try once more now
                if (asked && !retried && !hasAppOpen && !appOpenLoading && !timeUp) {
                    retried = true
                    loadAppOpen(activity.applicationContext)
                }
                val appOpenComing = appOpenLoading || (!ready && !asked)
                when {
                    hasAppOpen -> showAppOpen(activity, finish)
                    !timeUp && appOpenComing -> main.postDelayed(this, 150)
                    // no App Open ad: a ready full-screen ad instead, like the original
                    hasInterstitial -> { showingLaunchAd = true; showInterstitial(activity, force = true) { showingLaunchAd = false; finish() } }
                    !timeUp && loading -> main.postDelayed(this, 150)
                    else -> {
                        debugNote(activity, when {
                            !started.get() -> "no App Open ad: ads not started (consent not given yet)"
                            !ready -> "no App Open ad: ads SDK still starting"
                            else -> "no App Open ad: none loaded in time"
                        })
                        finish()
                    }
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
