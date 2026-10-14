package com.theoccess.alldocreader.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityPremiumBinding
import com.theoccess.alldocreader.databinding.ItemPremiumSlideBinding
import com.theoccess.alldocreader.premium.Billing
import com.theoccess.alldocreader.premium.PaywallUi
import com.theoccess.alldocreader.util.Links
import com.theoccess.alldocreader.util.TopPill
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.toast

/**
 * "Get Premium" (crown on All files / Tools / Settings, Remove ads banner): sliding feature
 * pictures, benefits, Yearly (free trial) and Monthly plans with prices from Google Play,
 * Continue → Google Play's payment sheet (add a credit / debit card …). If the sheet is
 * closed without buying, the "Start Free Trial" screen is offered once.
 */
class PremiumActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    private lateinit var binding: ActivityPremiumBinding
    private var plan = Billing.Plan.YEARLY
    private var trialOffered = false
    private var buying = false

    private val slides = listOf(
        R.string.remove_ads to R.drawable.ill_prem_ads,
        R.string.premium_slide_sign to R.drawable.ill_prem_sign,
        R.string.premium_slide_convert to R.drawable.ill_prem_convert,
        R.string.premium_slide_templates to R.drawable.ill_prem_templates,
        R.string.premium_slide_pages to R.drawable.ill_prem_pages,
        R.string.premium_slide_merge to R.drawable.ill_prem_merge
    )
    private val auto: Runnable = object : Runnable {
        override fun run() {
            goTo(current + 1)
            binding.heroSlides.removeCallbacks(this)
            binding.heroSlides.postDelayed(this, 3000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        trialOffered = savedInstanceState?.getBoolean(STATE_TRIAL) ?: false

        binding.btnClose.setOnClickListener { finish() }
        // like the original: "Get Ready for Your 7-Day Free Trial" for a moment, then the page
        val showReady = savedInstanceState == null && !Billing.isPremium
        if (showReady) playReady() else binding.readyView.visibility = View.GONE
        PaywallUi.revealClose(binding.btnClose, if (showReady) 3000L + READY_MS else 3000L)
        binding.btnRestore.setOnClickListener { restore() }
        PaywallUi.gradientText(binding.tvGetPremium, 0xFF6FA8FF.toInt(), 0xFFB18CFF.toInt())

        setupSlides()
        setupFeatures()
        setupReviews()
        binding.planYearly.setOnClickListener { select(Billing.Plan.YEARLY) }
        binding.planMonthly.setOnClickListener { select(Billing.Plan.MONTHLY) }
        select(plan)
        binding.btnContinue.setOnClickListener { onContinue() }
        PaywallUi.shine(binding.btnContinue, binding.shine)
        setupLinks()

        Billing.prices.observe(this) { showPrices(it) }
        val premiumAtStart = Billing.isPremium
        Billing.premium.observe(this) { on ->
            updateButton()
            if (on && !premiumAtStart) finish()   // bought here or on "Start Free Trial"
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_TRIAL, trialOffered)
    }

    override fun onResume() {
        super.onResume()
        if (trialOffered) restartSlides()
        binding.heroSlides.removeCallbacks(auto)
        binding.heroSlides.postDelayed(auto, 3000)
    }

    /** Back from "Start Free Trial": the pictures start again from "Remove ads". */
    private fun restartSlides() {
        trialOffered = false
        if (slideViews.isEmpty() || current == 0) return
        slideViews.forEach { it.root.animate().cancel(); it.root.translationX = 0f }
        animating = false
        current = 0
        bindSlide(slideViews[0], 0)
        slideViews[0].root.visibility = View.VISIBLE
        slideViews[1].root.visibility = View.INVISIBLE
        updateDots()
    }

    override fun onPause() {
        binding.heroSlides.removeCallbacks(auto)
        super.onPause()
    }

    // ------------------------------------------------------------------ slides & benefits

    private var current = 0
    private var slideViews: List<ItemPremiumSlideBinding> = emptyList()
    private var animating = false

    /**
     * Hero pictures: two slide views take turns — the next one slides in from the right while the
     * current one leaves to the left (swipe works too), with the dots following.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupSlides() {
        val container = binding.heroSlides
        slideViews = List(2) { ItemPremiumSlideBinding.inflate(layoutInflater, container, false).also { container.addView(it.root) } }
        bindSlide(slideViews[0], 0)
        slideViews[1].root.visibility = View.INVISIBLE
        repeat(slides.size) {
            binding.dots.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(4), dp(4)).apply { marginEnd = dp(5) }
                background = ContextCompat.getDrawable(this@PremiumActivity, R.drawable.bg_dot_paywall)
            })
        }
        updateDots()
        val detector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: android.view.MotionEvent) = true
            override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
                if (kotlin.math.abs(vx) < kotlin.math.abs(vy)) return false
                goTo(if (vx < 0) current + 1 else current - 1, forward = vx < 0)
                return true
            }
        })
        container.setOnTouchListener { _, e -> detector.onTouchEvent(e) }
    }

    private fun bindSlide(b: ItemPremiumSlideBinding, index: Int) {
        b.tvTitle.setText(slides[index].first)
        b.ivArt.setImageResource(slides[index].second)
    }

    private fun goTo(target: Int, forward: Boolean = true) {
        if (animating || slideViews.size < 2) return
        val next = ((target % slides.size) + slides.size) % slides.size
        if (next == current) return
        val w = binding.heroSlides.width.toFloat().takeIf { it > 0 } ?: return
        val out = slideViews[0]
        val incoming = slideViews[1]
        bindSlide(incoming, next)
        incoming.root.translationX = if (forward) w else -w
        incoming.root.visibility = View.VISIBLE
        animating = true
        out.root.animate().translationX(if (forward) -w else w).setDuration(420).start()
        incoming.root.animate().translationX(0f).setDuration(420).withEndAction {
            out.root.visibility = View.INVISIBLE
            out.root.translationX = 0f
            slideViews = listOf(incoming, out)
            animating = false
        }.start()
        current = next
        updateDots()
        binding.heroSlides.removeCallbacks(auto)
        binding.heroSlides.postDelayed(auto, 3000)
    }

    private fun updateDots() {
        for (i in 0 until binding.dots.childCount) binding.dots.getChildAt(i).apply {
            isSelected = i == current
            layoutParams = layoutParams.apply { width = dp(if (i == current) 14 else 4) }
        }
    }

    private fun setupFeatures() {
        listOf(
            R.drawable.ic_q_ads to R.string.feat_ad_free,
            R.drawable.ic_q_edit to R.string.feat_edit_sign,
            R.drawable.ic_st_terms to R.string.feat_convert,
            R.drawable.ic_st_widget to R.string.feat_templates,
            R.drawable.ic_pages to R.string.feat_pages,
            R.drawable.ic_feat_merge to R.string.feat_merge
        ).forEach { (icon, text) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38))
            }
            row.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                setImageResource(icon)
                ImageViewCompat.setImageTintList(this, android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()))
            })
            row.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) }
                setText(text)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 14f
            })
            row.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18))
                setImageResource(R.drawable.ic_check_blue)
            })
            binding.features.addView(row)
        }
    }

    // ------------------------------------------------------------------ plans

    private fun select(p: Billing.Plan) {
        plan = p
        binding.planYearly.isSelected = p == Billing.Plan.YEARLY
        binding.planMonthly.isSelected = p == Billing.Plan.MONTHLY
        updateButton()
    }

    /** "Start Free Trial →" for the yearly plan with a trial, "Continue →" otherwise. */
    private fun updateButton() {
        val on = Billing.isPremium
        val trial = plan == Billing.Plan.YEARLY && (Billing.prices.value?.get(Billing.Plan.YEARLY)?.trialDays ?: 7) > 0
        binding.tvContinue.setText(
            when {
                on -> R.string.manage_subscriptions
                trial -> R.string.start_free_trial
                else -> R.string.continue_label
            }
        )
        binding.ivContinueArrow.visibility = if (on) View.GONE else View.VISIBLE
        binding.tvCancelAnytime.visibility = if (on) View.GONE else View.VISIBLE
    }

    private fun showPrices(prices: Map<Billing.Plan, Billing.Price>) {
        updateButton()
        val y = prices[Billing.Plan.YEARLY]
        val m = prices[Billing.Plan.MONTHLY]
        if (y != null) {
            binding.tvYearlyTitle.text = if (y.trialDays > 0) getString(R.string.plan_trial_days, y.trialDays) else getString(R.string.plan_yearly)
            binding.tvYearlySub.text = getString(if (y.trialDays > 0) R.string.plan_then_only_per_day else R.string.plan_per_day, y.perDay)
            binding.tvYearlyPrice.text = getString(R.string.plan_price_year, y.formatted)
        } else {
            binding.tvYearlyTitle.text = getString(R.string.plan_trial_days, 7)
            binding.tvYearlySub.text = getString(R.string.plan_then_only_per_day, getString(R.string.fallback_yearly_per_day))
            binding.tvYearlyPrice.text = getString(R.string.plan_price_year, getString(R.string.fallback_yearly_price))
        }
        if (m != null) {
            binding.tvMonthlySub.text = getString(R.string.plan_per_day, m.perDay)
            binding.tvMonthlyPrice.text = getString(R.string.plan_price_month, m.formatted)
        } else {
            binding.tvMonthlySub.text = getString(R.string.plan_per_day, getString(R.string.fallback_monthly_per_day))
            binding.tvMonthlyPrice.text = getString(R.string.plan_price_month, getString(R.string.fallback_monthly_price))
        }
        val save = Billing.savePercent(prices) ?: if (prices.isEmpty()) 67 else null
        binding.tvSave.visibility = if (save != null) View.VISIBLE else View.GONE
        if (save != null) binding.tvSave.text = getString(R.string.plan_save_percent, save)
    }

    // ------------------------------------------------------------------ buy / restore

    private fun onContinue() {
        if (buying) return
        if (Billing.isPremium) { Links.manageSubscriptions(this); return }
        buying = true
        PaywallUi.busy(binding.tvContinue, binding.pbContinue, true)
        PaywallUi.buy(this, plan) { result ->
            buying = false
            PaywallUi.busy(binding.tvContinue, binding.pbContinue, false)
            when (result) {
                Billing.Result.SUCCESS -> { toast(R.string.premium_welcome); finish() }
                // like the original: when Google Play's sheet closes without a purchase,
                // "Start Free Trial" is offered (once); after that a failure shows "Subscribe failed"
                // like the original: whenever Google Play's payment sheet closes without a purchase,
                // the "Start Free Trial" screen comes up; its X brings the user back here
                else -> {
                    val openTrial = {
                        trialOffered = true
                        startActivity(Intent(this, StartTrialActivity::class.java))
                        @Suppress("DEPRECATION")
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                    openTrial()
                }
            }
        }
    }

    private fun restore() {
        Billing.refreshPurchases { active ->
            if (isFinishing) return@refreshPurchases
            if (active) { toast(R.string.premium_restored); finish() }
            else com.theoccess.alldocreader.premium.PaywallUi.noSubscription(this)
        }
    }

    private fun setupLinks() {
        listOf(binding.tvTerms, binding.tvPrivacy).forEach { it.paintFlags = it.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG }
        binding.tvTerms.setOnClickListener { startActivity(PolicyActivity.intent(this, PolicyActivity.TERMS)) }
        binding.tvPrivacy.setOnClickListener { Links.open(this, Links.PRIVACY_POLICY) }
    }

    // ------------------------------------------------------------------ user reviews

    /**
     * "User Reviews" cards under the benefits. NOTE: placeholder texts — before publishing, replace
     * them (strings review1_… review3_…) with real reviews of your app (Google Play policy).
     */
    private fun setupReviews() {
        PaywallUi.gradientText(binding.tvReviews, 0xFF5B8CFF.toInt(), 0xFFA9BCFF.toInt())
        listOf(
            Triple(R.string.review1_title, R.string.review1_name, R.string.review1_text),
            Triple(R.string.review2_title, R.string.review2_name, R.string.review2_text),
            Triple(R.string.review3_title, R.string.review3_name, R.string.review3_text)
        ).forEach { (title, name, text) -> binding.reviews.addView(reviewCard(title, name, text)) }
    }

    private fun reviewCard(title: Int, name: Int, text: Int): View {
        val card = android.widget.FrameLayout(this).apply {
            background = ContextCompat.getDrawable(this@PremiumActivity, R.drawable.bg_review_card)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
        }
        // faint quote mark in the corner
        card.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_quote_big)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, android.widget.FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP or Gravity.END).apply { topMargin = dp(6); marginEnd = dp(10) })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(14))
        }
        col.addView(TextView(this).apply {
            setText(title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val stars = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }
        repeat(5) {
            stars.addView(ImageView(this).apply { setImageResource(R.drawable.ic_star_filled) },
                LinearLayout.LayoutParams(dp(15), dp(15)).apply { marginEnd = dp(3) })
        }
        stars.addView(TextView(this).apply {
            setText(name)
            setTextColor(0xB3FFFFFF.toInt())
            textSize = 12f
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        col.addView(stars)
        col.addView(TextView(this).apply {
            setText(text)
            setTextColor(0x99FFFFFF.toInt())
            textSize = 12.5f
            setLineSpacing(dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        })
        card.addView(col)
        return card
    }

    // ------------------------------------------------------------------ "Get Ready" screen

    /** The red check pops in with a glow and twinkling dots, the text rises in, then the page shows. */
    private fun playReady() {
        val r = binding.readyView
        r.visibility = View.VISIBLE
        r.alpha = 1f
        PaywallUi.gradientText(binding.tvReadyTop, 0xFFFF5A3C.toInt(), 0xFFFFB259.toInt())
        PaywallUi.gradientText(binding.tvReadyBottom, 0xFFFF4B3A.toInt(), 0xFFFFC56E.toInt())
        binding.readyBall.apply {
            scaleX = 0.5f; scaleY = 0.5f; alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(450)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.6f)).start()
        }
        android.animation.ObjectAnimator.ofFloat(binding.readyGlow, View.ALPHA, 0.55f, 1f).apply {
            duration = 700
            repeatCount = android.animation.ValueAnimator.INFINITE
            repeatMode = android.animation.ValueAnimator.REVERSE
            start()
        }
        listOf(binding.spark1, binding.spark2, binding.spark3).forEachIndexed { i, v ->
            android.animation.ObjectAnimator.ofFloat(v, View.ALPHA, 0.15f, 1f).apply {
                duration = 520L + i * 130
                startDelay = i * 160L
                repeatCount = android.animation.ValueAnimator.INFINITE
                repeatMode = android.animation.ValueAnimator.REVERSE
                start()
            }
        }
        listOf(binding.tvReadyTop, binding.tvReadyBottom).forEachIndexed { i, t ->
            t.alpha = 0f
            t.translationY = dp(14).toFloat()
            t.animate().alpha(1f).translationY(0f).setStartDelay(220L + i * 120).setDuration(380).start()
        }
        r.postDelayed({
            if (isFinishing || isDestroyed) return@postDelayed
            r.animate().alpha(0f).setDuration(260).withEndAction { r.visibility = View.GONE }.start()
        }, READY_MS)
    }

    companion object {
        private const val STATE_TRIAL = "trial_offered"
        /** How long "Get Ready for Your 7-Day Free Trial" stays before the page. */
        private const val READY_MS = 1700L
        fun intent(context: Context) = Intent(context, PremiumActivity::class.java)
    }
}
