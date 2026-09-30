package com.theoccess.alldocreader.ui.scan

import android.app.Activity
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.ActivityImageToPdfBinding
import com.theoccess.alldocreader.databinding.ItemScanAddBinding
import com.theoccess.alldocreader.databinding.ItemScanThumbBinding
import com.theoccess.alldocreader.databinding.SheetConfirmBinding
import com.theoccess.alldocreader.ui.viewer.Converters
import com.theoccess.alldocreader.util.toast
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Image to PDF": the finished pages in a grid — long press and drag to reorder, × to remove,
 * tap to edit again, Add pages (tile or top-right icon) — then Convert → "Converting… (x%)"
 * → Converted successfully screen.
 */
class ImageToPdfActivity : AppCompatActivity() {

    private lateinit var binding: ActivityImageToPdfBinding
    private val pages get() = ScanSession.pages
    private val adapter = GridAdapter()
    private var busy = false

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (pages.isEmpty()) { ScanSession.clear(this); finish() } else adapter.notifyDataSetChanged()
    }

    private val flow = AddPagesFlow(this) { added ->
        if (added.isNotEmpty()) {
            val first = pages.size
            pages.addAll(added)
            editor.launch(ScanEditActivity.intent(this, first, fromList = true))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageToPdfBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (pages.isEmpty()) { finish(); return }
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = confirmDiscard()
        })
        binding.btnAdd.setOnClickListener { if (!busy) flow.chooseSource() }
        if (Prefs.raw.getBoolean(KEY_HINT, false)) binding.hintBanner.visibility = View.GONE
        binding.btnCloseHint.setOnClickListener {
            Prefs.raw.edit().putBoolean(KEY_HINT, true).apply()
            binding.hintBanner.visibility = View.GONE
        }
        binding.btnConvert.setOnClickListener { convert() }

        val lm = GridLayoutManager(this, 2)
        binding.rvPages.layoutManager = lm
        binding.rvPages.adapter = adapter
        ItemTouchHelper(dragCallback).attachToRecyclerView(binding.rvPages)
    }

    // ------------------------------------------------------------------ grid

    private inner class GridAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = pages.size + 1
        override fun getItemViewType(position: Int) = if (position < pages.size) TYPE_PAGE else TYPE_ADD

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_PAGE) PageVH(ItemScanThumbBinding.inflate(inf, parent, false))
            else object : RecyclerView.ViewHolder(ItemScanAddBinding.inflate(inf, parent, false).root) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is PageVH) holder.bind(pages[position], position)
            else holder.itemView.findViewById<View>(R.id.tile).setOnClickListener { if (!busy) flow.chooseSource() }
        }
    }

    private inner class PageVH(val b: ItemScanThumbBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(page: ScanPage, position: Int) {
            b.tvNumber.text = (position + 1).toString()
            b.btnRemove.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos in pages.indices && !busy) {
                    pages.removeAt(pos)
                    if (pages.isEmpty()) { ScanSession.clear(this@ImageToPdfActivity); finish(); return@setOnClickListener }
                    refreshGrid()
                }
            }
            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos in pages.indices && !busy) editor.launch(ScanEditActivity.intent(this@ImageToPdfActivity, pos, fromList = true))
            }
            val file = page.output ?: page.source
            b.ivThumb.tag = file
            b.ivThumb.setImageDrawable(null)
            lifecycleScope.launch {
                val bmp = withContext(Dispatchers.IO) {
                    try {
                        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, o)
                        var s = 1
                        while (o.outWidth / (s * 2) >= 420 || o.outHeight / (s * 2) >= 420) s *= 2
                        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = s })
                    } catch (e: Throwable) {
                        null
                    }
                }
                if (b.ivThumb.tag == file) b.ivThumb.setImageBitmap(bmp)
            }
        }
    }

    @Suppress("NotifyDataSetChanged")
    private fun refreshGrid() = adapter.notifyDataSetChanged()

    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
    ) {
        override fun getMovementFlags(rv: RecyclerView, vh: RecyclerView.ViewHolder): Int =
            if (vh is PageVH) super.getMovementFlags(rv, vh) else 0

        override fun canDropOver(rv: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) =
            target is PageVH

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                viewHolder?.itemView?.animate()?.scaleX(1.05f)?.scaleY(1.05f)?.setDuration(120)?.start()
            }
        }

        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = vh.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from !in pages.indices || to !in pages.indices) return false
            pages.add(to, pages.removeAt(from))
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            vh.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            rv.post { refreshGrid() } // page numbers
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }

    // ------------------------------------------------------------------ convert

    private fun convert() {
        if (busy || pages.isEmpty()) return
        busy = true
        val pill = ProgressPill(this)
        pill.show(getString(R.string.converting_progress, 0))
        val list = pages.toList()
        // both Image to PDF and Scan to PDF save as "Image_PDF_…", like the original app
        val name = "Image_PDF_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                try {
                    val f = Converters.uniqueFile(Converters.outputDir(), name, "pdf")
                    PDDocument().use { doc ->
                        list.forEachIndexed { i, page ->
                            val jpeg = page.output?.takeIf { page.outputReady } ?: render(page)
                            addPage(doc, jpeg)
                            val p = (i + 1) * 99 / list.size
                            withContext(Dispatchers.Main) { pill.show(getString(R.string.converting_progress, p)) }
                        }
                        doc.save(f)
                    }
                    com.theoccess.alldocreader.data.SavedFiles.onSaved(applicationContext, f)
                    f
                } catch (e: Throwable) {
                    null
                }
            }
            pill.dismiss()
            busy = false
            if (out == null) {
                toast(R.string.convert_failed)
                return@launch
            }
            ScanSession.clear(this@ImageToPdfActivity)
            startActivity(ConvertResultActivity.intent(this@ImageToPdfActivity, out))
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    private fun render(page: ScanPage): File {
        val bmp = ImageOps.render(page, ImageOps.FINAL_PX) ?: throw IllegalStateException("page")
        val f = ScanSession.newFile(this, "page")
        ImageOps.saveJpeg(bmp, f)
        bmp.recycle()
        return f
    }

    /** One PDF page per picture, shaped like the picture (long side = A4 long side). */
    private fun addPage(doc: PDDocument, jpeg: File) {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(jpeg.absolutePath, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) throw IllegalStateException("bad image")
        val long = PDRectangle.A4.height
        val s = long / maxOf(o.outWidth, o.outHeight)
        val pw = o.outWidth * s
        val ph = o.outHeight * s
        val page = PDPage(PDRectangle(pw, ph))
        doc.addPage(page)
        val img = jpeg.inputStream().use { JPEGFactory.createFromStream(doc, it) }
        PDPageContentStream(doc, page).use { it.drawImage(img, 0f, 0f, pw, ph) }
    }

    private fun confirmDiscard() {
        if (busy) return
        val dialog = BottomSheetDialog(this, R.style.Theme_DocReader_BottomSheet)
        val b = SheetConfirmBinding.inflate(LayoutInflater.from(this))
        b.tvTitle.setText(R.string.discard_title)
        b.tvMessage.setText(R.string.discard_message)
        b.btnOk.setText(R.string.discard)
        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnOk.setOnClickListener {
            dialog.dismiss()
            ScanSession.clear(this)
            finish()
        }
        dialog.setContentView(b.root)
        dialog.show()
    }

    companion object {
        private const val TYPE_PAGE = 0
        private const val TYPE_ADD = 1
        private const val KEY_HINT = "img2pdf_hint_closed"
    }
}
