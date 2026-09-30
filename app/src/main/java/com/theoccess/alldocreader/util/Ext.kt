package com.theoccess.alldocreader.util

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.text.format.DateUtils
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.annotation.StringRes
import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpF(value: Float): Float = value * resources.displayMetrics.density

fun Context.toast(@StringRes res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()
fun Context.toast(text: CharSequence) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

/** 3.2 MB style sizes. */
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var i = 0
    while (value >= 1024 && i < units.lastIndex) {
        value /= 1024.0
        i++
    }
    return if (value >= 100 || i == 0) String.format(Locale.US, "%.0f %s", value, units[i])
    else String.format(Locale.US, "%.1f %s", value, units[i])
}

/** "Just now", "12 minutes ago", "3 hours ago", otherwise a short date. */
fun formatWhen(time: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - time
    return when {
        diff < DateUtils.MINUTE_IN_MILLIS -> DateUtils.getRelativeTimeSpanString(time, now, DateUtils.SECOND_IN_MILLIS).toString()
        diff < DateUtils.DAY_IN_MILLIS -> DateUtils.getRelativeTimeSpanString(time, now, DateUtils.MINUTE_IN_MILLIS).toString()
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(time))
    }
}

fun formatDateTime(time: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))

/** Gentle up/down floating loop used for illustrations. */
fun View.startFloating(distancePx: Float, duration: Long = 1600L, delay: Long = 0L): ObjectAnimator =
    ObjectAnimator.ofFloat(this, View.TRANSLATION_Y, 0f, -distancePx).apply {
        this.duration = duration
        startDelay = delay
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        start()
    }
