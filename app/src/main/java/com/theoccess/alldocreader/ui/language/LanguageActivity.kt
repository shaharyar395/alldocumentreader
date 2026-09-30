package com.theoccess.alldocreader.ui.language

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.theoccess.alldocreader.data.Languages
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.ActivityLanguageBinding
import com.theoccess.alldocreader.ui.onboarding.OnboardingActivity
import com.theoccess.alldocreader.util.dp

class LanguageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLanguageBinding
    private lateinit var adapter: LanguageAdapter
    private val fromSettings by lazy { intent.getBooleanExtra(EXTRA_FROM_SETTINGS, false) }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (fromSettings) {
            binding.btnBack.visibility = View.VISIBLE
            binding.btnBack.setOnClickListener { finish() }
            (binding.tvTitle.layoutParams as FrameLayout.LayoutParams).marginStart = dp(60)
        }

        val current = savedInstanceState?.getString(STATE_SELECTED)
            ?: Languages.match(AppCompatDelegate.getApplicationLocales().toLanguageTags())
        adapter = LanguageAdapter(Languages.all, current)
        binding.rvLanguages.layoutManager = LinearLayoutManager(this)
        binding.rvLanguages.adapter = adapter
        binding.rvLanguages.itemAnimator = null

        binding.btnDone.setOnClickListener { applySelection() }

        if (!fromSettings && savedInstanceState == null) askNotificationPermission()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_SELECTED, adapter.selectedTag)
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun applySelection() {
        val tag = adapter.selectedTag
        val firstRun = !Prefs.languageDone
        Prefs.languageDone = true

        if (firstRun && !fromSettings) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        finish()

        val locales = if (tag == Languages.DEFAULT_TAG) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(tag)
        if (locales.toLanguageTags() != AppCompatDelegate.getApplicationLocales().toLanguageTags()) {
            // Recreates open activities with the new language.
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    companion object {
        private const val EXTRA_FROM_SETTINGS = "from_settings"
        private const val STATE_SELECTED = "selected"

        fun intent(context: Context, fromSettings: Boolean) =
            Intent(context, LanguageActivity::class.java).putExtra(EXTRA_FROM_SETTINGS, fromSettings)
    }
}
