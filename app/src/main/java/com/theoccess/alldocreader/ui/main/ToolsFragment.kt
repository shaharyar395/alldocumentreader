package com.theoccess.alldocreader.ui.main

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.FragmentToolsBinding
import com.theoccess.alldocreader.databinding.ItemResultToolBinding
import com.theoccess.alldocreader.databinding.ItemToolsSectionBinding
import com.theoccess.alldocreader.ui.create.ConvertKind
import com.theoccess.alldocreader.ui.create.CreateFilesSheet
import com.theoccess.alldocreader.ui.create.ImportActivity
import com.theoccess.alldocreader.ui.create.PickFileActivity
import com.theoccess.alldocreader.ui.create.TemplatesActivity
import com.theoccess.alldocreader.ui.directories.DirectoriesActivity
import com.theoccess.alldocreader.ui.edit.PdfEditActivity
import com.theoccess.alldocreader.ui.pages.InsertBlankPagesActivity
import com.theoccess.alldocreader.ui.scan.ScanStartActivity
import com.theoccess.alldocreader.ui.search.SearchActivity
import com.theoccess.alldocreader.util.toast

/**
 * Tools tab: Convert & Create (same flows as the "+" sheet), Edit (Edit text, Annotate,
 * Add text, Sign → pick a PDF → editor), Others (Import files, Manage pages, Recycle bin,
 * Print PDF) and "Don't have the feature you want? Tell us".
 */
class ToolsFragment : Fragment() {

    private class Tool(@DrawableRes val icon: Int, @StringRes val label: Int, val needsAccess: Boolean = true, val target: (Context) -> Intent)

    private fun sections(): List<Pair<Int, List<Tool>>> = listOf(
        R.string.section_convert_create to listOf(
            Tool(R.drawable.ic_cv_image2pdf, R.string.image_to_pdf) { PickFileActivity.intent(it, ConvertKind.IMAGE_TO_PDF) },
            Tool(R.drawable.ic_cv_scan, R.string.scan_to_pdf) { ScanStartActivity.intent(it, scan = true) },
            Tool(R.drawable.ic_cv_word2pdf, R.string.word_to_pdf) { PickFileActivity.intent(it, ConvertKind.WORD_TO_PDF) },
            Tool(R.drawable.ic_cv_pdf2word, R.string.pdf_to_word) { PickFileActivity.intent(it, ConvertKind.PDF_TO_WORD) },
            Tool(R.drawable.ic_cv_ppt2pdf, R.string.ppt_to_pdf) { PickFileActivity.intent(it, ConvertKind.PPT_TO_PDF) },
            Tool(R.drawable.ic_create_templates, R.string.tool_templates) { Intent(it, TemplatesActivity::class.java) },
            Tool(R.drawable.ic_create_pdf_badge, R.string.create_pdf) { InsertBlankPagesActivity.createIntent(it) }
        ),
        R.string.section_edit to listOf(
            Tool(R.drawable.ic_rt_edit_text, R.string.edit_text) { PickFileActivity.editIntent(it, PdfEditActivity.Mode.EDIT_TEXT) },
            Tool(R.drawable.ic_rt_annotate, R.string.annotate) { PickFileActivity.editIntent(it, PdfEditActivity.Mode.ANNOTATE) },
            Tool(R.drawable.ic_rt_add_text, R.string.add_text) { PickFileActivity.editIntent(it, PdfEditActivity.Mode.ADD_TEXT) },
            Tool(R.drawable.ic_rt_sign, R.string.sign) { PickFileActivity.editIntent(it, PdfEditActivity.Mode.SIGN) }
        ),
        R.string.section_others to listOf(
            Tool(R.drawable.ic_create_import, R.string.import_files) { Intent(it, ImportActivity::class.java) },
            Tool(R.drawable.ic_rt_manage_pages, R.string.manage_pages) { PickFileActivity.pagesIntent(it) },
            Tool(R.drawable.ic_rt_recycle_bin, R.string.recycle_bin, needsAccess = false) {
                Intent(it, com.theoccess.alldocreader.ui.directories.RecycleBinActivity::class.java)
            },
            Tool(R.drawable.ic_rt_print, R.string.print_pdf) { PickFileActivity.printIntent(it) }
        )
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val b = FragmentToolsBinding.inflate(inflater, container, false)
        val ctx = requireContext()
        sections().forEachIndexed { index, (title, tools) ->
            val s = ItemToolsSectionBinding.inflate(inflater, b.sections, false)
            s.tvSection.setText(title)
            tools.forEach { t ->
                val cell = ItemResultToolBinding.inflate(inflater, s.grid, false)
                cell.ivIcon.setImageResource(t.icon)
                cell.tvLabel.setText(t.label)
                cell.root.setOnClickListener {
                    if (!t.needsAccess || CreateFilesSheet.ensureAccess(ctx)) startActivity(t.target(ctx))
                }
                s.grid.addView(cell.root, GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply { width = 0 })
            }
            // keep a 4-column grid even when the last row is short
            repeat((4 - tools.size % 4) % 4) {
                s.grid.addView(View(ctx), GridLayout.LayoutParams(
                    GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)
                ).apply { width = 0; height = 1 })
            }
            b.sections.addView(s.root, index)
        }
        b.cardFeedback.setOnClickListener {
            startActivity(Intent(ctx, com.theoccess.alldocreader.ui.settings.FeedbackActivity::class.java))
        }
        b.btnSearch.setOnClickListener { startActivity(Intent(ctx, SearchActivity::class.java)) }
        b.btnPremium.setOnClickListener {
            startActivity(Intent(ctx, com.theoccess.alldocreader.ui.settings.PremiumActivity::class.java))
        }
        return b.root
    }
}
