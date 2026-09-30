package com.theoccess.alldocreader.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.theoccess.alldocreader.BuildConfig
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Languages
import com.theoccess.alldocreader.databinding.FragmentSettingsBinding
import com.theoccess.alldocreader.databinding.ItemSettingsRowBinding
import com.theoccess.alldocreader.databinding.ItemSettingsSectionBinding
import com.theoccess.alldocreader.ui.language.LanguageActivity
import com.theoccess.alldocreader.ui.scan.ScanPrefs
import com.theoccess.alldocreader.ui.search.SearchActivity
import com.theoccess.alldocreader.ui.settings.AppTheme
import com.theoccess.alldocreader.ui.settings.ChoiceSheet
import com.theoccess.alldocreader.ui.settings.ExploreAppsSheet
import com.theoccess.alldocreader.ui.settings.FaqActivity
import com.theoccess.alldocreader.ui.settings.FeedbackActivity
import com.theoccess.alldocreader.ui.settings.PolicyActivity
import com.theoccess.alldocreader.ui.settings.PremiumActivity
import com.theoccess.alldocreader.ui.settings.WidgetSheet
import com.theoccess.alldocreader.util.Links
import com.theoccess.alldocreader.util.toast

/**
 * Settings tab: Remove ads banner, File manager, FAQ, Share; General (Scan settings,
 * App theme, Language, Feedback or suggestion); Others (Add widget, Explore more apps,
 * Terms of use, Privacy policy, Manage subscriptions) and the version.
 */
class SettingsFragment : Fragment() {

    private var binding: FragmentSettingsBinding? = null

    /** Google's account / permission screen for "Save to Google Drive". */
    private val driveSignIn = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val act = activity ?: return@registerForActivityResult
        com.theoccess.alldocreader.data.DriveBackup.onSignInResult(act, r.resultCode, r.data)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        com.theoccess.alldocreader.premium.Billing.premium.observe(viewLifecycleOwner) { on ->
            binding?.bannerPremium?.visibility = if (on) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentSettingsBinding.inflate(inflater, container, false)
        binding = b
        val ctx = requireContext()
        b.btnSearch.setOnClickListener { startActivity(Intent(ctx, SearchActivity::class.java)) }
        b.btnPremium.setOnClickListener { startActivity(Intent(ctx, PremiumActivity::class.java)) }
        b.bannerPremium.setOnClickListener { startActivity(Intent(ctx, PremiumActivity::class.java)) }
        // the "Remove ads" banner is not needed once Premium is active
        b.bannerPremium.visibility = if (com.theoccess.alldocreader.premium.Billing.isPremium) View.GONE else View.VISIBLE
        b.tvVersion.text = getString(R.string.settings_version, BuildConfig.VERSION_NAME)
        build()
        return b.root
    }

    override fun onResume() {
        super.onResume()
        build() // subtitles (language, theme, scan) may have changed
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }

    private fun driveLabel(): Int {
        val d = com.theoccess.alldocreader.data.DriveBackup
        return when {
            !d.enabled -> R.string.drive_off
            d.needsSignIn -> R.string.drive_sign_in_again
            else -> R.string.drive_on
        }
    }

    /** "Save to Google Drive": On (sign in with Google, allow Drive) / Off. */
    private fun chooseDrive() {
        val ctx = requireContext()
        val d = com.theoccess.alldocreader.data.DriveBackup
        ChoiceSheet.show(ctx, getString(R.string.save_to_drive),
            listOf(getString(R.string.drive_option_on), getString(R.string.drive_option_off)),
            if (d.enabled) 0 else 1
        ) { which ->
            if (which == 1) { d.turnOff(); build(); return@show }
            val act = activity ?: return@show
            d.turnOn(act, driveSignIn) { ok, error ->
                if (!isAdded) return@turnOn
                if (ok) ctx.toast(R.string.drive_turned_on)
                else if (error != null) ctx.toast(getString(R.string.drive_failed, error))
                build()
            }
        }
    }

    private fun build() {
        val b = binding ?: return
        val ctx = requireContext()
        val inflater = LayoutInflater.from(ctx)
        b.rows.removeAllViews()

        fun section(title: Int) {
            val s = ItemSettingsSectionBinding.inflate(inflater, b.rows, false)
            s.tvSection.setText(title)
            b.rows.addView(s.root)
        }

        fun row(@DrawableRes icon: Int, title: Int, subtitle: String? = null, onClick: () -> Unit) {
            val r = ItemSettingsRowBinding.inflate(inflater, b.rows, false)
            r.ivIcon.setImageResource(icon)
            r.tvTitle.setText(title)
            if (subtitle != null) {
                r.tvSubtitle.text = subtitle
                r.tvSubtitle.visibility = View.VISIBLE
            }
            r.root.setOnClickListener { onClick() }
            b.rows.addView(r.root)
        }

        row(R.drawable.ic_st_file_manager, R.string.file_manager) { Links.fileManager(ctx) }
        row(R.drawable.ic_st_faq, R.string.faq) { startActivity(Intent(ctx, FaqActivity::class.java)) }
        row(R.drawable.ic_share, R.string.share) { Links.shareApp(ctx) }

        section(R.string.section_general)
        row(R.drawable.ic_st_scan, R.string.scan_settings, getString(scanLabel())) { chooseScan() }
        row(R.drawable.ic_st_theme, R.string.app_theme, getString(AppTheme.current.label)) { chooseTheme() }
        row(R.drawable.ic_st_drive, R.string.save_to_drive, getString(driveLabel())) { chooseDrive() }
        row(R.drawable.ic_language, R.string.language, languageName()) {
            startActivity(LanguageActivity.intent(ctx, fromSettings = true))
        }
        row(R.drawable.ic_st_feedback, R.string.feedback_or_suggestion) { startActivity(Intent(ctx, FeedbackActivity::class.java)) }

        section(R.string.section_others)
        row(R.drawable.ic_st_widget, R.string.add_widget) { WidgetSheet.show(ctx) }
        row(R.drawable.ic_st_more_apps, R.string.explore_more_apps) { ExploreAppsSheet.show(ctx) }
        row(R.drawable.ic_st_terms, R.string.terms_of_use) { startActivity(PolicyActivity.intent(ctx, PolicyActivity.TERMS)) }
        row(R.drawable.ic_shield, R.string.privacy_policy) { startActivity(PolicyActivity.intent(ctx, PolicyActivity.PRIVACY)) }
        row(R.drawable.ic_st_subscriptions, R.string.manage_subscriptions, getString(R.string.go_to_google_play)) {
            Links.manageSubscriptions(ctx)
        }
    }

    private fun scanLabel() = when {
        !ScanPrefs.dontAsk -> R.string.ask_every_time
        ScanPrefs.autoCrop -> R.string.auto_crop
        else -> R.string.no_crop
    }

    private fun chooseScan() {
        val current = when {
            !ScanPrefs.dontAsk -> 0
            ScanPrefs.autoCrop -> 1
            else -> 2
        }
        ChoiceSheet.show(
            requireContext(), getString(R.string.scan_settings),
            listOf(getString(R.string.ask_every_time), getString(R.string.auto_crop), getString(R.string.no_crop)), current
        ) { i ->
            ScanPrefs.dontAsk = i != 0
            if (i != 0) ScanPrefs.autoCrop = i == 1
            build()
        }
    }

    private fun chooseTheme() {
        val themes = AppTheme.entries
        ChoiceSheet.show(requireContext(), getString(R.string.app_theme), themes.map { getString(it.label) }, AppTheme.current.ordinal) { i ->
            // setDefaultNightMode only recreates when the effective day/night changes
            // (e.g. Light -> System on a light device), so always refresh the subtitle.
            if (themes[i] != AppTheme.current) AppTheme.current = themes[i]
            build()
        }
    }

    private fun languageName(): String {
        val tag = Languages.match(AppCompatDelegate.getApplicationLocales().toLanguageTags())
        return Languages.all.firstOrNull { it.tag == tag && tag.isNotEmpty() }?.displayName ?: getString(R.string.language_default)
    }
}
