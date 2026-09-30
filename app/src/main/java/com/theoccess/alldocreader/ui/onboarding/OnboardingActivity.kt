package com.theoccess.alldocreader.ui.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.ActivityOnboardingBinding
import com.theoccess.alldocreader.ui.main.MainActivity
import com.theoccess.alldocreader.util.dp

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val adapter = OnboardingAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, bars.bottom)
            insets
        }

        binding.viewPager.adapter = adapter
        binding.viewPager.offscreenPageLimit = 2
        buildDots()
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                binding.btnNext.setText(if (position == adapter.itemCount - 1) R.string.start else R.string.next)
                adapter.animatePage(binding.viewPager, position)
            }
        })

        binding.btnNext.setOnClickListener {
            val pos = binding.viewPager.currentItem
            if (pos < adapter.itemCount - 1) binding.viewPager.currentItem = pos + 1 else finishOnboarding()
        }
    }

    private fun buildDots() {
        binding.dots.removeAllViews()
        repeat(adapter.itemCount) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(dp(6), dp(6)).apply { marginEnd = dp(8) }
            dot.layoutParams = lp
            dot.setBackgroundResource(R.drawable.bg_dot_inactive)
            binding.dots.addView(dot)
        }
        updateDots(0)
    }

    private fun updateDots(selected: Int) {
        for (i in 0 until binding.dots.childCount) {
            val dot = binding.dots.getChildAt(i)
            val lp = dot.layoutParams as LinearLayout.LayoutParams
            lp.width = if (i == selected) dp(18) else dp(6)
            dot.layoutParams = lp
            dot.setBackgroundResource(if (i == selected) R.drawable.bg_dot_active else R.drawable.bg_dot_inactive)
        }
    }

    private fun finishOnboarding() {
        Prefs.onboardingDone = true
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }
}
