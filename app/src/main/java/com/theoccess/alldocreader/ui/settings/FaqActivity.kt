package com.theoccess.alldocreader.ui.settings

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityFaqBinding
import com.theoccess.alldocreader.databinding.ItemFaqBinding
import com.theoccess.alldocreader.databinding.ItemSettingsSectionBinding
import com.theoccess.alldocreader.util.Links
import com.theoccess.alldocreader.util.toast

/**
 * FAQ: View & Edit / Manage / About App chips that jump to (and follow) their section,
 * questions that open with the answer and a "Was this helpful? YES / NO", and a
 * "Feedback or suggestion" button at the bottom.
 */
class FaqActivity : AppCompatActivity() {

    private class Q(val icon: Int, val question: Int, val answer: Int)

    private lateinit var binding: ActivityFaqBinding
    private val sectionViews = ArrayList<View>()

    private val sections = listOf(
        R.string.faq_view_edit to listOf(
            Q(R.drawable.ic_q_edit, R.string.faq_q_edit, R.string.faq_a_edit),
            Q(R.drawable.ic_q_display, R.string.faq_q_display, R.string.faq_a_display),
            Q(R.drawable.ic_q_copy, R.string.faq_q_copy, R.string.faq_a_copy),
            Q(R.drawable.ic_q_search, R.string.faq_q_search, R.string.faq_a_search)
        ),
        R.string.faq_manage to listOf(
            Q(R.drawable.ic_q_open, R.string.faq_q_open, R.string.faq_a_open),
            Q(R.drawable.ic_q_create, R.string.faq_q_create, R.string.faq_a_create),
            Q(R.drawable.ic_print, R.string.faq_q_print, R.string.faq_a_print)
        ),
        R.string.faq_about_app to listOf(
            Q(R.drawable.ic_st_subscriptions, R.string.faq_q_subscription, R.string.faq_a_subscription),
            Q(R.drawable.ic_q_ads, R.string.faq_q_ads, R.string.faq_a_ads),
            Q(R.drawable.ic_q_slow, R.string.faq_q_slow, R.string.faq_a_slow),
            Q(R.drawable.ic_shield, R.string.faq_q_permissions, R.string.faq_a_permissions)
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFaqBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        binding.btnFeedback.setOnClickListener { startActivity(Intent(this, FeedbackActivity::class.java)) }

        val inflater = LayoutInflater.from(this)
        sections.forEach { (title, questions) ->
            val s = ItemSettingsSectionBinding.inflate(inflater, binding.content, false)
            s.tvSection.setText(title)
            s.tvSection.setPadding(dpx(6), dpx(12), 0, dpx(8))
            binding.content.addView(s.root)
            sectionViews += s.root
            questions.forEach { q -> binding.content.addView(card(inflater, q)) }
        }

        val chips = listOf(binding.chipView, binding.chipManage, binding.chipAbout)
        chips.forEachIndexed { i, chip ->
            chip.setOnClickListener {
                select(i)
                binding.scroll.smoothScrollTo(0, sectionViews[i].top)
            }
        }
        select(0)
        binding.scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            // the chip follows the section at the top of the list
            val i = sectionViews.indexOfLast { it.top <= y + dpx(24) }.coerceAtLeast(0)
            val atEnd = !binding.scroll.canScrollVertically(1)
            select(if (atEnd && y > 0) sectionViews.lastIndex else i)
        }
    }

    private fun select(i: Int) {
        listOf(binding.chipView, binding.chipManage, binding.chipAbout).forEachIndexed { j, c -> c.isSelected = i == j }
    }

    private fun dpx(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun card(inflater: LayoutInflater, q: Q): View {
        val c = ItemFaqBinding.inflate(inflater, binding.content, false)
        c.ivIcon.setImageResource(q.icon)
        c.tvQuestion.setText(q.question)
        c.tvAnswer.text = linkify(getString(q.answer))
        c.tvAnswer.movementMethod = LinkMovementMethod.getInstance()
        c.header.setOnClickListener {
            val open = c.body.visibility != View.VISIBLE
            c.body.visibility = if (open) View.VISIBLE else View.GONE
            c.ivChevron.animate().rotation(if (open) 180f else 0f).setDuration(150).start()
        }
        val vote = { chosen: TextView, other: TextView ->
            if (!chosen.isSelected) {
                chosen.isSelected = true
                other.isSelected = false
                toast(R.string.faq_thanks)
            }
        }
        c.btnYes.setOnClickListener { vote(c.btnYes, c.btnNo) }
        c.btnNo.setOnClickListener {
            vote(c.btnNo, c.btnYes)
        }
        return c.root
    }

    /** Answer HTML; links like app://feedback open the matching screen. */
    private fun linkify(html: String): CharSequence {
        val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        val out = SpannableStringBuilder(spanned)
        out.getSpans(0, out.length, URLSpan::class.java).forEach { span ->
            val start = out.getSpanStart(span)
            val end = out.getSpanEnd(span)
            val target = span.url
            out.removeSpan(span)
            out.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) = openLink(target)
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    private fun openLink(target: String) {
        when (target) {
            "app://feedback" -> startActivity(Intent(this, FeedbackActivity::class.java))
            "app://filemanager" -> Links.fileManager(this)
            "app://premium" -> startActivity(Intent(this, PremiumActivity::class.java))
            "app://terms" -> startActivity(PolicyActivity.intent(this, PolicyActivity.TERMS))
            else -> Links.open(this, target)
        }
    }
}
