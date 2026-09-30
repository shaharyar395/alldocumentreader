package com.theoccess.alldocreader.ui.pages

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityInsertBlankBinding
import com.theoccess.alldocreader.databinding.ItemSizeCardBinding
import com.theoccess.alldocreader.databinding.ItemTemplatePageBinding
import com.theoccess.alldocreader.databinding.SheetPageSizeBinding
import com.theoccess.alldocreader.ui.files.FileSheets
import androidx.lifecycle.lifecycleScope
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.ui.create.Outputs
import com.theoccess.alldocreader.ui.viewer.Converters
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.theoccess.alldocreader.util.dp
import android.content.res.ColorStateList
import kotlin.math.abs

/** "Insert blank pages": template, page size, colour, orientation and count. */
class InsertBlankPagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInsertBlankBinding
    private var template = 0
    /** null = "Follow previous". */
    private var size: PageSize? = null
    private var prevW = 420f
    private var prevH = 595f
    private var color = Templates.COLORS[0]
    private var landscape = false
    private var count = 1
    /** "Create PDF" from the + sheet instead of inserting into an open PDF. */
    private var createMode = false
    private var fileName = ""
    private var creating = false
    private val stripViews = ArrayList<ImageView>()
    private val colorViews = ArrayList<FrameLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsertBlankBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prevW = intent.getFloatExtra(EXTRA_PREV_W, 420f)
        prevH = intent.getFloatExtra(EXTRA_PREV_H, 595f)
        landscape = prevW > prevH
        createMode = intent.getBooleanExtra(EXTRA_CREATE, false)
        if (createMode) {
            size = PageSize.ALL[1] // A4
            landscape = false
            fileName = Outputs.defaultName()
            binding.tvTitle.setText(R.string.create_pdf)
            binding.btnInsert.setText(R.string.create)
            binding.rowFileName.visibility = View.VISIBLE
            binding.rowFileName.setOnClickListener {
                FileSheets.askText(this, getString(R.string.file_name), fileName) { fileName = Outputs.clean(it); refresh() }
            }
        }

        binding.btnBack.setOnClickListener { finish() }
        setupPager()
        setupStrip()
        setupColors()
        binding.rowSize.setOnClickListener { showSizeSheet() }
        binding.btnPortrait.setOnClickListener { landscape = false; refresh() }
        binding.btnLandscape.setOnClickListener { landscape = true; refresh() }
        binding.btnMinus.setOnClickListener { if (count > 1) { count--; refresh() } }
        binding.btnPlus.setOnClickListener { if (count < MAX_COUNT) { count++; refresh() } }
        binding.tvCount.setOnClickListener {
            FileSheets.askNumber(this, getString(R.string.page_count), count, 1, MAX_COUNT) { count = it; refresh() }
        }
        binding.btnInsert.setOnClickListener {
            if (createMode) { createPdf(); return@setOnClickListener }
            val spec = currentSpec(template)
            setResult(Activity.RESULT_OK, Intent()
                .putExtra(EXTRA_TEMPLATE, spec.template)
                .putExtra(EXTRA_W, spec.widthPt)
                .putExtra(EXTRA_H, spec.heightPt)
                .putExtra(EXTRA_COLOR, spec.color)
                .putExtra(EXTRA_COUNT, count))
            finish()
        }
        refresh()
    }

    /** Size in points after orientation. */
    private fun currentSpec(tpl: Int): BlankSpec {
        var w = size?.widthPt ?: prevW
        var h = size?.heightPt ?: prevH
        val isLandscape = w > h
        if (landscape != isLandscape) { val t = w; w = h; h = t }
        return BlankSpec(tpl, w, h, color)
    }

    private fun dimsText(spec: BlankSpec) =
        "${PageSize.ptToMm(spec.widthPt).toInt()}x${PageSize.ptToMm(spec.heightPt).toInt()}mm"

    private fun setupPager() {
        binding.pager.offscreenPageLimit = 2
        (binding.pager.getChildAt(0) as? RecyclerView)?.apply { clipToPadding = false; clipChildren = false }
        binding.pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = Templates.ALL.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                object : RecyclerView.ViewHolder(
                    ItemTemplatePageBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
                ) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ib = ItemTemplatePageBinding.bind(holder.itemView)
                val spec = currentSpec(position)
                ib.preview.spec = spec
                ib.preview.title = getString(Templates.ALL[position].name)
                ib.preview.subtitle = dimsText(spec)
                ib.root.setOnClickListener { binding.pager.currentItem = position }
            }
        }
        binding.pager.setPageTransformer { page, pos ->
            val s = 1f - 0.12f * abs(pos)
            page.scaleX = s
            page.scaleY = s
            page.alpha = 1f - 0.3f * abs(pos)
        }
        binding.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                template = position
                updateStrip()
            }
        })
    }

    private fun setupStrip() {
        Templates.ALL.forEachIndexed { i, t ->
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(30), dp(34)).apply { marginStart = dp(2); marginEnd = dp(2) }
                setPadding(dp(3), dp(3), dp(3), dp(3))
                setImageResource(t.icon)
                contentDescription = getString(t.name)
                setOnClickListener { binding.pager.currentItem = i }
            }
            binding.strip.addView(iv)
            stripViews += iv
        }
        updateStrip()
    }

    private fun updateStrip() {
        stripViews.forEachIndexed { i, v ->
            v.background = if (i == template) ContextCompat.getDrawable(this, R.drawable.bg_strip_selected) else null
        }
    }

    private fun setupColors() {
        Templates.COLORS.forEachIndexed { i, c ->
            val f = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginStart = dp(10) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(c)
                    setStroke(dp(1), 0xFFD5DAE1.toInt())
                }
                setOnClickListener { color = Templates.COLORS[i]; refresh() }
            }
            val check = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(16), dp(16), android.view.Gravity.CENTER)
                setImageResource(R.drawable.ic_check)
                ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(0xFF1B1F2A.toInt()))
            }
            f.addView(check)
            binding.colors.addView(f)
            colorViews += f
        }
    }

    /** Builds the new PDF in Documents/AllDocumentReader/create and opens it. */
    private fun createPdf() {
        if (creating) return
        creating = true
        binding.btnInsert.isEnabled = false
        val spec = currentSpec(template)
        val pages = count
        lifecycleScope.launch {
            val out = try {
                withContext(Dispatchers.IO) {
                    val f = Converters.uniqueFile(Outputs.createDir(), fileName, "pdf")
                    PdfEditor.createBlank(f, spec, pages)
                    com.theoccess.alldocreader.data.SavedFiles.onSaved(applicationContext, f)
                    f
                }
            } catch (e: Throwable) {
                null
            }
            creating = false
            binding.btnInsert.isEnabled = true
            if (out == null) {
                toast(R.string.save_failed)
                return@launch
            }
            FileRepository.refresh(applicationContext, force = true)
            FileActions.open(this@InsertBlankPagesActivity, DocFile.from(out))
            finish()
        }
    }

    private fun refresh() {
        binding.tvFileName.text = "$fileName.pdf"
        binding.tvSize.text = size?.let { getString(it.label) } ?: getString(R.string.follow_previous)
        colorViews.forEachIndexed { i, v -> v.getChildAt(0).visibility = if (Templates.COLORS[i] == color) View.VISIBLE else View.INVISIBLE }
        binding.btnPortrait.isSelected = !landscape
        binding.btnLandscape.isSelected = landscape
        binding.tvCount.text = count.toString()
        binding.btnMinus.isEnabled = count > 1
        binding.btnPlus.isEnabled = count < MAX_COUNT
        binding.pager.adapter?.notifyDataSetChanged()
    }

    private fun showSizeSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_DocReader_BottomSheet)
        val b = SheetPageSizeBinding.inflate(LayoutInflater.from(this))
        var chosen: PageSize? = size
        val options: List<PageSize?> = if (createMode) PageSize.ALL else listOf<PageSize?>(null) + PageSize.ALL
        val cards = ArrayList<ItemSizeCardBinding>()

        fun render() {
            cards.forEachIndexed { i, c ->
                val on = options[i] == chosen
                c.box.isSelected = on
                c.tvPage.isSelected = on
                c.tvPage.setTextColor(ContextCompat.getColor(this, if (on) R.color.primary else R.color.text_primary))
            }
        }

        options.chunked(4).forEach { rowOptions ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowOptions.forEach { opt ->
                val c = ItemSizeCardBinding.inflate(LayoutInflater.from(this), row, false)
                if (opt == null) {
                    c.tvPage.text = getString(R.string.follow_previous)
                    c.tvPage.layoutParams = c.tvPage.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = ViewGroup.LayoutParams.MATCH_PARENT }
                    c.tvPage.background = null
                    c.tvDims.text = "${PageSize.ptToMm(prevW).toInt()}x${PageSize.ptToMm(prevH).toInt()}mm"
                } else {
                    c.tvPage.text = getString(opt.label)
                    c.tvDims.text = opt.dims
                    // mini page keeps the real proportions
                    val h = dp(46)
                    c.tvPage.layoutParams = c.tvPage.layoutParams.apply {
                        height = h
                        width = (h * opt.widthMm / opt.heightMm).toInt()
                    }
                }
                c.root.setOnClickListener { chosen = opt; render() }
                row.addView(c.root)
                cards += c
            }
            b.grid.addView(row)
        }
        render()
        b.btnClose.setOnClickListener { dialog.dismiss() }
        b.btnApply.setOnClickListener {
            size = chosen
            dialog.dismiss()
            refresh()
        }
        dialog.setContentView(b.root)
        dialog.show()
    }

    companion object {
        private const val MAX_COUNT = 100
        private const val EXTRA_PREV_W = "prev_w"
        private const val EXTRA_PREV_H = "prev_h"
        private const val EXTRA_TEMPLATE = "template"
        private const val EXTRA_W = "w"
        private const val EXTRA_H = "h"
        private const val EXTRA_COLOR = "color"
        const val EXTRA_COUNT = "count"
        private const val EXTRA_CREATE = "create"

        /** "Create PDF" mode (file name + Create button). */
        fun createIntent(context: Context) =
            Intent(context, InsertBlankPagesActivity::class.java)
                .putExtra(EXTRA_CREATE, true)
                .putExtra(EXTRA_PREV_W, PageSize.ALL[1].widthPt)
                .putExtra(EXTRA_PREV_H, PageSize.ALL[1].heightPt)

        fun intent(context: Context, prevWidthPt: Float, prevHeightPt: Float) =
            Intent(context, InsertBlankPagesActivity::class.java)
                .putExtra(EXTRA_PREV_W, prevWidthPt)
                .putExtra(EXTRA_PREV_H, prevHeightPt)

        fun specFrom(data: Intent): BlankSpec? {
            if (!data.hasExtra(EXTRA_W)) return null
            return BlankSpec(
                data.getIntExtra(EXTRA_TEMPLATE, 0),
                data.getFloatExtra(EXTRA_W, 595f),
                data.getFloatExtra(EXTRA_H, 842f),
                data.getIntExtra(EXTRA_COLOR, Templates.COLORS[0])
            )
        }
    }
}
