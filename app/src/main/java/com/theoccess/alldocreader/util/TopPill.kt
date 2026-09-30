package com.theoccess.alldocreader.util

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.theoccess.alldocreader.R

/** Dark rounded message near the top of the screen ("Saved successfully"), like the original app. */
object TopPill {

    private const val TAG = "top_pill"

    fun show(activity: Activity, text: CharSequence, icon: Int = 0, topMarginDp: Int = 64, bgRes: Int = R.drawable.bg_top_pill) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        content.findViewWithTag<View>(TAG)?.let { content.removeView(it) }
        val tv = TextView(activity).apply {
            tag = TAG
            this.text = text
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            this.background = ContextCompat.getDrawable(activity, bgRes)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(activity.dp(if (icon != 0) 12 else 16), 0, activity.dp(16), 0)
            elevation = activity.dp(6).toFloat()
            if (icon != 0) {
                val d = ContextCompat.getDrawable(activity, icon)?.mutate()?.apply {
                    setBounds(0, 0, activity.dp(18), activity.dp(18))
                    setTint(Color.WHITE)
                }
                setCompoundDrawablesRelative(d, null, null, null)
                compoundDrawablePadding = activity.dp(6)
            }
            alpha = 0f
        }
        content.addView(tv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, activity.dp(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL
        ).apply { topMargin = activity.dp(topMarginDp) })
        tv.animate().alpha(1f).setDuration(150).start()
        tv.postDelayed({
            tv.animate().alpha(0f).setDuration(250).withEndAction { content.removeView(tv) }.start()
        }, 1600)
    }
}
