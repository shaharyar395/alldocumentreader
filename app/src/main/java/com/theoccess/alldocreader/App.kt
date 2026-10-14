package com.theoccess.alldocreader

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.data.RecycleBin
import com.theoccess.alldocreader.premium.Billing
import com.theoccess.alldocreader.ui.splash.WelcomeBackActivity

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        com.theoccess.alldocreader.ui.settings.AppTheme.apply()
        LibraryStore.init(this)
        com.theoccess.alldocreader.data.NewFiles.init(this)
        PDFBoxResourceLoader.init(applicationContext)
        Billing.init(this)
        com.theoccess.alldocreader.ads.Ads.warmUp(this)   // ads start loading while the splash shows
        Thread { RecycleBin.purgeExpired(this) }.start()
        registerActivityLifecycleCallbacks(WelcomeBackTracker)
    }

    /**
     * "Welcome back…" + App Open ad: when the user leaves the app (Home / Recents / another app)
     * from any screen and comes back after a while, a short loading screen and the App Open ad
     * are shown, then the same screen is there again. Not when the app itself opened something
     * (Files app, share, sign-in, settings …: see [com.theoccess.alldocreader.ui.BaseActivity]),
     * and not on the loading / language / intro screens.
     */
    internal object WelcomeBackTracker : ActivityLifecycleCallbacks {
        private const val AWAY_MS = 3_000L
        private var started = 0
        private var leftAt = 0L
        private var leftByUser = false
        private var firstActivitySeen = false
        /** Android closed the app while it was in the background and is now bringing a screen back. */
        private var restored = false

        /** Set by every screen when it opens something (see BaseActivity). */
        @Volatile
        var launchedSomething = false

        private fun eligible(activity: Activity) =
            activity is com.theoccess.alldocreader.ui.BaseActivity &&
                activity !is com.theoccess.alldocreader.ui.splash.SplashActivity &&
                activity !is WelcomeBackActivity &&
                activity !is com.theoccess.alldocreader.ui.language.LanguageActivity &&
                activity !is com.theoccess.alldocreader.ui.onboarding.OnboardingActivity

        override fun onActivityStarted(activity: Activity) {
            val away = leftAt > 0 && leftByUser && SystemClock.elapsedRealtime() - leftAt >= AWAY_MS
            val returning = started == 0 && (restored || away) && eligible(activity) &&
                !activity.isFinishing && Prefs.languageDone && Prefs.onboardingDone
            restored = false
            started++
            leftAt = 0
            launchedSomething = false
            if (returning) {
                activity.startActivity(WelcomeBackActivity.intent(activity))
                @Suppress("DEPRECATION")
                activity.overridePendingTransition(0, 0)
            }
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity.isChangingConfigurations) { started--; return }
            started = (started - 1).coerceAtLeast(0)
            if (started == 0) {
                leftAt = SystemClock.elapsedRealtime()
                // left with Home / Recents / another app — not because this app opened something
                leftByUser = !launchedSomething
                launchedSomething = false
                // have the App Open ad ready for "Welcome back…"
                com.theoccess.alldocreader.ads.Ads.onAppBackground(activity.applicationContext)
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            if (firstActivitySeen) return
            firstActivitySeen = true
            // reopened from Recents after Android closed the app in the background: no splash
            // runs, so show "Welcome back…" + the App Open ad like any other return
            restored = savedInstanceState != null && eligible(activity)
        }

        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
