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
import com.theoccess.alldocreader.ui.main.MainActivity
import com.theoccess.alldocreader.ui.splash.WelcomeBackActivity

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        com.theoccess.alldocreader.ui.settings.AppTheme.apply()
        LibraryStore.init(this)
        PDFBoxResourceLoader.init(applicationContext)
        Billing.init(this)
        com.theoccess.alldocreader.ads.Ads.warmUp(this)   // ads start loading while the splash shows
        Thread { RecycleBin.purgeExpired(this) }.start()
        registerActivityLifecycleCallbacks(WelcomeBackTracker)
    }

    /**
     * "Welcome back…": when the user leaves the main screen (Home / Recents / another app) and
     * comes back after a while, a short loading screen is shown before the same tab appears again.
     */
    internal object WelcomeBackTracker : ActivityLifecycleCallbacks {
        private const val AWAY_MS = 3_000L
        private var started = 0
        private var leftAt = 0L
        private var leftFromMain = false

        /** Set by MainActivity when it opens another screen (Files app, Play, permission settings, share …). */
        var mainLaunchedSomething = false

        override fun onActivityStarted(activity: Activity) {
            val returning = started == 0 && leftAt > 0 && leftFromMain &&
                SystemClock.elapsedRealtime() - leftAt >= AWAY_MS
            started++
            leftAt = 0
            mainLaunchedSomething = false
            if (returning && activity is MainActivity) {
                activity.startActivity(WelcomeBackActivity.intent(activity))
                activity.overridePendingTransition(0, 0)
            }
        }

        override fun onActivityStopped(activity: Activity) {
            if (activity.isChangingConfigurations) { started--; return }
            started = (started - 1).coerceAtLeast(0)
            if (started == 0) {
                leftAt = SystemClock.elapsedRealtime()
                // not when the main screen itself opened another app (e.g. All files access settings)
                leftFromMain = activity is MainActivity && !mainLaunchedSomething
                mainLaunchedSomething = false
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
    }
}
