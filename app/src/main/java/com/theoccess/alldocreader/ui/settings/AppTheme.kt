package com.theoccess.alldocreader.ui.settings

import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Prefs

/** Settings → App theme: System default, Light (default) or Dark. */
enum class AppTheme(@StringRes val label: Int, val nightMode: Int) {
    SYSTEM(R.string.theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
    LIGHT(R.string.theme_light, AppCompatDelegate.MODE_NIGHT_NO),
    DARK(R.string.theme_dark, AppCompatDelegate.MODE_NIGHT_YES);

    companion object {
        private const val KEY = "app_theme"

        var current: AppTheme
            get() = entries.getOrElse(Prefs.raw.getInt(KEY, LIGHT.ordinal)) { LIGHT }
            set(value) {
                Prefs.raw.edit().putInt(KEY, value.ordinal).apply()
                AppCompatDelegate.setDefaultNightMode(value.nightMode)
            }

        /** Call once at start-up. */
        fun apply() = AppCompatDelegate.setDefaultNightMode(current.nightMode)
    }
}
