package com.theoccess.alldocreader.data

import android.content.Context
import android.content.SharedPreferences

/** Small wrapper around SharedPreferences for app flags. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("doc_reader_prefs", Context.MODE_PRIVATE)
    }

    internal val raw: SharedPreferences get() = sp

    /** User picked a language on first launch. */
    var languageDone: Boolean
        get() = sp.getBoolean("language_done", false)
        set(v) = sp.edit().putBoolean("language_done", v).apply()

    /** Bottom tab the user was on (0 All files, 1 Tools, 2 Settings); reopened on the next launch. */
    var lastTab: Int
        get() = sp.getInt("last_tab", 0)
        set(v) = sp.edit().putInt("last_tab", v).apply()

    /** Active Premium subscription (refreshed from Google Play on every launch). */
    var isPremium: Boolean
        get() = sp.getBoolean("is_premium", false)
        set(v) = sp.edit().putBoolean("is_premium", v).apply()

    var onboardingDone: Boolean
        get() = sp.getBoolean("onboarding_done", false)
        set(v) = sp.edit().putBoolean("onboarding_done", v).apply()

    /** "Click the folders above to view" coach mark was shown. */
    var coachShown: Boolean
        get() = sp.getBoolean("coach_shown", false)
        set(v) = sp.edit().putBoolean("coach_shown", v).apply()

    /** "Discover new feature: Create PDF" tooltip was closed. */
    var tooltipDismissed: Boolean
        get() = sp.getBoolean("tooltip_dismissed", false)
        set(v) = sp.edit().putBoolean("tooltip_dismissed", v).apply()

    /** We already showed the storage permission dialog automatically once. */
    var permissionPrompted: Boolean
        get() = sp.getBoolean("permission_prompted", false)
        set(v) = sp.edit().putBoolean("permission_prompted", v).apply()

    /** Sort direction for file lists (Filter by → Ascending / Descending). */
    var sortAscending: Boolean
        get() = sp.getBoolean("sort_ascending", false)
        set(v) = sp.edit().putBoolean("sort_ascending", v).apply()

    /** Rating sheet already shown once. */
    var rateShown: Boolean
        get() = sp.getBoolean("rate_shown", false)
        set(v) = sp.edit().putBoolean("rate_shown", v).apply()

    /** "Is it helpful?" sheet: answered (Helpful / Not really) → never shown again. */
    var helpfulAnswered: Boolean
        get() = sp.getBoolean("helpful_answered", false)
        set(v) = sp.edit().putBoolean("helpful_answered", v).apply()

    /** When the "Is it helpful?" sheet was last shown (0 = never). */
    var helpfulShownAt: Long
        get() = sp.getLong("helpful_shown_at", 0L)
        set(v) = sp.edit().putLong("helpful_shown_at", v).apply()

    /** Files saved / converted since the "Is it helpful?" sheet was last shown. */
    var savesSinceHelpful: Int
        get() = sp.getInt("saves_since_helpful", 0)
        set(v) = sp.edit().putInt("saves_since_helpful", v).apply()

    /** Set when the user opened a file list; the rating sheet appears after coming back home. */
    var visitedFileList: Boolean
        get() = sp.getBoolean("visited_file_list", false)
        set(v) = sp.edit().putBoolean("visited_file_list", v).apply()

    /** User confirmed the MIUI "Home screen shortcuts" permission screen once. */
    var shortcutPermissionAsked: Boolean
        get() = sp.getBoolean("shortcut_permission_asked", false)
        set(v) = sp.edit().putBoolean("shortcut_permission_asked", v).apply()

    /** Files modified after this time count as new in the "Recently added" badge. */
    var recentSeenAt: Long
        get() = sp.getLong("recent_seen_at", 0L)
        set(v) = sp.edit().putLong("recent_seen_at", v).apply()

    var sortMode: SortMode
        get() = SortMode.entries.getOrElse(sp.getInt("sort_mode", 0)) { SortMode.DATE }
        set(v) = sp.edit().putInt("sort_mode", v.ordinal).apply()
}

enum class SortMode { DATE, NAME, SIZE }
