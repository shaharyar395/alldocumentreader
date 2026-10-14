package com.theoccess.alldocreader.premium

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.annotation.StringRes
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.ui.settings.PremiumActivity
import com.theoccess.alldocreader.util.toast
import java.util.Calendar

/**
 * What Premium unlocks — exactly the points on the "Get Premium" page:
 *  • Ad-free experience            → [Billing.isPremium] in Ads (no banners, interstitials, app-open ads)
 *  • Edit & sign PDFs easily       → [Feature.EDIT_SIGN]   (Edit text, Annotate, Add text, Sign, viewer ✎)
 *  • Convert files instantly       → [Feature.CONVERT]     (Word/PPT/Image → PDF, PDF → Word, viewer convert)
 *  • Unlock premium templates      → [Feature.TEMPLATES]   (templates with a crown, [isPremiumTemplate])
 *  • Complete page management      → [Feature.PAGES]       (Manage pages: reorder, rotate, extract, delete, insert)
 *  • Merge & split PDFs seamlessly → [Feature.MERGE_SPLIT] (Merge PDF, Split PDF)
 *
 * Free users can try every premium tool [FREE_USES_PER_DAY] times a day; after that the tool
 * opens the Get Premium page. With Premium (or the free trial) everything is unlimited and the
 * crowns disappear. Reading / viewing, scanning, printing, importing stay free for everyone.
 */
object PremiumGate {

    /** Free tries per premium tool per day. */
    const val FREE_USES_PER_DAY = 2

    /**
     * [limited] = free users get only [FREE_USES_PER_DAY] tries a day. Like the original app, the
     * tools (edit & sign, convert, pages, merge & split) are free for everyone — no crown, no
     * limit; only the crowned templates (and the ads) depend on Premium. Set limited = true on a
     * feature to make it a Premium tool again.
     */
    enum class Feature(val key: String, @StringRes val label: Int, val limited: Boolean) {
        EDIT_SIGN("edit_sign", R.string.feat_edit_sign, false),
        CONVERT("convert", R.string.feat_convert, false),
        TEMPLATES("templates", R.string.feat_templates, true),
        PAGES("pages", R.string.feat_pages, false),
        MERGE_SPLIT("merge_split", R.string.feat_merge, false)
    }

    /** Templates with a crown (the second half of each category); the others are free for everyone. */
    private val PREMIUM_TEMPLATES = setOf(
        "resume_dark", "resume_blue",
        "letter_fax", "letter_proposal",
        "briefing_weekly",
        "poster_webinar", "poster_poster_bf", "poster_christmas"
    )

    val isPremium: Boolean get() = Billing.isPremium

    fun isPremiumTemplate(id: String) = id in PREMIUM_TEMPLATES

    /** Crown shown on premium tools / templates for free users only. */
    fun showCrown(crown: View?) {
        crown?.visibility = if (isPremium) View.GONE else View.VISIBLE
    }

    // ------------------------------------------------------------------ free uses

    private fun today(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.YEAR) * 1000 + c.get(Calendar.DAY_OF_YEAR)
    }

    private fun used(f: Feature): Int {
        val p = Prefs.raw
        return if (p.getInt("gate_${f.key}_day", 0) == today()) p.getInt("gate_${f.key}_used", 0) else 0
    }

    /** Free tries left today for [f] (unlimited with Premium). */
    fun remaining(f: Feature): Int =
        if (isPremium || !f.limited) Int.MAX_VALUE else (FREE_USES_PER_DAY - used(f)).coerceAtLeast(0)

    fun canUse(f: Feature): Boolean = isPremium || !f.limited || remaining(f) > 0

    private fun consume(f: Feature) {
        Prefs.raw.edit()
            .putInt("gate_${f.key}_day", today())
            .putInt("gate_${f.key}_used", used(f) + 1)
            .apply()
    }

    // ------------------------------------------------------------------ gates

    /**
     * Before a file list for a premium tool opens: false (and the Get Premium page) when today's
     * free tries are used up. Nothing is counted yet.
     */
    fun check(context: Context, f: Feature): Boolean {
        if (canUse(f)) return true
        openPaywall(context, f)
        return false
    }

    /**
     * The premium tool really starts (file chosen, Convert tapped …): counts one free try for free
     * users and says how many are left; false (and the Get Premium page) when none are left.
     */
    fun use(context: Context, f: Feature): Boolean {
        if (isPremium || !f.limited) return true
        val left = remaining(f)
        if (left <= 0) {
            openPaywall(context, f)
            return false
        }
        consume(f)
        val now = left - 1
        val msg = if (now > 0) context.getString(R.string.premium_free_uses_left, now)
        else context.getString(R.string.premium_last_free_use)
        context.toast(msg)   // a toast: the tool's own screen usually opens right after
        return true
    }

    /** "Today's free uses of … are used up" → Get Premium page. */
    fun openPaywall(context: Context, f: Feature) {
        context.toast(context.getString(R.string.premium_limit_reached, context.getString(f.label)))
        val i = PremiumActivity.intent(context)
        if (context !is Activity) i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
