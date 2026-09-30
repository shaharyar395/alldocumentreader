package com.theoccess.alldocreader.ui.main

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.SheetRateBinding
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.toast

/** "Do you like our app?" rating sheet with 5 tappable stars. */
object RateSheet {

    fun show(context: Context) {
        Prefs.rateShown = true
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet_Transparent)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        val b = SheetRateBinding.inflate(LayoutInflater.from(context))
        @Suppress("DEPRECATION")
        b.tvTitle.text = Html.fromHtml(context.getString(R.string.rate_title))

        var rating = 0
        val glows = mutableListOf<FrameLayout>()
        val stars = mutableListOf<ImageView>()

        fun render(animateUpTo: Int = 0) {
            stars.forEachIndexed { i, star ->
                val filled = i < rating
                star.setImageResource(
                    when {
                        filled -> R.drawable.ic_star_filled
                        i == 4 && rating == 0 -> R.drawable.ic_star_hint
                        else -> R.drawable.ic_star_empty
                    }
                )
                glows[i].setBackgroundResource(if (filled) R.drawable.bg_star_glow else 0)
                if (filled && i < animateUpTo) {
                    star.scaleX = 0.5f
                    star.scaleY = 0.5f
                    star.animate().scaleX(1f).scaleY(1f).setStartDelay(i * 50L)
                        .setInterpolator(OvershootInterpolator(3f)).setDuration(260).start()
                }
            }
            b.btnRate.isEnabled = rating > 0
        }

        val size = context.dp(52)
        repeat(5) { i ->
            val glow = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = context.dp(3); marginEnd = context.dp(3)
                }
                isClickable = true
                isFocusable = true
                contentDescription = "${i + 1}"
            }
            val star = ImageView(context).apply {
                layoutParams = FrameLayout.LayoutParams(context.dp(38), context.dp(38), Gravity.CENTER)
            }
            glow.addView(star)
            glow.setOnClickListener {
                rating = i + 1
                render(animateUpTo = rating)
            }
            b.stars.addView(glow)
            glows += glow
            stars += star
        }
        render()

        // The 5th star gently pulses to invite a 5-star rating.
        val pulse = ObjectAnimator.ofFloat(stars[4], View.ROTATION, -12f, 12f).apply {
            duration = 500
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
        val bob = ObjectAnimator.ofFloat(b.ivMascot, View.TRANSLATION_Y, 0f, -context.dp(6).toFloat()).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        b.btnRate.setOnClickListener {
            dialog.dismiss()
            if (rating >= 4) openStore(context) else context.toast(R.string.rate_thanks)
        }
        dialog.setOnDismissListener {
            pulse.cancel()
            bob.cancel()
        }
        dialog.setContentView(b.root)
        dialog.show()
    }

    private fun openStore(context: Context) {
        val pkg = context.packageName
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
            } catch (ignored: ActivityNotFoundException) {
                context.toast(R.string.rate_thanks)
            }
        }
    }
}
