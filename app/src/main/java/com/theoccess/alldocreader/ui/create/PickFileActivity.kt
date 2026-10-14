package com.theoccess.alldocreader.ui.create

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.databinding.ActivityPickFileBinding
import com.theoccess.alldocreader.databinding.ItemPickFileBinding
import com.theoccess.alldocreader.premium.PremiumGate
import com.theoccess.alldocreader.ui.edit.PdfEditActivity
import com.theoccess.alldocreader.ui.files.ListStateHelper
import com.theoccess.alldocreader.ui.scan.ImageToPdfActivity
import com.theoccess.alldocreader.ui.scan.ProgressPill
import com.theoccess.alldocreader.ui.scan.ScanSession
import com.theoccess.alldocreader.ui.viewer.Converters
import com.theoccess.alldocreader.ui.viewer.ImageViewerActivity
import com.theoccess.alldocreader.ui.viewer.ViewerActivity
import com.theoccess.alldocreader.util.PdfPrint
import com.theoccess.alldocreader.util.StorageAccess
import com.theoccess.alldocreader.util.formatSize
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Select a file" for Word/PDF/PPT/Image convert, or PDF/image for edit / pages / print.
 * Images used for edit tools are converted to PDF first.
 */
class PickFileActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    private lateinit var binding: ActivityPickFileBinding
    private lateinit var kind: ConvertKind
    /** What happens with the chosen file: convert, edit, manage pages or print. */
    private var target = TARGET_CONVERT
    private var editMode = 0
    private lateinit var state: ListStateHelper
    private val adapter = PickAdapter { choose(it) }
    private var query = ""
    private var animated = false
    private var busy = false

    private val convert = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) finish()
    }

    private val searchBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeSearch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPickFileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        kind = ConvertKind.entries.getOrElse(intent.getIntExtra(EXTRA_KIND, 0)) { ConvertKind.WORD_TO_PDF }
        target = intent.getIntExtra(EXTRA_TARGET, TARGET_CONVERT)
        editMode = intent.getIntExtra(EXTRA_EDIT_MODE, 0)
        // edit / pages / print list PDFs and images (images → PDF first)
        if (target != TARGET_CONVERT) kind = ConvertKind.PDF_TO_WORD
        // premium tool with today's free tries used up: straight to Get Premium
        feature?.let { if (!PremiumGate.check(this, it)) { finish(); return } }
        when (target) {
        }
        if (target == TARGET_MERGE) {
            adapter.multi = true
            intent.getStringArrayListExtra(EXTRA_PRESELECTED)?.let { adapter.picked += it }
            savedInstanceState?.getStringArrayList(STATE_PICKED)?.let { adapter.picked.clear(); adapter.picked += it }
            binding.btnAction.visibility = View.VISIBLE
            binding.btnAction.setOnClickListener { continueMerge() }
            updateMergeButton()
        }
        state = ListStateHelper(binding.state)
        onBackPressedDispatcher.addCallback(this, searchBack)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSearch.setOnClickListener { openSearch() }
        binding.btnCancelSearch.setOnClickListener { closeSearch() }
        binding.etSearch.doAfterTextChanged {
            query = it?.toString().orEmpty().trim()
            render()
        }

        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter

        FileRepository.state.observe(this) { render() }
        if (StorageAccess.has(this)) FileRepository.refresh(this) else binding.state.tvEmpty.setText(R.string.permission_needed_hint)
    }

    private fun openSearch() {
        binding.titleBar.visibility = View.GONE
        binding.searchBar.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        searchBack.isEnabled = true
    }

    private fun closeSearch() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.etSearch.setText("")
        binding.searchBar.visibility = View.GONE
        binding.titleBar.visibility = View.VISIBLE
        searchBack.isEnabled = false
    }

    private fun isImage(doc: DocFile) =
        doc.type == FileType.IMAGE || ImageViewerActivity.canOpen(doc.name)

    private fun render() {
        val s = FileRepository.current
        if (!s.loaded) {
            if (StorageAccess.has(this)) state.showLoading() else state.showContent(true)
            return
        }
        val files = s.all()
            .filter {
                when (target) {
                    TARGET_CONVERT -> kind.matches(it)
                    TARGET_MERGE, TARGET_SPLIT -> it.type == FileType.PDF
                    else -> it.type == FileType.PDF || isImage(it)
                }
            }
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .sortedByDescending { it.modified }
        adapter.submitList(files)
        state.showContent(files.isEmpty())
        if (!animated && files.isNotEmpty()) {
            animated = true
            binding.rvFiles.scheduleLayoutAnimation()
        }
    }

    /** The premium feature behind this list (null = free, e.g. Print). */
    private val feature: PremiumGate.Feature?
        get() = when (target) {
            TARGET_CONVERT -> PremiumGate.Feature.CONVERT
            TARGET_EDIT -> PremiumGate.Feature.EDIT_SIGN
            TARGET_PAGES -> PremiumGate.Feature.PAGES
            TARGET_MERGE, TARGET_SPLIT -> PremiumGate.Feature.MERGE_SPLIT
            else -> null
        }

    private fun choose(doc: DocFile) {
        if (busy) return
        if (target == TARGET_MERGE) { togglePick(doc); return }
        if (target == TARGET_SPLIT) { askSplit(doc); return }
        if (target == TARGET_CONVERT && !kind.canConvert(doc)) {
            toast(R.string.convert_old_format)
            return
        }
        // a free try is counted now (unlimited with Premium); none left → Get Premium
        feature?.let { if (!PremiumGate.use(this, it)) return }
        com.theoccess.alldocreader.data.LibraryStore.addRecent(doc.path)
        when (target) {
            TARGET_CONVERT -> {
                if (kind == ConvertKind.IMAGE_TO_PDF) {
                    ScanSession.clear(this)
                    ScanSession.scanMode = false
                    ScanSession.pages.add(ScanSession.newPage(doc.file, fromCamera = false))
                    convert.launch(Intent(this, ImageToPdfActivity::class.java))
                } else {
                    convert.launch(ConvertActivity.intent(this, kind, doc.path))
                }
            }
            TARGET_EDIT, TARGET_PAGES, TARGET_PRINT -> {
                if (doc.type == FileType.PDF) {
                    usePdf(doc.file)
                } else if (isImage(doc)) {
                    convertImageThen(doc) { pdf -> usePdf(pdf) }
                } else {
                    toast(R.string.convert_old_format)
                }
            }
        }
    }

    private fun usePdf(pdf: File) {
        when (target) {
            TARGET_EDIT -> {
                val mode = PdfEditActivity.Mode.entries.getOrElse(editMode) { PdfEditActivity.Mode.NONE }
                startActivities(
                    arrayOf(
                        ViewerActivity.intent(this, pdf),
                        PdfEditActivity.intent(this, pdf, 0, mode)
                    )
                )
                finish()
            }
            TARGET_PAGES -> {
                startActivity(com.theoccess.alldocreader.ui.pages.PageOrganizerActivity.intent(this, pdf, openAfterSave = true))
                finish()
            }
            TARGET_PRINT -> PdfPrint.print(this, pdf, pdf.name)
            else -> Unit
        }
    }

    /** Image → PDF, then run edit / pages / print on the new file. */
    private fun convertImageThen(doc: DocFile, onReady: (File) -> Unit) {
        busy = true
        val pill = ProgressPill(this)
        pill.show(getString(R.string.converting_progress, 0))
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                try {
                    Converters.imageToPdf(applicationContext, doc.file, doc.file.nameWithoutExtension) { p ->
                        runOnUiThread { pill.show(getString(R.string.converting_progress, p)) }
                    }.also {
                        com.theoccess.alldocreader.data.SavedFiles.onSaved(applicationContext, it)
                    }
                } catch (_: Throwable) {
                    null
                }
            }
            pill.dismiss()
            busy = false
            if (out == null) {
                toast(R.string.convert_failed)
                return@launch
            }
            onReady(out)
        }
    }

    // ------------------------------------------------------------------ Merge PDF

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (target == TARGET_MERGE) outState.putStringArrayList(STATE_PICKED, ArrayList(adapter.picked))
    }

    private fun togglePick(doc: DocFile) {
        adapter.toggle(doc.path)
        updateMergeButton()
    }

    /** "Select a file" → "2 selected"; "Continue" → "Continue (2)" (two PDFs are needed). */
    private fun updateMergeButton() {
        val n = adapter.picked.size
        binding.tvTitle.text = if (n == 0) getString(R.string.select_a_file) else getString(R.string.selected_count, n)
        binding.btnAction.isEnabled = n >= 2
        binding.btnAction.alpha = if (n >= 2) 1f else 0.4f
        binding.btnAction.text = if (n == 0) getString(R.string.continue_label) else getString(R.string.continue_n, n)
    }

    private fun continueMerge() {
        val paths = adapter.picked.filter { File(it).exists() }
        if (paths.size < 2) return
        if (intent.getBooleanExtra(EXTRA_FOR_RESULT, false)) {
            setResult(Activity.RESULT_OK, Intent().putStringArrayListExtra(EXTRA_RESULT_PATHS, ArrayList(paths)))
        } else {
            if (!PremiumGate.use(this, PremiumGate.Feature.MERGE_SPLIT)) return
            startActivity(com.theoccess.alldocreader.ui.pages.MergePdfActivity.intent(this, paths))
        }
        finish()
    }

    // ------------------------------------------------------------------ Split PDF

    private fun askSplit(doc: DocFile) {
        if (!PremiumGate.use(this, PremiumGate.Feature.MERGE_SPLIT)) return
        com.theoccess.alldocreader.data.LibraryStore.addRecent(doc.path)
        startActivity(com.theoccess.alldocreader.ui.pages.SplitPdfActivity.intent(this, doc.file))
    }

    private class PickAdapter(val onClick: (DocFile) -> Unit) : ListAdapter<DocFile, PickAdapter.VH>(DIFF) {
        private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)

        /** Merge PDF: several files can be picked (checkbox on every row, in the order they are ticked). */
        var multi = false
        val picked = ArrayList<String>()

        fun toggle(path: String) {
            if (!picked.remove(path)) picked += path
            notifyItemRangeChanged(0, itemCount)
        }

        class VH(val b: ItemPickFileBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPickFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val doc = getItem(position)
            holder.b.ivIcon.setImageResource(doc.type.icon)
            holder.b.tvName.text = doc.name
            holder.b.tvMeta.text = "${dateFormat.format(Date(doc.modified))} · ${formatSize(doc.size)}"
            holder.b.root.setOnClickListener { onClick(doc) }
            holder.b.cbPick.visibility = if (multi) View.VISIBLE else View.GONE
            holder.b.cbPick.isChecked = doc.path in picked
        }

        companion object {
            val DIFF = object : DiffUtil.ItemCallback<DocFile>() {
                override fun areItemsTheSame(a: DocFile, b: DocFile) = a.path == b.path
                override fun areContentsTheSame(a: DocFile, b: DocFile) = a == b
            }
        }
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_EDIT_MODE = "edit_mode"
        private const val TARGET_CONVERT = 0
        private const val TARGET_EDIT = 1
        private const val TARGET_PAGES = 2
        private const val TARGET_PRINT = 3
        private const val TARGET_MERGE = 4
        private const val TARGET_SPLIT = 5
        private const val EXTRA_PRESELECTED = "preselected"
        private const val EXTRA_FOR_RESULT = "for_result"
        private const val STATE_PICKED = "picked"
        /** Merge's "add files": the whole new selection comes back in this extra. */
        const val EXTRA_RESULT_PATHS = "result_paths"

        fun intent(context: Context, kind: ConvertKind) =
            Intent(context, PickFileActivity::class.java).putExtra(EXTRA_KIND, kind.ordinal)

        /** Tools → Edit text / Annotate / Add text / Sign. */
        fun editIntent(context: Context, mode: PdfEditActivity.Mode) =
            Intent(context, PickFileActivity::class.java)
                .putExtra(EXTRA_TARGET, TARGET_EDIT)
                .putExtra(EXTRA_EDIT_MODE, mode.ordinal)

        fun pagesIntent(context: Context) =
            Intent(context, PickFileActivity::class.java).putExtra(EXTRA_TARGET, TARGET_PAGES)

        /** Tools → Merge PDF: tick PDFs → "Continue (n)" → Merge PDF screen. [preselected]: already ticked (viewer ⋮ → Merge PDF). */
        fun mergeIntent(context: Context, preselected: List<String> = emptyList()) =
            Intent(context, PickFileActivity::class.java)
                .putExtra(EXTRA_TARGET, TARGET_MERGE)
                .putStringArrayListExtra(EXTRA_PRESELECTED, ArrayList(preselected))

        /** Merge PDF's file+ button: the current PDFs ticked; the new selection is returned. */
        fun mergeSelectIntent(context: Context, current: List<String>) =
            mergeIntent(context, current).putExtra(EXTRA_FOR_RESULT, true)

        /** Tools → Split PDF: pick a PDF → Split PDF screen. */
        fun splitIntent(context: Context) =
            Intent(context, PickFileActivity::class.java).putExtra(EXTRA_TARGET, TARGET_SPLIT)

        fun printIntent(context: Context) =
            Intent(context, PickFileActivity::class.java).putExtra(EXTRA_TARGET, TARGET_PRINT)
    }
}
