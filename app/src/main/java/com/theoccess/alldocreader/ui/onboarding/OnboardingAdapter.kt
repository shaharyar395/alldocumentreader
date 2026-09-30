package com.theoccess.alldocreader.ui.onboarding

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import androidx.viewpager2.widget.ViewPager2
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ItemMockRowBinding
import com.theoccess.alldocreader.databinding.PageOnboarding1Binding
import com.theoccess.alldocreader.databinding.PageOnboarding2Binding
import com.theoccess.alldocreader.databinding.PageOnboarding3Binding
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.startFloating
import android.content.res.ColorStateList

/** Three illustrated onboarding pages, drawn with real views so they stay crisp on any screen. */
class OnboardingAdapter : RecyclerView.Adapter<OnboardingAdapter.PageVH>() {

    class PageVH(val binding: ViewBinding) : RecyclerView.ViewHolder(binding.root) {
        var floatersStarted = false
    }

    private data class MockRow(val icon: Int, val name: String, val meta: String, val bookmarked: Boolean)

    private val mockRows = listOf(
        MockRow(R.drawable.ic_cat_word, "Project Proposal_2024 Version.docx", "Just now · 3.2MB", true),
        MockRow(R.drawable.ic_cat_pdf, "Research Paper_The Future of AI.pdf", "12 minutes ago · 150KB", true),
        MockRow(R.drawable.ic_cat_excel, "Recent work arrangement.xlsx", "3 hours ago · 3.2MB", false),
        MockRow(R.drawable.ic_cat_ppt, "Product data analysis.ppt", "08/25/2024 · 1.8MB", false),
        MockRow(R.drawable.ic_cat_word, "Annual Financial Report_Q4.docx", "04/30/2024 · 67KB", true),
        MockRow(R.drawable.ic_cat_txt, "Dune battle.txt", "10/28/2023 · 2.5MB", false),
        MockRow(R.drawable.ic_cat_image, "Image file_architecture.img", "09/10/2023 · 450KB", false),
        MockRow(R.drawable.ic_cat_pdf, "Market Analysis Report_Q2.pdf", "07/19/2023 · 4.7MB", true),
        MockRow(R.drawable.ic_cat_word, "Training Plan_H2 2023.pptx", "05/05/2023 · 600KB", false)
    )

    override fun getItemCount() = 3
    override fun getItemViewType(position: Int) = position

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val inf = LayoutInflater.from(parent.context)
        val b: ViewBinding = when (viewType) {
            0 -> PageOnboarding1Binding.inflate(inf, parent, false).also { fillMockList(it) }
            1 -> PageOnboarding2Binding.inflate(inf, parent, false)
            else -> PageOnboarding3Binding.inflate(inf, parent, false).also { styleColorGlyph(it) }
        }
        b.root.findViewById<View>(R.id.screen)?.clipToOutline = true
        return PageVH(b)
    }

    override fun onBindViewHolder(holder: PageVH, position: Int) {
        if (holder.floatersStarted) return
        holder.floatersStarted = true
        val ctx = holder.itemView.context
        val d = ctx.dp(8).toFloat()
        when (val b = holder.binding) {
            is PageOnboarding1Binding -> {
                b.tileWord.startFloating(d, 1700)
                b.tilePdf.startFloating(d, 1500, 300)
                b.tilePpt.startFloating(d, 1900, 150)
                b.tileExcel.startFloating(d, 1600, 450)
            }
            is PageOnboarding2Binding -> {
                b.tileText.startFloating(d, 1600)
                b.penButton.startFloating(d, 1800, 250)
                b.formatBar.startFloating(d * 0.6f, 2000, 100)
            }
            is PageOnboarding3Binding -> {
                b.chipResume.startFloating(d * 0.7f, 1700)
                b.formatBar.startFloating(d * 0.6f, 2100, 200)
            }
        }
    }

    private fun fillMockList(b: PageOnboarding1Binding) {
        val inf = LayoutInflater.from(b.root.context)
        mockRows.forEach { row ->
            val r = ItemMockRowBinding.inflate(inf, b.mockRows, false)
            r.ivIcon.setImageResource(row.icon)
            r.tvName.text = row.name
            r.tvMeta.text = row.meta
            if (row.bookmarked) {
                r.ivBookmark.setImageResource(R.drawable.ic_bookmark)
                ImageViewCompat.setImageTintList(
                    r.ivBookmark,
                    ColorStateList.valueOf(ContextCompat.getColor(b.root.context, R.color.bookmark_yellow))
                )
            }
            b.mockRows.addView(r.root)
        }
    }

    /** The last "A" in the resume toolbar gets a red underline like a text-color picker. */
    private fun styleColorGlyph(b: PageOnboarding3Binding) {
        val ctx = b.root.context
        val bar = GradientDrawable().apply {
            setColor(0xFFE53935.toInt())
            cornerRadius = ctx.dp(1).toFloat()
            setBounds(0, 0, ctx.dp(16), ctx.dp(3))
        }
        b.glyphColor.setCompoundDrawables(null, null, null, bar)
        b.glyphColor.compoundDrawablePadding = -ctx.dp(8)
    }

    /** Small entrance animation when a page becomes visible. */
    fun animatePage(pager: ViewPager2, position: Int) {
        val rv = pager.getChildAt(0) as? RecyclerView ?: return
        val holder = rv.findViewHolderForAdapterPosition(position) as? PageVH ?: return
        val phone = holder.itemView.findViewById<View>(R.id.phone) ?: return
        val title = holder.itemView.findViewById<View>(R.id.tvTitle)
        phone.alpha = 0.4f
        phone.scaleX = 0.94f
        phone.scaleY = 0.94f
        phone.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(450).start()
        title?.let {
            it.alpha = 0f
            it.translationY = it.context.dp(16).toFloat()
            it.animate().alpha(1f).translationY(0f).setDuration(400).setStartDelay(120).start()
        }
    }
}
