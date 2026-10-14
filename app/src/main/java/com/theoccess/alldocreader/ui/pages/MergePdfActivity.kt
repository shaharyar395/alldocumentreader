package com.theoccess.alldocreader.ui.pages

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityMergePdfBinding
import com.theoccess.alldocreader.databinding.ItemPdfPartBinding
import com.theoccess.alldocreader.ui.create.PickFileActivity
import com.theoccess.alldocreader.ui.scan.ConvertResultActivity
import com.theoccess.alldocreader.ui.scan.ProgressPill
import com.theoccess.alldocreader.util.formatSize
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Merge PDF" (after picking PDFs in "Select a file"), like the original app: the chosen PDFs
 * with ⋮⋮ (long press / drag to reorder), "2 pages · 8.7 KB" and ⊖ to remove; the file+ icon
 * adds more; "Merge (n)" (needs two) → "Merging… (x%)" → "Merged successfully!" screen.
 * Back asks "Quit merging?".
 */
class MergePdfActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    companion object {
        private const val EXTRA_PATHS = "paths"

        fun intent(context: Context, paths: List<String>) =
            Intent(context, MergePdfActivity::class.java).putStringArrayListExtra(EXTRA_PATHS, ArrayList(paths))
    }

    private lateinit var b: ActivityMergePdfBinding
    private val files = ArrayList<File>()
    private val pages = HashMap<String, Int>()
    private val adapter = Adapter()
    private var merging = false
    private lateinit var touch: ItemTouchHelper

    /** file+ → "Select a file" with the current PDFs already ticked → the new selection. */
    private val addMore = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val list = r.data?.getStringArrayListExtra(PickFileActivity.EXTRA_RESULT_PATHS)
        if (r.resultCode == Activity.RESULT_OK && list != null) {
            // keep the order the user already arranged, new ones at the end
            val keep = files.filter { it.absolutePath in list }
            val added = list.filter { p -> keep.none { it.absolutePath == p } }.map { File(it) }
            files.clear()
            files += keep + added
            adapter.notifyDataSetChanged()
            loadPageCounts()
            update()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMergePdfBinding.inflate(layoutInflater)
        setContentView(b.root)
        val paths = savedInstanceState?.getStringArrayList(EXTRA_PATHS) ?: intent.getStringArrayListExtra(EXTRA_PATHS).orEmpty()
        files += paths.map { File(it) }.filter { it.exists() }

        b.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = askQuit()
        })
        b.btnAdd.setOnClickListener {
            addMore.launch(PickFileActivity.mergeSelectIntent(this, files.map { it.absolutePath }))
        }
        b.btnMerge.setOnClickListener { merge() }

        b.rvFiles.layoutManager = LinearLayoutManager(this)
        b.rvFiles.adapter = adapter
        touch = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val a = from.bindingAdapterPosition
                val z = to.bindingAdapterPosition
                if (a < 0 || z < 0) return false
                files.add(z, files.removeAt(a))
                adapter.notifyItemMoved(a, z)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) = Unit
            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, state: Int) {
                super.onSelectedChanged(vh, state)
                if (state == ItemTouchHelper.ACTION_STATE_DRAG) vh?.itemView?.apply { elevation = 8f; alpha = 0.95f }
            }
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.elevation = 0f
                vh.itemView.alpha = 1f
            }
        })
        touch.attachToRecyclerView(b.rvFiles)
        loadPageCounts()
        update()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(EXTRA_PATHS, ArrayList(files.map { it.absolutePath }))
    }

    private fun update() {
        val n = files.size
        b.btnMerge.text = getString(R.string.merge_n, n)
        b.btnMerge.isEnabled = n >= 2
        b.btnMerge.alpha = if (n >= 2) 1f else 0.4f
    }

    private fun loadPageCounts() {
        val todo = files.filter { it.absolutePath !in pages }
        if (todo.isEmpty()) return
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) { todo.associate { it.absolutePath to PdfTools.pageCount(it) } }
            pages.putAll(counts)
            adapter.notifyDataSetChanged()
        }
    }

    private fun askQuit() {
        if (merging) return
        ConfirmSheet.show(this, getString(R.string.quit_merging_title), getString(R.string.quit_changes_message), getString(R.string.quit)) { finish() }
    }

    private fun merge() {
        if (merging || files.size < 2) return
        merging = true
        val pill = ProgressPill(this)
        pill.show(getString(R.string.merging_progress, 0))
        val list = files.toList()
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                try {
                    PdfTools.merge(applicationContext, list) { p -> runOnUiThread { pill.show(getString(R.string.merging_progress, p)) } }
                } catch (e: Throwable) {
                    null
                }
            }
            pill.dismiss()
            merging = false
            if (out == null) {
                toast(R.string.merge_failed)
                return@launch
            }
            startActivity(ConvertResultActivity.intent(this@MergePdfActivity, out, ConvertResultActivity.KIND_MERGE))
            finish()
        }
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val v: ItemPdfPartBinding) : RecyclerView.ViewHolder(v.root)

        override fun getItemCount() = files.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPdfPartBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = files[position]
            holder.v.tvName.text = f.name
            val n = pages[f.absolutePath]
            holder.v.tvMeta.text = if (n != null && n >= 0)
                resources.getQuantityString(R.plurals.pages_and_size, n, n, formatSize(f.length()))
            else formatSize(f.length())
            // ⋮⋮ starts dragging right away; a long press anywhere on the row works too
            holder.v.ivHandle.setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_DOWN) touch.startDrag(holder)
                false
            }
            holder.v.btnRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos < 0) return@setOnClickListener
                files.removeAt(pos)
                notifyItemRemoved(pos)
                update()
            }
        }
    }
}
