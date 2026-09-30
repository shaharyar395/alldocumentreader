package com.theoccess.alldocreader.ui.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import com.theoccess.alldocreader.BuildConfig
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityFeedbackBinding
import com.theoccess.alldocreader.util.Links
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.toast

/**
 * "Feedback or suggestion": star rating, "What problems did you encounter?" chips, details,
 * up to 4 screenshots, Submit (sends an e-mail to the support address with everything attached).
 */
class FeedbackActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFeedbackBinding
    private var rating = 0
    private val stars = ArrayList<ImageView>()
    private val shots = ArrayList<Uri>()
    private val problems = listOf(
        R.string.fb_cant_open, R.string.fb_too_many_ads, R.string.fb_slow, R.string.fb_crashes, R.string.fb_others
    )

    private val pickShots = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_SHOTS)) { uris ->
        uris.forEach { if (shots.size < MAX_SHOTS && it !in shots) shots += it }
        refreshShots()
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }

        for (i in 1..5) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginStart = dp(8); marginEnd = dp(8) }
                setPadding(dp(4), dp(4), dp(4), dp(4))
                setOnClickListener { rating = i; refresh() }
            }
            stars += iv
            binding.stars.addView(iv)
        }
        problems.forEach { label ->
            val chip = Chip(this).apply {
                setText(label)
                isCheckable = true
                isCheckedIconVisible = false
                chipBackgroundColor = ContextCompat.getColorStateList(this@FeedbackActivity, R.color.feedback_chip_bg)
                setTextColor(ContextCompat.getColorStateList(this@FeedbackActivity, R.color.feedback_chip_text))
                chipStrokeWidth = 0f
                setOnCheckedChangeListener { _, _ -> refresh() }
            }
            binding.chips.addView(chip)
        }
        binding.etDetails.doAfterTextChanged { refresh() }
        binding.btnAddShot.setOnClickListener {
            if (shots.size >= MAX_SHOTS) toast(R.string.fb_max_shots)
            else pickShots.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        binding.btnSubmit.setOnClickListener { submit() }
        refresh()
    }

    private fun checkedProblems(): List<String> =
        (0 until binding.chips.childCount).mapNotNull { (binding.chips.getChildAt(it) as? Chip)?.takeIf { c -> c.isChecked }?.text?.toString() }

    private fun refresh() {
        stars.forEachIndexed { i, iv ->
            iv.setImageResource(if (i < rating) R.drawable.ic_star_filled else R.drawable.ic_star_empty)
            iv.imageTintList = if (i < rating) null else ColorStateList.valueOf(ContextCompat.getColor(this, R.color.text_hint))
        }
        binding.btnSubmit.isEnabled = rating > 0 || checkedProblems().isNotEmpty() || binding.etDetails.text.isNotBlank()
        binding.btnSubmit.alpha = if (binding.btnSubmit.isEnabled) 1f else 0.45f
    }

    private fun refreshShots() {
        while (binding.shots.childCount > 1) binding.shots.removeViewAt(1)
        shots.forEach { uri ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { marginStart = dp(10) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageURI(uri)
                setOnClickListener { shots.remove(uri); refreshShots() }
            }
            binding.shots.addView(iv)
        }
    }

    private fun submit() {
        val body = buildString {
            if (rating > 0) appendLine(getString(R.string.fb_rating_line, rating))
            val p = checkedProblems()
            if (p.isNotEmpty()) appendLine(getString(R.string.what_problems) + " " + p.joinToString(", "))
            val details = binding.etDetails.text.toString().trim()
            if (details.isNotEmpty()) { appendLine(); appendLine(details) }
            appendLine()
            appendLine("—")
            appendLine("${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}")
            appendLine("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
        }
        val subject = getString(R.string.app_name) + " – " + getString(R.string.feedback_or_suggestion)
        val intent = (if (shots.isEmpty()) {
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("message/rfc822")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(shots))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(Links.SUPPORT_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        try {
            startActivity(if (shots.isEmpty()) intent else Intent.createChooser(intent, getString(R.string.feedback_or_suggestion)))
            toast(R.string.faq_thanks)
            finish()
        } catch (e: ActivityNotFoundException) {
            toast(R.string.no_app_to_open)
        }
    }

    companion object {
        private const val MAX_SHOTS = 4
    }
}
