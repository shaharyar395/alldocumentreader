package com.theoccess.alldocreader.ui.scan

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.LruCache
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityScanEditBinding
import com.theoccess.alldocreader.databinding.ItemBottomToolBinding
import com.theoccess.alldocreader.databinding.ItemScanFilterBinding
import com.theoccess.alldocreader.databinding.ItemScanPageBinding
import com.theoccess.alldocreader.databinding.SheetConfirmBinding
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

/**
 * Page editor after taking / choosing pictures: swipe between pages (neighbours peek in),
 * remove a page (×), page counter, hold-to-compare with the original, look filters
 * (with "Apply to all"), Retake, Pages (add more), Rotate and Crop (frame editor with
 * Left / Right / No crop ⇄ Auto crop). Done → "Processing… (x%)" → Image to PDF list.
 */
class ScanEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanEditBinding
    private val pages get() = ScanSession.pages
    private var fromList = false
    private var snapshot: List<ScanPage> = emptyList()

    private val renderDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val pageAdapter = PageAdapter()
    private val filterAdapter = FilterAdapter()
    private var compareId: Long? = null
    private var thumbsJob: Job? = null
    private var busy = false

    // crop mode
    private var cropMode = false
    private var cropRotation = 0
    private var cropBase: Bitmap? = null

    private val flow = AddPagesFlow(this) { added -> addPages(added) }

    private val retake = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val path = r.data?.getStringArrayListExtra(CameraActivity.EXTRA_PATHS)?.firstOrNull()
        val page = current()
        if (r.resultCode != Activity.RESULT_OK || path == null || page == null) return@registerForActivityResult
        lifecycleScope.launch {
            val file = File(path)
            val quad = if (ScanPrefs.autoCrop) withContext(Dispatchers.Default) { AddPagesFlow.detect(file) } else null
            page.source = file
            page.quad = quad
            page.rotation = 0
            refreshCurrent()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        if (pages.isEmpty()) { finish(); return }
        fromList = intent.getBooleanExtra(EXTRA_FROM_LIST, false)
        snapshot = pages.map { it.snapshot() }

        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        binding.btnDone.setOnClickListener { done() }
        binding.btnCropOk.setOnClickListener { applyCrop() }

        binding.pager.adapter = pageAdapter
        binding.pager.offscreenPageLimit = 1
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUi()
                loadFilterThumbs()
                scrollFiltersToSelected()
            }
        })
        val start = intent.getIntExtra(EXTRA_START, 0).coerceIn(0, pages.size - 1)
        binding.pager.setCurrentItem(start, false)
        binding.btnPrev.setOnClickListener { binding.pager.currentItem = binding.pager.currentItem - 1 }
        binding.btnNext.setOnClickListener { binding.pager.currentItem = binding.pager.currentItem + 1 }
        setupCompare()

        binding.rvFilters.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
        binding.rvFilters.adapter = filterAdapter
        binding.cbApplyAll.setOnCheckedChangeListener { _, checked ->
            if (checked) current()?.let { cur -> setFilter(cur.filter, all = true) }
        }

        tool(binding.toolRetake, R.drawable.ic_camera, R.string.retake) {
            retake.launch(CameraActivity.intent(this, single = true))
        }
        tool(binding.toolPages, R.drawable.ic_insert_page, R.string.pages) { flow.chooseSource() }
        tool(binding.toolRotate, R.drawable.ic_rotate, R.string.rotate) {
            current()?.let { it.rotation = (it.rotation + 270) % 360; refreshCurrent() }
        }
        tool(binding.toolCrop, R.drawable.ic_crop, R.string.crop) { enterCrop() }
        tool(binding.toolLeft, R.drawable.ic_rotate_left, R.string.left) { turnCrop(270) }
        tool(binding.toolRight, R.drawable.ic_rotate_right, R.string.right) { turnCrop(90) }
        tool(binding.toolNoCrop, R.drawable.ic_no_crop, R.string.no_crop) { toggleCrop() }
        binding.cropView.onChanged = { updateCropLabel() }

        updateUi()
        loadFilterThumbs()
        scrollFiltersToSelected()
    }

    private fun tool(t: ItemBottomToolBinding, icon: Int, label: Int, onClick: () -> Unit) {
        t.ivIcon.setImageResource(icon)
        t.tvLabel.setText(label)
        t.root.setOnClickListener { if (!busy) onClick() }
    }

    private fun current(): ScanPage? = pages.getOrNull(binding.pager.currentItem)

    private fun updateUi() {
        val pos = binding.pager.currentItem
        binding.tvCounter.text = getString(R.string.page_counter, (pos + 1).coerceAtMost(pages.size), pages.size)
        binding.btnPrev.isEnabled = pos > 0 && !cropMode
        binding.btnNext.isEnabled = pos < pages.size - 1 && !cropMode
        binding.cbApplyAll.visibility = if (pages.size > 1) View.VISIBLE else View.GONE
        filterAdapter.notifyDataSetChanged()
    }

    private fun refreshCurrent() {
        pageAdapter.notifyItemChanged(binding.pager.currentItem)
        loadFilterThumbs()
    }

    // ------------------------------------------------------------------ pages

    private fun addPages(added: List<ScanPage>) {
        if (added.isEmpty()) return
        val first = pages.size
        pages.addAll(added)
        pageAdapter.notifyItemRangeInserted(first, added.size)
        binding.pager.setCurrentItem(first, true)
        updateUi()
    }

    private fun deletePage(pos: Int) {
        if (pos !in pages.indices || busy) return
        pages.removeAt(pos)
        pageAdapter.notifyItemRemoved(pos)
        if (pages.isEmpty()) {
            if (!fromList) ScanSession.clear(this)
            setResult(Activity.RESULT_OK)
            finish()
            return
        }
        binding.pager.post {
            updateUi()
            loadFilterThumbs()
        }
    }

    // ------------------------------------------------------------------ filters

    private fun setFilter(f: ScanFilter, all: Boolean) {
        if (all) {
            pages.forEach { it.filter = f }
            pageAdapter.notifyItemRangeChanged(0, pages.size)
        } else {
            current()?.filter = f
            pageAdapter.notifyItemChanged(binding.pager.currentItem)
        }
        filterAdapter.notifyDataSetChanged()
    }

    /** Small previews of the current page in every filter. */
    private fun loadFilterThumbs() {
        val page = current() ?: return
        thumbsJob?.cancel()
        thumbsJob = lifecycleScope.launch {
            val baseKey = "t:" + page.key(ScanFilter.ORIGINAL)
            val base = cache.get(baseKey) ?: withContext(renderDispatcher) {
                try { ImageOps.render(page, ImageOps.THUMB_PX, ScanFilter.ORIGINAL) } catch (e: Throwable) { null }
            }?.also { cache.put(baseKey, it) } ?: return@launch
            for (f in ScanFilter.entries) {
                val k = "$baseKey:$f"
                if (cache.get(k) == null) {
                    val bmp = withContext(renderDispatcher) {
                        try { ImageOps.applyFilter(base.copy(Bitmap.Config.ARGB_8888, true), f) } catch (e: Throwable) { null }
                    } ?: continue
                    cache.put(k, bmp)
                }
                filterAdapter.notifyItemChanged(f.ordinal, PAYLOAD_THUMB)
            }
        }
    }

    /** Keeps the page's filter in view in the strip. */
    private fun scrollFiltersToSelected() {
        val f = current()?.filter ?: return
        binding.rvFilters.post {
            (binding.rvFilters.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(f.ordinal, binding.rvFilters.width / 2 - binding.rvFilters.paddingStart - (34 * resources.displayMetrics.density).toInt())
        }
    }

    private fun thumbFor(page: ScanPage, f: ScanFilter): Bitmap? = cache.get("t:" + page.key(ScanFilter.ORIGINAL) + ":$f")

    private inner class FilterAdapter : RecyclerView.Adapter<FilterVH>() {
        override fun getItemCount() = ScanFilter.entries.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            FilterVH(ItemScanFilterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: FilterVH, position: Int) = holder.bind(ScanFilter.entries[position])
        override fun onBindViewHolder(holder: FilterVH, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains(PAYLOAD_THUMB)) holder.bindThumb(ScanFilter.entries[position]) else holder.bind(ScanFilter.entries[position])
        }
    }

    private inner class FilterVH(val b: ItemScanFilterBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(f: ScanFilter) {
            val on = current()?.filter == f
            b.tvLabel.setText(f.label)
            b.tvLabel.setTextColor(ContextCompat.getColor(this@ScanEditActivity, if (on) R.color.primary else R.color.text_secondary))
            b.tvLabel.paint.isFakeBoldText = on
            b.box.isSelected = on
            b.selShade.visibility = if (on) View.VISIBLE else View.GONE
            b.ivCheck.visibility = if (on) View.VISIBLE else View.GONE
            bindThumb(f)
            b.root.setOnClickListener { if (!busy) setFilter(f, binding.cbApplyAll.isChecked) }
        }

        fun bindThumb(f: ScanFilter) {
            val page = current()
            b.ivThumb.setImageBitmap(page?.let { thumbFor(it, f) })
        }
    }

    // ------------------------------------------------------------------ page pager

    private inner class PageAdapter : RecyclerView.Adapter<PageVH>() {
        override fun getItemCount() = pages.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PageVH(ItemScanPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun onBindViewHolder(holder: PageVH, position: Int) = holder.bind(pages[position])
        override fun onViewRecycled(holder: PageVH) { holder.job?.cancel() }
    }

    private inner class PageVH(val b: ItemScanPageBinding) : RecyclerView.ViewHolder(b.root) {
        var job: Job? = null

        fun bind(page: ScanPage) {
            b.btnDelete.setOnClickListener { deletePage(bindingAdapterPosition) }
            val filter = if (compareId == page.id) ScanFilter.ORIGINAL else page.filter
            val key = page.key(filter)
            job?.cancel()
            val cached = cache.get(key)
            if (cached != null) { show(cached); return }
            b.pbPage.visibility = View.VISIBLE
            job = lifecycleScope.launch {
                val bmp = withContext(renderDispatcher) {
                    try { ImageOps.render(page, ImageOps.PREVIEW_PX, filter) } catch (e: Throwable) { null }
                } ?: return@launch
                cache.put(key, bmp)
                if (pages.getOrNull(bindingAdapterPosition)?.id == page.id) show(bmp)
            }
        }

        /** Fits the page picture in the available space; the × sits on its top-left corner. */
        fun show(bmp: Bitmap) {
            b.pbPage.visibility = View.GONE
            val root = b.root
            val w = root.width - root.paddingStart - root.paddingEnd
            val h = root.height - root.paddingTop - root.paddingBottom
            if (w <= 0 || h <= 0) { root.post { show(bmp) }; return }
            val s = min(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
            val lp = b.ivPage.layoutParams
            lp.width = (bmp.width * s).toInt().coerceAtLeast(1)
            lp.height = (bmp.height * s).toInt().coerceAtLeast(1)
            b.ivPage.layoutParams = lp
            b.ivPage.setImageBitmap(bmp)
        }
    }

    /** Hold the compare button to see the page without the filter. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupCompare() {
        binding.btnCompare.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    compareId = current()?.id
                    v.isPressed = true
                    pageAdapter.notifyItemChanged(binding.pager.currentItem)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    compareId = null
                    v.isPressed = false
                    pageAdapter.notifyItemChanged(binding.pager.currentItem)
                    if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }

    // ------------------------------------------------------------------ crop mode

    private fun enterCrop() {
        val page = current() ?: return
        busy = true
        lifecycleScope.launch {
            val base = withContext(renderDispatcher) {
                try { ImageOps.decode(page.source, ImageOps.PREVIEW_PX)?.let { ImageOps.rotate(it, page.rotation) } } catch (e: Throwable) { null }
            }
            busy = false
            if (base == null) { toast(R.string.convert_failed); return@launch }
            cropBase = base
            cropRotation = page.rotation
            binding.cropView.setImage(base, ImageOps.rotateQuad(page.quad ?: ImageOps.FULL, page.rotation))
            cropStart = cropState()
            setCropMode(true)
        }
    }

    private fun setCropMode(on: Boolean) {
        cropMode = on
        binding.btnDone.visibility = if (on) View.GONE else View.VISIBLE
        binding.btnCropOk.visibility = if (on) View.VISIBLE else View.GONE
        binding.pager.visibility = if (on) View.INVISIBLE else View.VISIBLE
        binding.cropView.visibility = if (on) View.VISIBLE else View.GONE
        binding.filterPanel.visibility = if (on) View.GONE else View.VISIBLE
        binding.editTools.visibility = if (on) View.GONE else View.VISIBLE
        binding.cropTools.visibility = if (on) View.VISIBLE else View.GONE
        binding.btnCompare.visibility = if (on) View.INVISIBLE else View.VISIBLE
        if (!on) {
            binding.cropView.setImage(null, ImageOps.FULL)
            cropBase = null
        }
        updateCropLabel()
        updateUi()
    }

    private fun turnCrop(deg: Int) {
        val base = cropBase ?: return
        val q = binding.cropView.quad
        val turned = ImageOps.rotate(base.copy(Bitmap.Config.ARGB_8888, false), deg)
        cropBase = turned
        cropRotation = (cropRotation + deg) % 360
        binding.cropView.setImage(turned, ImageOps.rotateQuad(q, deg))
        updateCropLabel()
    }

    /** "No crop" frames the whole picture; when already whole it becomes "Auto crop". */
    private fun toggleCrop() {
        val base = cropBase ?: return
        if (!ImageOps.isFull(binding.cropView.quad)) {
            binding.cropView.setQuad(ImageOps.FULL)
            updateCropLabel()
            return
        }
        busy = true
        lifecycleScope.launch {
            val q = withContext(renderDispatcher) { try { ImageOps.detectDocument(base) } catch (e: Throwable) { null } }
            busy = false
            if (q == null) toast(R.string.no_edges_found) else binding.cropView.setQuad(q)
            updateCropLabel()
        }
    }

    private var cropStart = ""
    private fun cropState() = "$cropRotation:" + binding.cropView.quad.joinToString(",") { String.format(java.util.Locale.US, "%.3f", it) }

    private fun updateCropLabel() {
        binding.btnCropOk.isEnabled = cropMode && cropState() != cropStart
        binding.btnCropOk.alpha = if (binding.btnCropOk.isEnabled) 1f else 0.35f
        val full = ImageOps.isFull(binding.cropView.quad)
        binding.toolNoCrop.tvLabel.setText(if (full) R.string.auto_crop else R.string.no_crop)
        binding.toolNoCrop.ivIcon.setImageResource(if (full) R.drawable.ic_auto_crop else R.drawable.ic_no_crop)
    }

    private fun applyCrop() {
        val page = current() ?: return
        val q = ImageOps.unrotateQuad(binding.cropView.quad, cropRotation)
        page.rotation = ((cropRotation % 360) + 360) % 360
        page.quad = if (ImageOps.isFull(q)) null else q
        setCropMode(false)
        refreshCurrent()
    }

    // ------------------------------------------------------------------ done / back

    private fun done() {
        if (busy) return
        busy = true
        val pill = ProgressPill(this)
        pill.show(getString(R.string.processing_progress, 0))
        val list = pages.toList()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    list.forEachIndexed { i, page ->
                        if (!page.outputReady) {
                            val bmp = ImageOps.render(page, ImageOps.FINAL_PX) ?: throw IllegalStateException("page")
                            val out = ScanSession.newFile(this@ScanEditActivity, "page")
                            ImageOps.saveJpeg(bmp, out)
                            bmp.recycle()
                            page.output?.delete()
                            page.output = out
                            page.outputKey = page.key()
                        }
                        val p = (i + 1) * 100 / list.size
                        withContext(Dispatchers.Main) { pill.show(getString(R.string.processing_progress, p)) }
                    }
                    true
                } catch (e: Throwable) {
                    false
                }
            }
            pill.dismiss()
            busy = false
            if (!ok) { toast(R.string.convert_failed); return@launch }
            if (fromList) setResult(Activity.RESULT_OK)
            else startActivity(Intent(this@ScanEditActivity, ImageToPdfActivity::class.java))
            finish()
        }
    }

    private fun goBack() {
        if (cropMode) { setCropMode(false); return }
        if (fromList) {
            // leave the list as it was
            ScanSession.pages.clear()
            ScanSession.pages.addAll(snapshot)
            finish()
            return
        }
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

    override fun onDestroy() {
        renderDispatcher.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_START = "start"
        private const val EXTRA_FROM_LIST = "from_list"
        private const val PAYLOAD_THUMB = "thumb"

        fun intent(context: Context, start: Int = 0, fromList: Boolean = false) =
            Intent(context, ScanEditActivity::class.java)
                .putExtra(EXTRA_START, start)
                .putExtra(EXTRA_FROM_LIST, fromList)
    }
}
