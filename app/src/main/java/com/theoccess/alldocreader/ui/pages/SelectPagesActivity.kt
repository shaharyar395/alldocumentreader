package com.theoccess.alldocreader.ui.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivitySelectPagesBinding
import com.theoccess.alldocreader.databinding.ItemPageThumbBinding
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * "Select pages" (Split PDF → Custom range), like the original: the pages of the PDF in two
 * columns with a checkbox each, "N selected", select all, "Continue (n)". Returns the chosen
 * pages (1-based, in page order) in [EXTRA_PAGES].
 */
class SelectPagesActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    companion object {
        private const val EXTRA_PATH = "path"
        const val EXTRA_PAGES = "pages"

        fun intent(context: Context, file: File) =
            Intent(context, SelectPagesActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath)
    }

    private lateinit var b: ActivitySelectPagesBinding
    private var fd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var count = 0
    private var aspect = 1.414f
    private val selected = sortedSetOf<Int>()
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val cache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    private var thumbWidth = 360

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySelectPagesBinding.inflate(layoutInflater)
        setContentView(b.root)
        val file = File(intent.getStringExtra(EXTRA_PATH) ?: "")
        try {
            fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(fd!!)
            count = renderer!!.pageCount
            if (count > 0) renderer!!.openPage(0).use { aspect = it.height / it.width.toFloat() }
        } catch (e: Exception) {
            toast(R.string.pdf_password_protected)
            finish()
            return
        }
        savedInstanceState?.getIntegerArrayList(EXTRA_PAGES)?.let { selected += it }
        thumbWidth = (resources.displayMetrics.widthPixels / 2).coerceAtMost(540)

        b.btnBack.setOnClickListener { finish() }
        b.btnSelectAll.setOnClickListener {
            if (selected.size == count) selected.clear() else selected += (1..count)
            b.rvPages.adapter?.notifyDataSetChanged()
            update()
        }
        b.btnAction.setOnClickListener {
            if (selected.isEmpty()) return@setOnClickListener
            setResult(Activity.RESULT_OK, Intent().putIntegerArrayListExtra(EXTRA_PAGES, ArrayList(selected)))
            finish()
        }
        b.rvPages.layoutManager = GridLayoutManager(this, 2)
        b.rvPages.adapter = Adapter()
        update()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntegerArrayList(EXTRA_PAGES, ArrayList(selected))
    }

    private fun update() {
        val n = selected.size
        b.tvTitle.text = if (n == 0) getString(R.string.select_pages) else getString(R.string.selected_count, n)
        b.btnAction.text = if (n == 0) getString(R.string.continue_label) else getString(R.string.continue_n, n)
        b.btnAction.isEnabled = n > 0
        b.btnAction.alpha = if (n > 0) 1f else 0.4f
    }

    private fun render(index: Int): Bitmap? = synchronized(this) {
        val r = renderer ?: return null
        try {
            r.openPage(index).use { p ->
                val h = (thumbWidth * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(thumbWidth, h, Bitmap.Config.ARGB_8888).also {
                    it.eraseColor(Color.WHITE)
                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        synchronized(this) {
            try { renderer?.close() } catch (ignored: Exception) {}
            try { fd?.close() } catch (ignored: Exception) {}
            renderer = null
        }
        dispatcher.close()
        super.onDestroy()
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        inner class VH(val v: ItemPageThumbBinding) : RecyclerView.ViewHolder(v.root) { var job: Job? = null }

        override fun getItemCount() = count

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(ItemPageThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val page = position + 1
            val v = holder.v
            v.tvNumber.text = page.toString()
            val on = page in selected
            v.cbSelect.isChecked = on
            v.card.isSelected = on
            v.selectedOverlay.visibility = if (on) View.VISIBLE else View.GONE
            v.root.setOnClickListener {
                if (!selected.remove(page)) selected += page
                notifyItemChanged(holder.bindingAdapterPosition)
                update()
            }
            holder.job?.cancel()
            val cached = cache.get(position)
            if (cached != null) {
                v.ivThumb.setImageBitmap(cached)
                v.pbThumb.visibility = View.GONE
                return
            }
            v.ivThumb.setImageDrawable(null)
            v.pbThumb.visibility = View.VISIBLE
            holder.job = lifecycleScope.launch {
                val bmp = withContext(dispatcher) { render(position) }
                if (bmp != null) cache.put(position, bmp)
                if (holder.bindingAdapterPosition == position) {
                    v.ivThumb.setImageBitmap(bmp)
                    v.pbThumb.visibility = View.GONE
                }
            }
        }
    }
}
