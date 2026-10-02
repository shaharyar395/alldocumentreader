package com.theoccess.alldocreader.ui.main

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.SheetHelpfulBinding
import com.theoccess.alldocreader.ui.settings.FeedbackActivity
import com.theoccess.alldocreader.util.dp

/**
 * "Do you think All Document Reader is helpful?" — Not really / Helpful, like the original.
 *  - Helpful → the 5-star rating sheet (4–5 stars go to Google Play);
 *  - Not really → Feedback ("What problems did you meet?");
 *  - X → asked again later.
 *
 * Shown sometimes when the user comes back to the home screen after saving / converting files
 * (see [shouldShow]), never again once they have answered.
 */
object HelpfulSheet {

    /** First time after the 1st saved / converted file, then after every 3 more, at most once a day. */
    private const val SAVES_FIRST = 1
    private const val SAVES_AGAIN = 3
    private const val MIN_GAP_MS = 24 * 3600_000L

    private var shownThisRun = false

    /** True while the sheet is on screen. */
    var isShowing = false
        private set

    fun shouldShow(): Boolean {
        if (shownThisRun || Prefs.helpfulAnswered) return false
        val need = if (Prefs.helpfulShownAt == 0L) SAVES_FIRST else SAVES_AGAIN
        if (Prefs.savesSinceHelpful < need) return false
        return System.currentTimeMillis() - Prefs.helpfulShownAt >= MIN_GAP_MS
    }

    fun show(context: Context) {
        shownThisRun = true
        Prefs.helpfulShownAt = System.currentTimeMillis()
        Prefs.savesSinceHelpful = 0
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet_Transparent)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        val b = SheetHelpfulBinding.inflate(LayoutInflater.from(context))
        b.tvTitle.text = context.getString(R.string.helpful_title, context.getString(R.string.app_name))

        // the little mascot bobs and the heart beats
        val bob = ObjectAnimator.ofFloat(b.mascotBox, View.TRANSLATION_Y, 0f, -context.dp(6).toFloat()).apply {
            duration = 1200; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }
        val beatX = ObjectAnimator.ofFloat(b.ivHeart, View.SCALE_X, 1f, 1.15f).apply {
            duration = 450; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }
        val beatY = ObjectAnimator.ofFloat(b.ivHeart, View.SCALE_Y, 1f, 1.15f).apply {
            duration = 450; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE; start()
        }

        b.btnClose.setOnClickListener { dialog.dismiss() }
        b.btnHelpful.setOnClickListener {
            Prefs.helpfulAnswered = true
            dialog.dismiss()
            RateSheet.show(context)
        }
        b.btnNotReally.setOnClickListener {
            Prefs.helpfulAnswered = true
            dialog.dismiss()
            context.startActivity(Intent(context, FeedbackActivity::class.java))
        }
        dialog.setOnDismissListener { isShowing = false; bob.cancel(); beatX.cancel(); beatY.cancel() }
        dialog.setContentView(b.root)
        isShowing = true
        dialog.show()
    }
}
