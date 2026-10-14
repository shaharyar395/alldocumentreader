package com.theoccess.alldocreader.ui.scan

import com.theoccess.alldocreader.premium.PremiumGate
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.databinding.ActivityConvertResultBinding
import com.theoccess.alldocreader.databinding.ItemResultToolBinding
import com.theoccess.alldocreader.ui.create.ConvertActivity
import com.theoccess.alldocreader.ui.create.ConvertKind
import com.theoccess.alldocreader.ui.directories.StorageBrowserActivity
import com.theoccess.alldocreader.ui.files.FileSheets
import com.theoccess.alldocreader.ui.edit.PdfEditActivity
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.PdfPrint
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Converted successfully!" screen: notebook-style preview, file name with rename,
 * View locally, Open / Share, and Tools (Edit text, Annotate, Add text, PDF to Word, Print PDF, Sign).
 */
class ConvertResultActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    private lateinit var binding: ActivityConvertResultBinding
    private lateinit var doc: DocFile
    private val sparkles = ArrayList<ValueAnimator>()

    private val edited = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        doc = DocFile.from(doc.file)
        loadPreview()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConvertResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val file = File(intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return })
        if (!file.exists()) { toast(R.string.file_not_found); finish(); return }
        doc = DocFile.from(file)
        com.theoccess.alldocreader.data.LibraryStore.addRecent(file.absolutePath)
        // full-screen ad after converting / saving, like the original (closing it shows this page)
        if (savedInstanceState == null) binding.root.post { com.theoccess.alldocreader.ads.Ads.showInterstitial(this) }

        binding.btnBack.setOnClickListener { finish() }
        setupKind()
        binding.tvName.text = doc.name
        binding.btnRename.setOnClickListener {
            FileSheets.showRename(this, doc) { renamed ->
                doc = renamed
                binding.tvName.text = renamed.name
            }
        }
        binding.btnViewLocally.setOnClickListener {
            doc.file.parentFile?.let { startActivity(StorageBrowserActivity.intent(this, it)) }
        }
        binding.btnOpen.setOnClickListener { FileActions.open(this, doc) }
        binding.btnShare.setOnClickListener { FileActions.share(this, doc) }
        // the PDF tools only make sense for a PDF result (PDF to Word gives a .docx)
        if (doc.file.extension.equals("pdf", true)) setupTools() else {
            binding.toolsHeader.visibility = View.GONE
            binding.toolsGrid.visibility = View.GONE
        }
        loadPreview()
        animateIn()
    }

    private fun setupTools() {
        val tools: List<Triple<Int, Int, () -> Unit>> = listOf(
            // same order as the original: Edit text, Annotate, Add text, Sign / PDF to Word, Manage pages, Print PDF
            Triple(R.drawable.ic_rt_edit_text, R.string.edit_text, { annotate(PdfEditActivity.Mode.EDIT_TEXT) }),
            Triple(R.drawable.ic_rt_annotate, R.string.annotate, { annotate(PdfEditActivity.Mode.ANNOTATE) }),
            Triple(R.drawable.ic_rt_add_text, R.string.add_text, { annotate(PdfEditActivity.Mode.ADD_TEXT) }),
            Triple(R.drawable.ic_rt_sign, R.string.sign, { annotate(PdfEditActivity.Mode.SIGN) }),
            Triple(R.drawable.ic_rt_pdf2word, R.string.pdf_to_word, {
                if (PremiumGate.use(this, PremiumGate.Feature.CONVERT))
                    startActivity(ConvertActivity.intent(this, ConvertKind.PDF_TO_WORD, doc.path))
            }),
            Triple(R.drawable.ic_rt_manage_pages, R.string.manage_pages, {
                edited.launch(com.theoccess.alldocreader.ui.pages.PageOrganizerActivity.intent(this, doc.file))
            }),
            Triple(R.drawable.ic_rt_print, R.string.print_pdf, { PdfPrint.print(this, doc.file, doc.name) })
        )
        tools.forEach { (icon, label, action) ->
            val t = ItemResultToolBinding.inflate(LayoutInflater.from(this), binding.toolsGrid, false)
            t.ivIcon.setImageResource(icon)
            t.tvLabel.setText(label)
            t.root.setOnClickListener { action() }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f)
            ).apply { width = 0 }
            binding.toolsGrid.addView(t.root, lp)
        }
    }

    private fun annotate(mode: PdfEditActivity.Mode) {
        if (!PremiumGate.use(this, PremiumGate.Feature.EDIT_SIGN)) return
        edited.launch(PdfEditActivity.intent(this, doc.file, 0, mode))
    }

    /** Merge / Split: "Merged successfully!" and "Are you satisfied with our "Merge PDF" feature?" */
    private fun setupKind() {
        val kind = intent.getStringExtra(EXTRA_KIND) ?: return
        val (success, feature) = when (kind) {
            KIND_MERGE -> R.string.merged_successfully_excl to R.string.tool_merge_pdf
            KIND_SPLIT -> R.string.split_successfully_excl to R.string.tool_split_pdf
            else -> return
        }
        binding.tvSuccess.setText(success)
        val key = "satisfied_answered_$kind"
        if (com.theoccess.alldocreader.data.Prefs.raw.getBoolean(key, false)) return
        binding.feedbackCard.visibility = View.VISIBLE
        binding.tvSatisfied.text = getString(R.string.satisfied_question, getString(feature))
        val answered = {
            com.theoccess.alldocreader.data.Prefs.raw.edit().putBoolean(key, true).apply()
            binding.feedbackCard.visibility = View.GONE
        }
        binding.btnSatisfiedClose.setOnClickListener { binding.feedbackCard.visibility = View.GONE }
        binding.btnNotGood.setOnClickListener {
            answered()
            startActivity(android.content.Intent(this, com.theoccess.alldocreader.ui.settings.FeedbackActivity::class.java))
        }
        binding.btnGood.setOnClickListener {
            answered()
            toast(R.string.thanks_feedback)
        }
    }

    private fun loadPreview() {
        val file = doc.file
        var pages = 0
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                try {
                    if (!file.extension.equals("pdf", true)) {
                        return@withContext com.theoccess.alldocreader.ui.viewer.DocPageSource(
                            com.theoccess.alldocreader.ui.viewer.DocLayout(com.theoccess.alldocreader.ui.viewer.DocxParser.parse(file))
                        ).render(0, 360)
                    }
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        PdfRenderer(pfd).use { r ->
                            pages = r.pageCount
                            if (r.pageCount == 0) null else r.openPage(0).use { p ->
                                val w = 360
                                val h = (w * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    null
                }
            }
            binding.ivPreview.setImageBitmap(bmp)
            binding.tvPageCount.visibility = if (pages > 0) View.VISIBLE else View.GONE
            binding.tvPageCount.text = pages.toString()
        }
    }

    /** Success pill drops in, the notebook pops up and the sparkles twinkle. */
    private fun animateIn() {
        FileRepository.refresh(applicationContext, force = true)
        val pill = binding.tvSuccess
        pill.alpha = 0f
        pill.translationY = -pill.resources.displayMetrics.density * 16
        pill.animate().alpha(1f).translationY(0f).setDuration(300).start()
        pill.postDelayed({ pill.animate().alpha(0f).setDuration(400).start() }, 2600)
        binding.notebook.scaleX = 0.85f
        binding.notebook.scaleY = 0.85f
        binding.notebook.animate().scaleX(1f).scaleY(1f).setDuration(350).start()
        listOf<View>(binding.spark1, binding.spark2, binding.spark3).forEachIndexed { i, v ->
            val a = ObjectAnimator.ofPropertyValuesHolder(
                v,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f, 0f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.4f, 1.1f, 0.4f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.4f, 1.1f, 0.4f)
            ).apply {
                duration = 1100
                startDelay = 180L * i
                repeatCount = 2
            }
            a.start()
            sparkles += a
        }
    }

    override fun onDestroy() {
        sparkles.forEach { it.cancel() }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_KIND = "kind"
        const val KIND_MERGE = "merge"
        const val KIND_SPLIT = "split"

        /** [kind]: [KIND_MERGE] / [KIND_SPLIT] for "Merged / Split successfully!" + the feedback card. */
        fun intent(context: Context, file: File, kind: String? = null) =
            Intent(context, ConvertResultActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_KIND, kind)
    }
}
