package com.theoccess.alldocreader.ui.splash

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.databinding.ActivityWelcomeBackBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Welcome back…" with the sliding blue bar. Shown on top of the main screen when the user
 * returns to the app; it closes by itself and the tab they were on is there again.
 */
class WelcomeBackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWelcomeBackBinding
    private var anim: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeBackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        startSlidingBar(binding.progressTrack, binding.progressThumb) { anim = it }
        lifecycleScope.launch {
            delay(DURATION_MS)
            // like the original: "Welcome back…" → full-screen ad → the tab the user was on
            com.theoccess.alldocreader.ads.Ads.showOnLaunch(this@WelcomeBackActivity, AD_WAIT_MS) {
                finish()
                @Suppress("DEPRECATION")
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }

    override fun onDestroy() {
        anim?.cancel()
        super.onDestroy()
    }

    companion object {
        const val DURATION_MS = 1800L
        private const val AD_WAIT_MS = 2500L

        fun intent(context: Context) = Intent(context, WelcomeBackActivity::class.java)

        /** Small blue pill sliding back and forth on the track. */
        fun startSlidingBar(track: View, thumb: View, started: (ObjectAnimator) -> Unit) {
            track.post {
                val travel = (track.width - thumb.width).toFloat()
                started(ObjectAnimator.ofFloat(thumb, View.TRANSLATION_X, 0f, travel).apply {
                    duration = 750
                    repeatMode = ValueAnimator.REVERSE
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                })
            }
        }
    }
}
