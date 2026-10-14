package com.theoccess.alldocreader.ui.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivitySplitPdfBinding
import com.theoccess.alldocreader.databinding.ItemPdfPartBinding
import com.theoccess.alldocreader.ui.scan.ConvertResultActivity
import com.theoccess.alldocreader.ui.scan.ProgressPill
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Split PDF", like the original app:
 *  • the PDF (notebook preview, pages) and "Split method":
 *     – Custom range → "Select pages" → a part "Split_20261007_1240 ✎ / Page: 1,2" (⊖ remove,
 *       file+ adds another part) → "Split (n)": one PDF per part;
 *     – Fixed range → "Interval settings" [− 1 +] → "Split": a new PDF every N pages;
 *  • "Splitting… (x%)" → ad → "Split successfully!" screen.
 */
class SplitPdfActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    companion object {
        private const val EXTRA_PATH = "path"

        fun intent(context: Context, file: File) =
            Intent(context, SplitPdfActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath)
    }

    private lateinit var b: ActivitySplitPdfBinding
    private lateinit var file: File
    private var pages = 0
    private var custom = true
    private var interval = 1
    private val parts = ArrayList<PdfTools.Part>()
    private val adapter = PartAdapter()
    private var busy = false

    private val selectPages = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val chosen = r.data?.getIntegerArrayListExtra(SelectPagesActivity.EXTRA_PAGES)
        if (r.resultCode == Activity.RESULT_OK && !chosen.isNullOrEmpty()) {
            parts += PdfTools.Part(nextName(), chosen.toList())
            adapter.notifyItemInserted(parts.size - 1)
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplitPdfBinding.inflate(layoutInflater)
        setContentView(b.root)
        file = File(intent.getStringExtra(EXTRA_PATH) ?: "")
        if (!file.exists()) { toast(R.string.file_not_found); finish(); return }

        b.tvFileName.text = file.name
        b.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (busy) return
                if (parts.isEmpty()) finish()
                else ConfirmSheet.show(this@SplitPdfActivity, getString(R.string.quit_splitting_title),
                    getString(R.string.quit_changes_message), getString(R.string.quit)) { finish() }
            }
        })
        b.rowCustom.setOnClickListener { custom = true; refresh() }
        b.rowFixed.setOnClickListener { custom = false; refresh() }
        b.btnMinus.setOnClickListener { if (interval > 1) { interval--; refresh() } }
        b.btnPlus.setOnClickListener { if (interval < pages) { interval++; refresh() } }
        b.btnAdd.setOnClickListener { selectPages.launch(SelectPagesActivity.intent(this, file)) }
        b.btnAction.setOnClickListener { onAction() }
        b.rvParts.layoutManager = LinearLayoutManager(this)
        b.rvParts.adapter = adapter

        lifecycleScope.launch {
            val (count, bmp) = withContext(Dispatchers.IO) { PdfTools.pageCount(file) to PdfTools.firstPage(file, dp(84) * 2) }
            if (count < 1) { toast(R.string.pdf_password_protected); finish(); return@launch }
            pages = count
            b.tvPageCount.text = count.toString()
            b.ivPreview.setImageBitmap(bmp)
            refresh()
        }
        refresh()
    }

    /** "Split_20261007_1240", then "Split_20261007_1240(1)", … */
    private fun nextName(): String {
        val base = PdfTools.stamp("Split")
        var name = base
        var i = 1
        while (parts.any { it.name == name }) name = "$base(${i++})"
        return name
    }

    private fun refresh() {
        val listing = parts.isNotEmpty()
        b.methodView.visibility = if (listing) View.GONE else View.VISIBLE
        b.rvParts.visibility = if (listing) View.VISIBLE else View.GONE
        b.btnAdd.visibility = if (listing) View.VISIBLE else View.GONE
        b.rbCustom.isChecked = custom
        b.rbFixed.isChecked = !custom
        b.intervalRow.visibility = if (custom) View.GONE else View.VISIBLE
        b.tvInterval.text = interval.toString()
        b.btnMinus.isEnabled = interval > 1
        b.btnMinus.alpha = if (interval > 1) 1f else 0.3f
        b.btnPlus.isEnabled = interval < pages
        b.btnPlus.alpha = if (interval < pages) 1f else 0.3f
        b.btnAction.text = when {
            listing -> getString(R.string.split_n, parts.size)
            custom -> getString(R.string.select_pages)
            else -> getString(R.string.split_action)
        }
    }

    private fun onAction() {
        if (busy || pages < 1) return
        when {
            parts.isNotEmpty() -> split(parts.toList())
            custom -> selectPages.launch(SelectPagesActivity.intent(this, file))
            else -> split(PdfTools.fixedParts(pages, interval))
        }
    }

    private fun split(list: List<PdfTools.Part>) {
        busy = true
        val pill = ProgressPill(this)
        pill.show(getString(R.string.splitting_progress, 0))
        lifecycleScope.launch {
            val outs = withContext(Dispatchers.IO) {
                try {
                    PdfTools.split(applicationContext, file, list) { p -> runOnUiThread { pill.show(getString(R.string.splitting_progress, p)) } }
                } catch (e: Throwable) {
                    null
                }
            }
            pill.dismiss()
            busy = false
            if (outs.isNullOrEmpty()) {
                toast(R.string.split_failed)
                return@launch
            }
            if (outs.size > 1) toast(resources.getQuantityString(R.plurals.split_done, outs.size, outs.size))
            // the result screen shows the ad first, like after every conversion
            startActivity(ConvertResultActivity.intent(this@SplitPdfActivity, outs.first(), ConvertResultActivity.KIND_SPLIT))
            finish()
        }
    }

    private fun rename(part: PdfTools.Part, position: Int) {
        val et = EditText(this).apply {
            setText(part.name)
            setSelection(text.length)
            setSingleLine()
        }
        val box = android.widget.FrameLayout(this).apply {
            setPadding(dp(22), dp(8), dp(22), 0)
            addView(et)
        }
        MaterialAlertDialogBuilder(this, R.style.Theme_DocReader_Dialog)
            .setTitle(R.string.rename)
            .setView(box)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                val n = et.text.toString().trim().removeSuffix(".pdf")
                if (n.isNotEmpty()) {
                    part.name = n
                    adapter.notifyItemChanged(position)
                }
            }
            .show()
    }

    private inner class PartAdapter : RecyclerView.Adapter<PartAdapter.VH>() {
        inner class VH(val v: ItemPdfPartBinding) : RecyclerView.ViewHolder(v.root)

        override fun getItemCount() = parts.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPdfPartBinding.inflate(LayoutInflater.from(parent.context), parent, false)).also {
                it.v.ivHandle.visibility = View.GONE
                it.v.btnRename.visibility = View.VISIBLE
            }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val part = parts[position]
            holder.v.tvName.text = part.name
            holder.v.tvMeta.text = getString(R.string.part_pages, PdfTools.pagesLabel(part.pages))
            val renameIt = View.OnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) rename(parts[pos], pos)
            }
            holder.v.btnRename.setOnClickListener(renameIt)
            holder.v.tvName.setOnClickListener(renameIt)
            holder.v.btnRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos < 0) return@setOnClickListener
                parts.removeAt(pos)
                notifyItemRemoved(pos)
                refresh()
            }
        }
    }
}
