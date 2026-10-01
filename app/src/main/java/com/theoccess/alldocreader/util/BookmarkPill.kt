package com.theoccess.alldocreader.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.ui.main.MainActivity

/**
 * Dark rounded message near the bottom of the screen after tapping a bookmark icon, like the
 * original app: "▮ Bookmarked" / "crossed-out bookmark  Bookmark removed".
 */
object BookmarkPill {

    private const val TAG = "bookmark_pill"

    fun show(context: Context, added: Boolean) {
        val activity = context.findActivity()
        val content = activity?.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout
        val text = context.getString(if (added) R.string.bm_pill_added else R.string.bm_pill_removed)
        if (activity == null || content == null || activity.isFinishing) {
            context.toast(text)
            return
        }
        content.findViewWithTag<View>(TAG)?.let { old -> old.animate().cancel(); content.removeView(old) }
        val tv = TextView(activity).apply {
            tag = TAG
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            background = ContextCompat.getDrawable(activity, R.drawable.bg_bm_pill)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(16), 0, activity.dp(20), 0)
            elevation = activity.dp(6).toFloat()
            val icon = ContextCompat.getDrawable(activity, if (added) R.drawable.ic_bm_pill_on else R.drawable.ic_bm_pill_off)
            icon?.setBounds(0, 0, activity.dp(20), activity.dp(20))
            setCompoundDrawablesRelative(icon, null, null, null)
            compoundDrawablePadding = activity.dp(8)
            alpha = 0f
            scaleX = 0.85f
            scaleY = 0.85f
        }
        // above the bottom tabs on the home screen, a little above the bottom edge elsewhere
        val bottom = if (activity is MainActivity) 150 else 100
        content.addView(tv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, activity.dp(46), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        ).apply { bottomMargin = activity.dp(bottom) })
        tv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).setInterpolator(OvershootInterpolator(1.4f)).start()
        tv.postDelayed({
            tv.animate().alpha(0f).setDuration(220).withEndAction { content.removeView(tv) }.start()
        }, 1500)
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }
}
