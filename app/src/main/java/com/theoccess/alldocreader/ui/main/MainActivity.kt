package com.theoccess.alldocreader.ui.main

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityMainBinding
import com.theoccess.alldocreader.util.toast

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastBackPress = 0L
    private var coachMark: CoachMarkOverlay? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        com.theoccess.alldocreader.ads.Ads.init(this)   // consent (EEA / UK) + ads SDK

        binding.bottomNav.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
        binding.bottomNav.setOnItemReselectedListener { /* no-op */ }
        if (savedInstanceState == null) {
            // reopen the tab the user was on last time (All files / Tools / Settings)
            val openFiles = intent.getBooleanExtra(EXTRA_OPEN_BOOKMARKS, false)
            val start = if (openFiles) R.id.nav_files else when (com.theoccess.alldocreader.data.Prefs.lastTab) {
                1 -> R.id.nav_tools
                2 -> R.id.nav_settings
                else -> R.id.nav_files
            }
            if (start == R.id.nav_files) showTab(start) else binding.bottomNav.selectedItemId = start
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    coachMark != null -> dismissCoachMark()
                    binding.bottomNav.selectedItemId != R.id.nav_files ->
                        binding.bottomNav.selectedItemId = R.id.nav_files
                    SystemClock.elapsedRealtime() - lastBackPress < 2000 -> finish()
                    else -> {
                        lastBackPress = SystemClock.elapsedRealtime()
                        toast(R.string.press_back_again)
                    }
                }
            }
        })
    }

    /** Every startActivity / activity-result launch from here (and its fragments) ends up in this call. */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        com.theoccess.alldocreader.App.WelcomeBackTracker.mainLaunchedSomething = true
        super.startActivityForResult(intent, requestCode, options)
    }

    /** Google sign-in (Save to Google Drive) goes through here; no "Welcome back" after it. */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startIntentSenderForResult(
        intent: android.content.IntentSender, requestCode: Int, fillInIntent: Intent?,
        flagsMask: Int, flagsValues: Int, extraFlags: Int, options: Bundle?
    ) {
        com.theoccess.alldocreader.App.WelcomeBackTracker.mainLaunchedSomething = true
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
    }

    private fun showTab(itemId: Int) {
        val tag = when (itemId) {
            R.id.nav_tools -> TAG_TOOLS
            R.id.nav_settings -> TAG_SETTINGS
            else -> TAG_FILES
        }
        com.theoccess.alldocreader.data.Prefs.lastTab = when (tag) { TAG_TOOLS -> 1; TAG_SETTINGS -> 2; else -> 0 }
        val fm = supportFragmentManager
        val tx = fm.beginTransaction().setReorderingAllowed(true)
        listOf(TAG_FILES, TAG_TOOLS, TAG_SETTINGS).forEach { t ->
            fm.findFragmentByTag(t)?.let { if (t == tag) tx.show(it) else tx.hide(it) }
        }
        if (fm.findFragmentByTag(tag) == null) tx.add(R.id.fragmentContainer, create(tag), tag)
        tx.commit()
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
}
