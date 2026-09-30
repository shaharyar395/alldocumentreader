package com.theoccess.alldocreader.ui.pages

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.viewpager2.widget.ViewPager2
import com.theoccess.alldocreader.databinding.DialogPagePreviewBinding
import com.theoccess.alldocreader.databinding.DialogPageSetupBinding
import com.theoccess.alldocreader.databinding.ItemPreviewPageBinding
import com.theoccess.alldocreader.databinding.SheetExtractedBinding
import com.theoccess.alldocreader.util.TopPill
import java.util.Locale
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.text.Html
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.ActivityPageOrganizerBinding
import com.theoccess.alldocreader.databinding.ItemBottomToolBinding
import com.theoccess.alldocreader.databinding.ItemPageThumbBinding
import com.theoccess.alldocreader.databinding.PopupInsertBinding
import com.theoccess.alldocreader.databinding.SheetConfirmBinding
import com.theoccess.alldocreader.ui.viewer.Converters
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * "Pages" for a PDF: drag to reorder, select, rotate, extract, delete, insert blank pages / images,
 * undo / redo, and Done to save back into the same file.
 */
class PageOrganizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPageOrganizerBinding
    private lateinit var file: File
    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private val renderDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var origSizes: List<Pair<Float, Float>> = emptyList()

    private var items: MutableList<PageItem> = mutableListOf()
    private var original: List<PageItem> = emptyList()
    private val undo = ArrayDeque<List<PageItem>>()
    private val redo = ArrayDeque<List<PageItem>>()
    private val selected = LinkedHashSet<Long>()
    private var nextId = 1L
    private var thumbWidth = 360
    private var preselectPage = -1
    /** Bumped when the file is re-saved so thumbnails rendered from the old file are dropped. */
    private var generation = 0

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val adapter = ThumbAdapter()

    private val insertBlank = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == Activity.RESULT_OK && data != null) {
            val spec = InsertBlankPagesActivity.specFrom(data) ?: return@registerForActivityResult
            val count = data.getIntExtra(InsertBlankPagesActivity.EXTRA_COUNT, 1)
            insertItems(List(count) { PageItem(nextId++, PageKind.Blank(spec)) })
        }
    }

    private val pickImages = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)) { uris ->
        if (uris.isNotEmpty()) insertItems(uris.map { PageItem(nextId++, PageKind.Picture(it.toString())) })
    }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (items != original) confirmDiscard() else finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPageOrganizerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        file = File(intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return })
        preselectPage = if (savedInstanceState == null) intent.getIntExtra(EXTRA_PAGE, -1) else -1
        thumbWidth = (resources.displayMetrics.widthPixels / 2).coerceAtMost(540)

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, backCallback)
        binding.btnUndo.setOnClickListener { undo() }
        binding.btnRedo.setOnClickListener { redo() }
        binding.btnDone.setOnClickListener { save() }
        binding.btnSelectAll.setOnClickListener {
            if (selected.size == items.size) selected.clear() else items.forEach { selected += it.id }
            adapter.notifyItemRangeChanged(0, items.size, PAYLOAD_SELECT)
            updateUi()
        }
        if (Prefs.raw.getBoolean(KEY_HINT_CLOSED, false)) binding.hintBanner.visibility = View.GONE
        binding.btnCloseHint.setOnClickListener {
            Prefs.raw.edit().putBoolean(KEY_HINT_CLOSED, true).apply()
            binding.hintBanner.visibility = View.GONE
        }

        setupTools()
        binding.rvPages.layoutManager = GridLayoutManager(this, 2)
        binding.rvPages.adapter = adapter
        ItemTouchHelper(dragCallback).attachToRecyclerView(binding.rvPages)

        open()
    }

    private fun open() {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            fd = pfd
            val r = PdfRenderer(pfd)
            renderer = r
            origSizes = (0 until r.pageCount).map { i -> r.openPage(i).use { it.width.toFloat() to it.height.toFloat() } }
            items = MutableList(r.pageCount) { PageItem(nextId++, PageKind.Original(it)) }
            original = items.toList()
            // the page that was on screen in the viewer starts selected
            items.getOrNull(preselectPage)?.let { selected += it.id }
            preselectPage = -1
            adapter.notifyDataSetChanged()
            updateUi()
            items.indexOfFirst { it.id in selected }.takeIf { it > 1 }?.let { binding.rvPages.scrollToPosition(it) }
        } catch (e: Exception) {
            toast(R.string.pdf_password_protected)
            finish()
        }
    }

    // ------------------------------------------------------------------ tools

    private fun tool(t: ItemBottomToolBinding, icon: Int, label: Int, onClick: (View) -> Unit) {
        t.ivIcon.setImageResource(icon)
        t.tvLabel.setText(label)
        t.root.setOnClickListener { onClick(it) }
    }

    private fun setTool(t: ItemBottomToolBinding, enabled: Boolean) {
        t.root.isEnabled = enabled
        t.ivIcon.isEnabled = enabled
        t.tvLabel.isEnabled = enabled
    }

    private fun setupTools() {
        tool(binding.toolInsert, R.drawable.ic_insert_page, R.string.insert) { showInsertPopup(it) }
        tool(binding.toolRotate, R.drawable.ic_rotate, R.string.rotate) {
            commit(items.map { if (it.id in selected) it.copy(rotation = (it.rotation + 90) % 360) else it })
        }
        tool(binding.toolExtract, R.drawable.ic_extract, R.string.extract) { extract() }
        tool(binding.toolDelete, R.drawable.ic_delete, R.string.delete) { confirmDeletePages() }
        tool(binding.toolSetup, R.drawable.ic_setup, R.string.setup) { showSetup() }
    }

    private fun updateUi() {
        binding.tvSelected.text = getString(R.string.selected_count, selected.size)
        val any = selected.isNotEmpty()
        setTool(binding.toolRotate, any)
        setTool(binding.toolExtract, any)
        setTool(binding.toolDelete, any && selected.size < items.size)  // a PDF must keep at least one page
        setTool(binding.toolSetup, any)
        binding.btnUndo.isEnabled = undo.isNotEmpty()
        binding.btnRedo.isEnabled = redo.isNotEmpty()
        val history = if (undo.isEmpty() && redo.isEmpty()) View.INVISIBLE else View.VISIBLE
        binding.btnUndo.visibility = history
        binding.btnRedo.visibility = history
        binding.btnDone.isEnabled = items != original
    }

    // ------------------------------------------------------------------ history

    private fun commit(newList: List<PageItem>) {
        undo.addLast(items.toList())
        redo.clear()
        items = newList.toMutableList()
        selected.retainAll(items.map { it.id }.toSet())
        adapter.notifyDataSetChanged()
        updateUi()
    }

    private fun undo() {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(items.toList())
        items = prev.toMutableList()
        selected.retainAll(items.map { it.id }.toSet())
        adapter.notifyDataSetChanged()
        updateUi()
    }

    private fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(items.toList())
        items = next.toMutableList()
        selected.retainAll(items.map { it.id }.toSet())
        adapter.notifyDataSetChanged()
        updateUi()
    }

    // ------------------------------------------------------------------ insert

    /** New pages go after the last selected page, or at the end. */
    private fun insertPosition(): Int {
        val last = items.indexOfLast { it.id in selected }
        return if (last >= 0) last + 1 else items.size
    }

    private fun insertItems(newItems: List<PageItem>) {
        val pos = insertPosition()
        val list = items.toMutableList()
        list.addAll(pos, newItems)
        commit(list)
        binding.rvPages.post { binding.rvPages.smoothScrollToPosition((pos + newItems.size - 1).coerceAtLeast(0)) }
    }

    private fun showInsertPopup(anchor: View) {
        val pb = PopupInsertBinding.inflate(LayoutInflater.from(this))
        val popup = PopupWindow(pb.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.elevation = dp(8).toFloat()
        pb.optBlank.setOnClickListener {
            popup.dismiss()
            val prev = items.getOrNull(insertPosition() - 1) ?: items.lastOrNull()
            val (w, h) = prev?.let { sizeOf(it) } ?: (PageSize.ALL[1].widthPt to PageSize.ALL[1].heightPt)
            insertBlank.launch(InsertBlankPagesActivity.intent(this, w, h))
        }
        pb.optImages.setOnClickListener {
            popup.dismiss()
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        pb.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        popup.showAsDropDown(anchor, dp(8), -(anchor.height + pb.root.measuredHeight + dp(4)), Gravity.START)
    }

    /** Page size in points (before the user's extra rotation). */
    private fun sizeOf(item: PageItem): Pair<Float, Float> {
        val own = baseSize(item)
        val s = item.size ?: return own
        val short = minOf(s.first, s.second)
        val long = maxOf(s.first, s.second)
        return if (own.first > own.second) long to short else short to long
    }

    private fun baseSize(item: PageItem): Pair<Float, Float> = when (val k = item.kind) {
        is PageKind.Original -> origSizes.getOrElse(k.index) { 595f to 842f }
        is PageKind.Blank -> k.spec.widthPt to k.spec.heightPt
        is PageKind.Picture -> 595f to 842f
    }

    // ------------------------------------------------------------------ extract / delete / setup

    private fun extract() {
        val chosen = items.filter { it.id in selected }
        if (chosen.isEmpty()) return
        showSaving(getString(R.string.extracting))
        lifecycleScope.launch {
            val out = try {
                withContext(Dispatchers.IO) {
                    val f = Converters.uniqueFile(Converters.outputDir(), com.theoccess.alldocreader.ui.create.Outputs.defaultName("Extract_AllDocReader"), "pdf")
                    PdfEditor.write(applicationContext, file, chosen, f)
                    com.theoccess.alldocreader.data.SavedFiles.onSaved(applicationContext, f)
                    f
                }
            } catch (e: Throwable) {
                null
            }
            hideSaving()
            if (out == null) {
                toast(R.string.save_failed)
                return@launch
            }
            FileRepository.refresh(applicationContext, force = true)
            showExtracted(out)
        }
    }

    /** "Extracted successfully — Saved as "Extract_x.pdf". [View] [Share]" */
    private fun showExtracted(out: File) {
        val dialog = BottomSheetDialog(this, R.style.Theme_DocReader_BottomSheet)
        val b = SheetExtractedBinding.inflate(LayoutInflater.from(this))
        b.tvMessage.text = getString(R.string.saved_as, out.name)
        b.btnClose.setOnClickListener { dialog.dismiss() }
        b.btnView.setOnClickListener {
            dialog.dismiss()
            FileActions.open(this, DocFile.from(out))
        }
        b.btnShare.setOnClickListener {
            dialog.dismiss()
            FileActions.share(this, DocFile.from(out))
        }
        dialog.setContentView(b.root)
        dialog.show()
    }

    private fun confirmDeletePages() {
        val n = selected.size
        if (n == 0) return
        if (n >= items.size) {
            toast(R.string.pdf_needs_page)
            return
        }
        confirmSheet(
            getString(R.string.delete_confirm),
            Html.fromHtml(getString(R.string.delete_pages_message, n), Html.FROM_HTML_MODE_LEGACY),
            getString(R.string.delete)
        ) {
            commit(items.filterNot { it.id in selected })
            selected.clear()
            updateUi()
            toast(R.string.deleted_successfully)
        }
    }

    // ------------------------------------------------------------------ page setup

    /** Full-screen "Page setup": page size and page colour for the selected pages. */
    private fun showSetup() {
        val chosen = items.filter { it.id in selected }
        if (chosen.isEmpty()) return
        val first = chosen.first()
        val dialog = AppCompatDialog(this, R.style.Theme_DocReader_WhiteBar)
        val b = DialogPageSetupBinding.inflate(LayoutInflater.from(this))
        var size: PageSize? = null
        var color: Int? = null
        var job: Job? = null

        fun refresh() {
            val probe = first.copy(size = size?.let { it.widthPt to it.heightPt } ?: first.size, bg = color ?: first.bg)
            val (w, h) = sizeOf(probe)
            b.tvDims.text = getString(R.string.dims_cm, cm(w), cm(h))
            b.btnApply.isEnabled = size != null || color != null
            // preview box keeps the page's proportions
            val maxW = dp(150)
            val maxH = dp(210)
            val scale = minOf(maxW / w, maxH / h)
            b.ivPreview.layoutParams = b.ivPreview.layoutParams.apply {
                width = (w * scale).toInt()
                height = (h * scale).toInt()
            }
            b.ivPreview.requestLayout()
            job?.cancel()
            job = lifecycleScope.launch {
                val bmp = withContext(renderDispatcher) { try { renderThumb(probe, dp(300)) } catch (e: Exception) { null } }
                if (bmp != null) b.ivPreview.setImageBitmap(bmp)
            }
            for (i in 0 until b.sizes.childCount) b.sizes.getChildAt(i).isSelected = (i == 0 && size == null) || (i > 0 && PageSize.ALL[i - 1] == size)
            for (i in 0 until b.colors.childCount) {
                val c = b.colors.getChildAt(i) as FrameLayout
                val on = (i == 0 && color == null) || (i > 0 && Templates.COLORS[i - 1] == color)
                c.getChildAt(0).visibility = if (on && i > 0) View.VISIBLE else View.INVISIBLE
                c.isSelected = on
            }
        }

        // sizes: ⊘ A5 A4 A3 B5 B4 Letter Legal
        fun chip(): TextView = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginStart = dp(4); marginEnd = dp(4) }
            minWidth = dp(40)
            gravity = Gravity.CENTER
            setPadding(dp(10), 0, dp(10), 0)
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            background = ContextCompat.getDrawable(context, R.drawable.bg_setup_chip)
        }
        b.sizes.addView(chip().apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_none, 0, 0, 0)
            setOnClickListener { size = null; refresh() }
        })
        PageSize.ALL.forEach { ps ->
            b.sizes.addView(chip().apply {
                setText(ps.label)
                setOnClickListener { size = ps; refresh() }
            })
        }
        // colours: ⊘ + six page colours
        b.colors.addView(FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(8); marginEnd = dp(8) }
            addView(View(context))
            background = ContextCompat.getDrawable(context, R.drawable.ic_none)
            setOnClickListener { color = null; refresh() }
        })
        Templates.COLORS.forEach { c ->
            val f = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(8); marginEnd = dp(8) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(dp(1), 0xFFD5DAE1.toInt())
                }
                setOnClickListener { color = c; refresh() }
            }
            f.addView(ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
                setImageResource(R.drawable.ic_check)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(0xFF1B1F2A.toInt()))
            })
            b.colors.addView(f)
        }
        b.tvCount.text = resources.getQuantityString(R.plurals.pages_selected, chosen.size, chosen.size)
        b.btnBack.setOnClickListener { dialog.dismiss() }
        b.btnApply.setOnClickListener {
            val newSize = size?.let { it.widthPt to it.heightPt }
            val newColor = color
            commit(items.map {
                if (it.id in selected) it.copy(size = newSize ?: it.size, bg = newColor ?: it.bg) else it
            })
            dialog.dismiss()
        }
        dialog.setContentView(b.root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.setOnDismissListener { job?.cancel() }
        dialog.show()
        refresh()
    }

    private fun cm(pt: Float) = String.format(Locale.US, "%.2f", pt * 2.54f / 72f)

    // ------------------------------------------------------------------ all pages preview

    /** Tapping a page: full-screen "All pages (n/N)" with a checkbox to select it. */
    private fun showPreview(start: Int) {
        if (items.isEmpty()) return
        val dialog = AppCompatDialog(this, R.style.Theme_DocReader_WhiteBar)
        val b = DialogPagePreviewBinding.inflate(LayoutInflater.from(this))
        val snapshot = items.toList()
        fun sync(pos: Int) {
            b.tvTitle.text = getString(R.string.all_pages_title, pos + 1, snapshot.size)
            b.cbSelect.isChecked = snapshot[pos].id in selected
        }
        b.pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = snapshot.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val pb = ItemPreviewPageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return object : RecyclerView.ViewHolder(pb.root) {}.also { it.itemView.tag = pb }
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val pb = holder.itemView.tag as ItemPreviewPageBinding
                val item = snapshot[position]
                pb.ivPage.setImageDrawable(null)
                pb.pbPage.visibility = View.VISIBLE
                lifecycleScope.launch {
                    val bmp = withContext(renderDispatcher) {
                        try { renderThumb(item, resources.displayMetrics.widthPixels.coerceAtMost(1400)) } catch (e: Exception) { null }
                    }
                    if (holder.bindingAdapterPosition == position) {
                        pb.ivPage.setImageBitmap(bmp)
                        pb.pbPage.visibility = View.GONE
                    }
                }
            }
        }
        b.pager.setCurrentItem(start, false)
        sync(start)
        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = sync(position)
        })
        b.cbSelect.setOnClickListener {
            val item = snapshot[b.pager.currentItem]
            if (b.cbSelect.isChecked) selected += item.id else selected -= item.id
        }
        b.btnBack.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            adapter.notifyItemRangeChanged(0, items.size, PAYLOAD_SELECT)
            updateUi()
        }
        dialog.setContentView(b.root)
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.show()
    }

    private fun confirmSheet(title: String, message: CharSequence, ok: String, onOk: () -> Unit) {
        val dialog = BottomSheetDialog(this, R.style.Theme_DocReader_BottomSheet)
        val b = SheetConfirmBinding.inflate(LayoutInflater.from(this))
        b.tvTitle.text = title
        b.tvMessage.text = message
        b.btnOk.text = ok
        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnOk.setOnClickListener { dialog.dismiss(); onOk() }
        dialog.setContentView(b.root)
        dialog.show()
    }

    private fun confirmDiscard() {
        confirmSheet(getString(R.string.discard_title), getString(R.string.discard_message), getString(R.string.discard)) {
            finish()
        }
    }

    // ------------------------------------------------------------------ save

    private fun save() {
        if (items == original) return
        showSaving(getString(R.string.saving))
        val snapshot = items.toList()
        lifecycleScope.launch {
            val ok = try {
                withContext(Dispatchers.IO) {
                    // Write next to the original, then swap, so a failure never leaves a half-written PDF.
                    val tmp = File(file.parentFile, ".${file.name}.${System.currentTimeMillis()}.tmp")
                    try {
                        PdfEditor.write(applicationContext, file, snapshot, tmp)
                        closeRenderer()
                        if (!tmp.renameTo(file)) tmp.copyTo(file, overwrite = true)
                    } finally {
                        if (tmp.exists()) tmp.delete()
                    }
                    com.theoccess.alldocreader.data.SavedFiles.onSaved(applicationContext, file)
                    true
                }
            } catch (e: Throwable) {
                false
            }
            hideSaving()
            if (!ok) {
                toast(R.string.save_failed)
                return@launch
            }
            FileRepository.replace(file.absolutePath, DocFile.from(file))
            setResult(Activity.RESULT_OK)
            // stay here with the saved file, like the original app
            generation++
            cache.evictAll()
            undo.clear()
            redo.clear()
            selected.clear()
            open()
            TopPill.show(this@PageOrganizerActivity, getString(R.string.saved_successfully), topMarginDp = 60)
        }
    }

    private fun showSaving(text: String) {
        binding.tvSaving.text = text
        binding.saving.visibility = View.VISIBLE
    }

    private fun hideSaving() {
        binding.saving.visibility = View.GONE
    }

    @Synchronized
    private fun closeRenderer() {
        try { renderer?.close() } catch (ignored: Exception) {}
        try { fd?.close() } catch (ignored: Exception) {}
        renderer = null
        fd = null
    }

    override fun onDestroy() {
        renderDispatcher.close()
        closeRenderer()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ thumbnails

    private fun cacheKey(item: PageItem) = when (val k = item.kind) {
        is PageKind.Original -> "o${k.index}"
        is PageKind.Blank -> "b${k.spec.hashCode()}"
        is PageKind.Picture -> "p${k.uri}"
    } + "_r${item.rotation}_s${item.size}_c${item.bg}"

    /** Renders a page picture [width] px wide (with page setup applied); runs on [renderDispatcher]. */
    private fun renderThumb(item: PageItem, width: Int = thumbWidth): Bitmap? {
        val paper = item.bg ?: Color.WHITE
        val base: Bitmap = when (val k = item.kind) {
            is PageKind.Original -> synchronized(this) {
                val r = renderer ?: return null
                r.openPage(k.index).use { p ->
                    val h = (width * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888).also {
                        it.eraseColor(paper)
                        p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
            is PageKind.Blank -> sizeOf(item).let { (w, h) ->
                Templates.render(k.spec.copy(widthPt = w, heightPt = h, color = item.bg ?: k.spec.color), width)
            }
            is PageKind.Picture -> {
                val img = PdfEditor.decode(this, Uri.parse(k.uri), 800) ?: return null
                val landscape = img.width > img.height
                val pw = if (landscape) 842f else 595f
                val ph = if (landscape) 595f else 842f
                val w = width
                val h = (w * ph / pw).toInt()
                Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { out ->
                    val c = Canvas(out)
                    c.drawColor(paper)
                    val m = 18f * w / pw
                    val s = minOf((w - 2 * m) / img.width, (h - 2 * m) / img.height)
                    val dw = img.width * s
                    val dh = img.height * s
                    c.drawBitmap(img, null, android.graphics.RectF((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2), null)
                    img.recycle()
                }
            }
        }
        val sized = if (item.size == null || item.kind is PageKind.Blank) base else {
            // new page size: the original content scaled to fit and centred on the new page
            val (w, h) = sizeOf(item)
            val outW = width
            val outH = (width * h / w).toInt().coerceAtLeast(1)
            Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888).also { out ->
                val c = Canvas(out)
                c.drawColor(paper)
                val s = minOf(outW / base.width.toFloat(), outH / base.height.toFloat())
                val dw = base.width * s
                val dh = base.height * s
                c.drawBitmap(base, null, android.graphics.RectF((outW - dw) / 2, (outH - dh) / 2, (outW + dw) / 2, (outH + dh) / 2), null)
                base.recycle()
            }
        }
        if (item.rotation == 0) return sized
        val m = Matrix().apply { postRotate(item.rotation.toFloat()) }
        return Bitmap.createBitmap(sized, 0, 0, sized.width, sized.height, m, true)
    }

    private inner class ThumbAdapter : RecyclerView.Adapter<ThumbVH>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ThumbVH(ItemPageThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: ThumbVH, position: Int) = holder.bind(items[position], position)

        override fun onBindViewHolder(holder: ThumbVH, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains(PAYLOAD_SELECT)) holder.bindSelection(items[position])
            else holder.bind(items[position], position)
        }

        override fun onViewRecycled(holder: ThumbVH) {
            holder.job?.cancel()
        }
    }

    private inner class ThumbVH(val b: ItemPageThumbBinding) : RecyclerView.ViewHolder(b.root) {
        var job: Job? = null

        fun bind(item: PageItem, position: Int) {
            b.tvNumber.text = (position + 1).toString()
            bindSelection(item)
            b.cbSelect.isClickable = true
            b.cbSelect.setOnClickListener {
                val cur = items.getOrNull(bindingAdapterPosition) ?: return@setOnClickListener
                if (!selected.remove(cur.id)) selected += cur.id
                bindSelection(cur)
                updateUi()
            }
            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) showPreview(pos)
            }
            job?.cancel()
            val key = cacheKey(item)
            val cached = cache.get(key)
            if (cached != null) {
                b.ivThumb.setImageBitmap(cached)
                b.pbThumb.visibility = View.GONE
                return
            }
            b.ivThumb.setImageDrawable(null)
            b.pbThumb.visibility = View.VISIBLE
            val gen = generation
            job = lifecycleScope.launch {
                val bmp = withContext(renderDispatcher) { try { renderThumb(item) } catch (e: Exception) { null } }
                if (bmp != null && gen == generation) {
                    cache.put(key, bmp)
                    if (items.getOrNull(bindingAdapterPosition)?.let { cacheKey(it) } == key) {
                        b.ivThumb.setImageBitmap(bmp)
                        b.pbThumb.visibility = View.GONE
                    }
                }
            }
        }

        fun bindSelection(item: PageItem) {
            val on = item.id in selected
            b.cbSelect.isChecked = on
            b.card.isSelected = on
            b.selectedOverlay.visibility = if (on) View.VISIBLE else View.GONE
        }
    }

    /** Long-press and drag to reorder. */
    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
    ) {
        private var before: List<PageItem>? = null

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                before = items.toList()
                viewHolder?.itemView?.animate()?.scaleX(1.05f)?.scaleY(1.05f)?.setDuration(120)?.start()
            }
        }

        override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = vh.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) return false
            val moved = items.removeAt(from)
            items.add(to, moved)
            adapter.notifyItemMoved(from, to)
            return true
        }

        override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
            super.clearView(rv, vh)
            vh.itemView.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
            val prev = before
            before = null
            if (prev != null && prev != items) {
                undo.addLast(prev)
                redo.clear()
                rv.post { adapter.notifyDataSetChanged() } // refresh page numbers
                updateUi()
            }
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_PAGE = "page"
        private const val PAYLOAD_SELECT = "select"
        private const val KEY_HINT_CLOSED = "organizer_hint_closed"

        fun intent(context: Context, file: File, currentPage: Int = -1) =
            Intent(context, PageOrganizerActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_PAGE, currentPage)
    }
}
