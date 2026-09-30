package com.theoccess.alldocreader.ui.splash

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.ActivitySplashBinding
import com.theoccess.alldocreader.ui.language.LanguageActivity
import com.theoccess.alldocreader.ui.main.MainActivity
import com.theoccess.alldocreader.ui.onboarding.OnboardingActivity
import com.theoccess.alldocreader.util.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private var progressAnim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Back is ignored while loading, like the original app.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        // Opening the app from its icon always shows the normal "All Document Reader" loading screen.
        // "Welcome back…" is only for coming back to an app that is still open (see App.WelcomeBackTracker).
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        animateIn()
        startProgress()

        lifecycleScope.launch {
            delay(SPLASH_DURATION_MS)
            route()
        }
    }

    private fun animateIn() {
        listOf<View>(binding.ivLogo, binding.tvTitle, binding.tvSubtitle).forEachIndexed { i, v ->
            v.alpha = 0f
            v.translationY = dp(12).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(80L * i).setDuration(450).start()
        }
    }

    /** Small blue pill sliding back and forth on the track. */
    private fun startProgress() {
        binding.progressTrack.post {
            val travel = (binding.progressTrack.width - binding.progressThumb.width).toFloat()
            progressAnim = ObjectAnimator.ofFloat(binding.progressThumb, View.TRANSLATION_X, 0f, travel).apply {
                duration = 750
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }
    }

    private fun route() {
        val next = when {
            !Prefs.languageDone -> LanguageActivity.intent(this, fromSettings = false)
            !Prefs.onboardingDone -> Intent(this, OnboardingActivity::class.java)
            else -> Intent(this, MainActivity::class.java)
        }
        startActivity(next)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        progressAnim?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val SPLASH_DURATION_MS = 2600L
    }
}
