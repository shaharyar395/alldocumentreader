package com.theoccess.alldocreader.ui.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityStartTrialBinding
import com.theoccess.alldocreader.premium.Billing
import com.theoccess.alldocreader.premium.PaywallUi
import com.theoccess.alldocreader.util.TopPill
import com.theoccess.alldocreader.util.toast

/**
 * "Start Free Trial": Today (unlock everything) → Get full access → Day 7 (no charge if
 * cancelled 24 h before). Continue opens Google Play for the yearly plan with its free trial;
 * if that does not go through, "Subscribe failed" is shown. X goes back to the paywall.
 */
class StartTrialActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStartTrialBinding
    private var buying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStartTrialBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnClose.setOnClickListener {
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
        PaywallUi.revealClose(binding.btnClose)
        binding.btnRestore.setOnClickListener {
            Billing.refreshPurchases { active ->
                if (isFinishing) return@refreshPurchases
                if (active) { toast(R.string.premium_restored); finishAffinity2() }
                else TopPill.show(this, getString(R.string.no_subscription_found), 0, 28)
            }
        }
        binding.btnContinue.setOnClickListener { buy() }
        PaywallUi.shine(binding.btnContinue, binding.shine)
        Billing.prices.observe(this) { prices ->
            val y = prices[Billing.Plan.YEARLY]
            val days = y?.trialDays?.takeIf { it > 0 } ?: 7
            binding.tvTryTitle.text = getString(R.string.try_days_free, days)
            binding.tvTrySub.text = getString(
                R.string.try_then,
                y?.formatted ?: getString(R.string.fallback_yearly_price),
                y?.perDay ?: getString(R.string.fallback_yearly_per_day)
            )
        }
    }

    private fun buy() {
        if (buying) return
        buying = true
        PaywallUi.busy(binding.tvContinue, binding.pbContinue, true)
        PaywallUi.buy(this, Billing.Plan.YEARLY) { result ->
            buying = false
            PaywallUi.busy(binding.tvContinue, binding.pbContinue, false)
            if (result == Billing.Result.SUCCESS) { toast(R.string.premium_welcome); finishAffinity2() }
            else PaywallUi.subscribeFailed(this)
        }
    }

    /** Closes this screen; the paywall under it closes itself once Premium is active. */
    private fun finishAffinity2() = finish()
}
