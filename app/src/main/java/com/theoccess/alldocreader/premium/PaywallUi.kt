package com.theoccess.alldocreader.premium

import android.app.Activity
import android.graphics.LinearGradient
import android.graphics.Shader
import android.view.View
import android.widget.TextView
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.util.TopPill

/** Small pieces shared by the paywall and the "Start Free Trial" screen. */
object PaywallUi {

    /** A light streak sweeps across the Continue button every couple of seconds. */
    fun shine(button: android.view.ViewGroup, streak: View) {
        button.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        button.clipToOutline = true
        button.post {
            val from = -streak.width * 2f
            val to = button.width + streak.width.toFloat()
            android.animation.ObjectAnimator.ofFloat(streak, View.TRANSLATION_X, from, to).apply {
                duration = 1100
                startDelay = 400
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.RESTART
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    /** The X shows up after a few seconds, like the original. */
    fun revealClose(close: View, delayMs: Long = 3000) {
        close.postDelayed({
            close.visibility = View.VISIBLE
            close.animate().alpha(1f).setDuration(300).start()
        }, delayMs)
    }

    fun gradientText(tv: TextView, from: Int, to: Int) {
        tv.post {
            val w = tv.paint.measureText(tv.text.toString())
            val start = (tv.width - w) / 2f
            tv.paint.shader = LinearGradient(start, 0f, start + w, 0f, from, to, Shader.TileMode.CLAMP)
            tv.invalidate()
        }
    }

    /**
     * Opens Google Play's payment sheet for [plan]. In debug builds, when Google Play cannot open it
     * (app not installed from Google Play / subscription not active yet), a clearly marked TEST MODE
     * sheet is shown instead so the whole flow can be tried: "Subscribe (test)" → success,
     * back / tap outside → cancelled. Release builds only ever use Google Play.
     */
    fun buy(activity: Activity, plan: Billing.Plan, done: (Billing.Result) -> Unit) {
        Billing.buy(activity, plan) { result ->
            if (result == Billing.Result.FAILED && com.theoccess.alldocreader.BuildConfig.DEBUG && !activity.isFinishing) {
                showTestSheet(activity, plan, done)
            } else done(result)
        }
    }

    private fun showTestSheet(activity: Activity, plan: Billing.Plan, done: (Billing.Result) -> Unit) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(activity)
        val b = com.theoccess.alldocreader.databinding.SheetTestPurchaseBinding.inflate(activity.layoutInflater)
        val app = activity.getString(R.string.app_name)
        val price = Billing.prices.value?.get(plan)
        if (plan == Billing.Plan.YEARLY) {
            b.tvProduct.text = "$app Premium (Yearly)"
            b.tvPrice.text = activity.getString(R.string.plan_price_year, price?.formatted ?: activity.getString(R.string.fallback_yearly_price)) +
                " · " + activity.getString(R.string.plan_trial_days, price?.trialDays?.takeIf { it > 0 } ?: 7)
        } else {
            b.tvProduct.text = "$app Premium (Monthly)"
            b.tvPrice.text = activity.getString(R.string.plan_price_month, price?.formatted ?: activity.getString(R.string.fallback_monthly_price))
        }
        b.tvReason.text = Billing.lastError ?: "Google Play Billing is not available for this build."
        var result = Billing.Result.CANCELED
        b.btnSubscribe.setOnClickListener {
            result = Billing.Result.SUCCESS
            Billing.setTestPremium()
            dialog.dismiss()
        }
        dialog.setContentView(b.root)
        dialog.window?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundResource(android.R.color.transparent)
        dialog.setOnDismissListener { done(result) }
        dialog.show()
    }

    fun subscribeFailed(activity: Activity) =
        TopPill.show(activity, activity.getString(R.string.subscribe_failed), R.drawable.ic_alert_circle, 28, R.drawable.bg_top_pill_warn)

    fun busy(label: View, progress: View, on: Boolean) {
        label.visibility = if (on) View.INVISIBLE else View.VISIBLE
        progress.visibility = if (on) View.VISIBLE else View.GONE
    }
}
