package com.theoccess.alldocreader.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityMainBinding
import com.theoccess.alldocreader.util.toast

class MainActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastBackPress = 0L
    private var coachMark: CoachMarkOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        shownNight = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        com.theoccess.alldocreader.ads.Ads.init(this)   // consent (EEA / UK) + ads SDK

        setupNav()
        if (savedInstanceState == null) {
            // reopen the tab the user was on last time (All files / Tools / Settings)
            val openFiles = intent.getBooleanExtra(EXTRA_OPEN_BOOKMARKS, false)
            val start = if (openFiles) R.id.nav_files else when (com.theoccess.alldocreader.data.Prefs.lastTab) {
                1 -> R.id.nav_tools
                2 -> R.id.nav_settings
                else -> R.id.nav_files
            }
            if (start == R.id.nav_files) showTab(start) else binding.bottomNav.selectedItemId = start
            prebuildTabs()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    coachMark != null -> dismissCoachMark()
                    binding.bottomNav.selectedItemId != R.id.nav_files ->
                        binding.bottomNav.selectedItemId = R.id.nav_files
                    SystemClock.elapsedRealtime() - lastBackPress < 2000 -> finish()
                    // sometimes, like the original: "Thank you for your support!" before leaving;
                    // closing it closes the app
                    com.theoccess.alldocreader.ui.settings.ExploreAppsSheet.shouldShowOnExit(this@MainActivity) ->
                        com.theoccess.alldocreader.ui.settings.ExploreAppsSheet.show(this@MainActivity) { finish() }
                    else -> {
                        lastBackPress = SystemClock.elapsedRealtime()
                        toast(R.string.press_back_again)
                    }
                }
            }
        })
    }

    private fun setupNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }
    }

    private fun showTab(itemId: Int) {
        val tag = when (itemId) {
            R.id.nav_tools -> TAG_TOOLS
            R.id.nav_settings -> TAG_SETTINGS
            else -> TAG_FILES
        }
        com.theoccess.alldocreader.data.Prefs.lastTab = when (tag) { TAG_TOOLS -> 1; TAG_SETTINGS -> 2; else -> 0 }
        val fm = supportFragmentManager
        if (fm.isStateSaved) return
        // apply any queued tab change first: with fast taps a tab could otherwise be created twice
        // (two screens stacked on each other, slower and slower)
        fm.executePendingTransactions()
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        listOf(TAG_FILES, TAG_TOOLS, TAG_SETTINGS).forEach { t ->
            fm.findFragmentByTag(t)?.let { if (t == tag) tx.show(it) else tx.hide(it) }
        }
        if (fm.findFragmentByTag(tag) == null) tx.add(R.id.fragmentContainer, create(tag), tag)
        tx.commitNow()
    }

    /**
     * Builds the Tools and Settings tabs (hidden) once the first screen is up and the app is idle,
     * so the first tap on them shows them at once instead of building them then.
     */
    private fun prebuildTabs() {
        android.os.Looper.myQueue().addIdleHandler {
            val fm = supportFragmentManager
            if (!isFinishing && !isDestroyed && !fm.isStateSaved) {
                fm.executePendingTransactions()
                val tx = fm.beginTransaction().setReorderingAllowed(true)
                var added = false
                listOf(TAG_TOOLS, TAG_SETTINGS).forEach { t ->
                    if (fm.findFragmentByTag(t) == null) {
                        val f = create(t)
                        tx.add(R.id.fragmentContainer, f, t).hide(f)
                        added = true
                    }
                }
                if (added) tx.commitNow()
            }
            false
        }
    }

    private fun create(tag: String): Fragment = when (tag) {
        TAG_TOOLS -> ToolsFragment()
        TAG_SETTINGS -> SettingsFragment()
        else -> HomeFragment()
    }

    /** Dims the screen except [target] and shows "Click the folders above to view". */
    fun showCoachMark(target: View, onDismiss: () -> Unit) {
        if (coachMark != null || isFinishing) return
        val overlay = CoachMarkOverlay(this, target) {
            coachMark = null
            onDismiss()
        }
        coachMark = overlay
        binding.root.addView(
            overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        overlay.alpha = 0f
        overlay.animate().alpha(1f).setDuration(250).start()
    }

    private fun dismissCoachMark() {
        coachMark?.dismiss()
    }

    companion object {
        /** Home widget "Bookmarks": open All files on the Bookmarks tab. */
        const val EXTRA_OPEN_BOOKMARKS = "open_bookmarks"
        private const val TAG_FILES = "files"
        private const val TAG_TOOLS = "tools"
        private const val TAG_SETTINGS = "settings"
    }

    /**
     * Like the original: sometimes, when the user comes back to the main screen (any tab) after
     * saving / converting files, "Do you think All Document Reader is helpful?".
     */
    /** Day / night of the screen as it is drawn now. */
    private var shownNight = -1

    /**
     * App theme changed (Settings → App theme, or the system's dark mode with "System default").
     * This screen handles uiMode itself (manifest), so Android does not tear it down — that
     * teardown showed a black screen before the new colours. Instead the screen is rebuilt in place
     * at once (see [rebuildForTheme]).
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (shownNight == -1 || night == shownNight) return
        shownNight = night
        if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            // not on screen (another screen is open on top): rebuild quietly in the background
            recreate()
            return
        }
        rebuildForTheme()
    }

    /**
     * Instant theme switch like the original: the same screen is rebuilt in the new colours right
     * away, before anything is drawn — no new screen to open (that took a moment, and meanwhile
     * the old screen showed half-changed colours with text disappearing), no black frame.
     */
    private fun rebuildForTheme() {
        // window and system bar colours of the new theme
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(androidx.core.content.ContextCompat.getColor(this, R.color.bg_screen)))
        @Suppress("DEPRECATION")
        window.statusBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.bg_header_top)
        @Suppress("DEPRECATION")
        window.navigationBarColor = androidx.core.content.ContextCompat.getColor(this, R.color.surface)
        val lightBars = resources.getBoolean(R.bool.light_bars)
        androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
        }
        // the tabs are made again with the new colours; the one on screen right away
        val current = binding.bottomNav.selectedItemId
        coachMark = null
        val fm = supportFragmentManager
        fm.executePendingTransactions()
        val tx = fm.beginTransaction()
        listOf(TAG_FILES, TAG_TOOLS, TAG_SETTINGS).forEach { t -> fm.findFragmentByTag(t)?.let { tx.remove(it) } }
        tx.commitNow()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupNav()
        if (current == R.id.nav_files) showTab(current) else binding.bottomNav.selectedItemId = current
        prebuildTabs()
    }

    override fun onResume() {
        super.onResume()
        if (!com.theoccess.alldocreader.data.Prefs.coachShown || !HelpfulSheet.shouldShow()) return
        binding.root.postDelayed({
            if (!isFinishing && coachMark == null && hasWindowFocus() && HelpfulSheet.shouldShow()) HelpfulSheet.show(this)
        }, 700)
    }
}
