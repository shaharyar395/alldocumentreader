package com.theoccess.alldocreader.ui.templates

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.text.StaticLayout
import android.text.TextPaint
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.data.SavedFiles
import com.theoccess.alldocreader.databinding.ActivityTemplateEditorBinding
import com.theoccess.alldocreader.databinding.ItemTplFontBinding
import com.theoccess.alldocreader.databinding.ItemTplFontLabelBinding
import com.theoccess.alldocreader.databinding.SheetConfirmBinding
import com.theoccess.alldocreader.databinding.SheetTplFormatBinding
import com.theoccess.alldocreader.ui.create.Outputs
import com.theoccess.alldocreader.ui.edit.TextEntryDialog
import com.theoccess.alldocreader.ui.pages.PdfEditor
import com.theoccess.alldocreader.ui.scan.ProgressPill
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Template editor, like the original app:
 *  - top bar: back, undo, redo, download (PNG / JPG / PDF), share;
 *  - tap any text or picture on the page to select it; tap the selected text again to change it;
 *  - nothing selected: Add text / Add image;
 *  - text selected: Fonts, Size, Color, Format (B I U S, bullets, numbers), Delete, X;
 *  - picture selected: Replace, Crop, H Flip, V Flip, Delete, X;
 *  - leaving with unsaved changes asks "Quit now?" (Discard / Save).
 */
class TemplateEditorActivity : AppCompatActivity(), TemplateCanvasView.Listener {

    companion object {
        const val EXTRA_ID = "template_id"
        const val EXTRA_SAVED = "saved"
        private const val KEY_FORMAT = "tpl_last_format"
        const val FMT_PNG = 0
        const val FMT_JPG = 1
        const val FMT_PDF = 2

        private val COLORS = intArrayOf(
            0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF8A8F98.toInt(), 0xFFFF1A1A.toInt(), 0xFF1ED41E.toInt(),
            0xFF1A1AFF.toInt(), 0xFFFF1AC6.toInt(), 0xFF1AE5F0.toInt(), 0xFFFF8A1A.toInt(), 0xFFFFD21A.toInt(),
            0xFF8E3CE8.toInt(), 0xFF8A5A2B.toInt(), 0xFF24407A.toInt(), 0xFF1E6FE6.toInt(), 0xFF1F7A45.toInt(),
            0xFFB3262B.toInt(), 0xFF6B7380.toInt(), 0xFF1B1F2A.toInt()
        )
    }

    private lateinit var b: ActivityTemplateEditorBinding
    private lateinit var page: TPage

    // undo / redo: snapshots of the page after every change
    private val history = ArrayList<TPage>()
    private var index = 0
    private var savedIndex = 0
    private val dirty get() = index != savedIndex

    private var lastSaved: File? = null
    private var replacing = false
    private var progressPill: ProgressPill? = null
    private val progress: ProgressPill get() = progressPill ?: ProgressPill(this).also { progressPill = it }
    private val hideBanner = Runnable { b.bannerSaved.visibility = View.GONE }

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val replace = replacing
        replacing = false
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { copyPicture(uri) }
            if (saved == null) { toast(R.string.tpl_image_failed); return@launch }
            val (path, bw, bh) = saved
            val sel = b.canvas.selected as? TImage
            if (replace && sel != null) {
                val cx = sel.x + sel.w / 2; val cy = sel.y + sel.h / 2
                sel.path = path; sel.res = null
                sel.crop.set(0f, 0f, 1f, 1f)
                sel.h = sel.w * bh / bw
                sel.x = cx - sel.w / 2; sel.y = cy - sel.h / 2
                b.canvas.invalidate()
                onChanged()
            } else {
                val w = 240f
                b.canvas.addCentered(TImage(null, path, 0f, 0f, w, w * bh / bw))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityTemplateEditorBinding.inflate(layoutInflater)
        setContentView(b.root)

        val tpl = TplLibrary.byId(intent.getStringExtra(EXTRA_ID) ?: "")
        if (tpl == null) { finish(); return }
        page = tpl.build()
        history.add(page.copyPage())
        b.canvas.page = page
        b.canvas.listener = this

        b.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        b.btnUndo.setOnClickListener { undo() }
        b.btnRedo.setOnClickListener { redo() }
        b.btnDownload.setOnClickListener { b.canvas.select(null); showFormatSheet() }
        b.btnShare.setOnClickListener { share() }
        b.btnBannerClose.setOnClickListener { b.bannerSaved.visibility = View.GONE }
        b.btnBannerOpen.setOnClickListener {
            b.bannerSaved.visibility = View.GONE
            lastSaved?.let { FileActions.open(this, DocFile.from(it)) }
        }

        // nothing selected
        b.btnAddText.setOnClickListener { addText() }
        b.btnAddImage.setOnClickListener { replacing = false; pick() }
        // text selected
        b.btnFonts.setOnClickListener { showPanel(b.panelFonts) }
        b.btnSize.setOnClickListener { showPanel(b.panelSize) }
        b.btnColor.setOnClickListener { showPanel(b.panelSize) }
        b.btnFormat.setOnClickListener { showPanel(b.panelFormat) }
        b.btnTextDelete.setOnClickListener { b.canvas.deleteSelected() }
        b.btnTextClose.setOnClickListener { b.canvas.select(null) }
        // picture selected
        b.btnReplace.setOnClickListener { replacing = true; pick() }
        b.btnCrop.setOnClickListener { crop() }
        b.btnFlipH.setOnClickListener { (b.canvas.selected as? TImage)?.let { it.flipH = !it.flipH; b.canvas.invalidate(); onChanged() } }
        b.btnFlipV.setOnClickListener { (b.canvas.selected as? TImage)?.let { it.flipV = !it.flipV; b.canvas.invalidate(); onChanged() } }
        b.btnImageDelete.setOnClickListener { b.canvas.deleteSelected() }
        b.btnImageClose.setOnClickListener { b.canvas.select(null) }
        // panels
        b.btnFontsClose.setOnClickListener { hidePanels() }
        b.btnSizeClose.setOnClickListener { hidePanels() }
        b.btnFormatClose.setOnClickListener { hidePanels() }
        setupFonts()
        setupSizeColor()
        setupFormat()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        updateUndo()
        showBar()
    }

    private fun pick() = pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    // ------------------------------------------------------------------ canvas callbacks

    override fun onSelectionChanged(el: TEl?) {
        hidePanels()
        showBar()
    }

    override fun onEditText(t: TText) {
        TextEntryDialog.show(this, t.text) { text ->
            if (text.isBlank()) {
                b.canvas.deleteSelected()
            } else if (text != t.text) {
                t.text = text
                b.canvas.invalidate()
                onChanged()
            }
        }
    }

    override fun onChanged() {
        while (history.size > index + 1) history.removeAt(history.size - 1)
        if (savedIndex > index) savedIndex = -1   // the saved state can't be reached any more
        history.add(page.copyPage())
        index = history.size - 1
        updateUndo()
        refreshPanels()
    }

    private fun undo() {
        if (index == 0) return
        index--
        restore()
    }

    private fun redo() {
        if (index >= history.size - 1) return
        index++
        restore()
    }

    private fun restore() {
        val sel = b.canvas.selectedIndex()
        page = history[index].copyPage()
        b.canvas.page = page
        b.canvas.selectIndex(sel)
        updateUndo()
    }

    private fun updateUndo() {
        b.btnUndo.isEnabled = index > 0
        b.btnUndo.alpha = if (index > 0) 1f else 0.3f
        val canRedo = index < history.size - 1
        b.btnRedo.isEnabled = canRedo
        b.btnRedo.alpha = if (canRedo) 1f else 0.3f
    }

    // ------------------------------------------------------------------ bars & panels

    private fun showBar() {
        val sel = b.canvas.selected
        val panelOpen = b.panelFonts.visibility == View.VISIBLE || b.panelSize.visibility == View.VISIBLE || b.panelFormat.visibility == View.VISIBLE
        b.barMain.visibility = if (sel == null && !panelOpen) View.VISIBLE else View.GONE
        b.barText.visibility = if (sel is TText && !panelOpen) View.VISIBLE else View.GONE
        b.barImage.visibility = if (sel is TImage && !panelOpen) View.VISIBLE else View.GONE
    }

    private fun showPanel(panel: View) {
        if (b.canvas.selected !is TText) return
        b.panelFonts.visibility = View.GONE
        b.panelSize.visibility = View.GONE
        b.panelFormat.visibility = View.GONE
        panel.visibility = View.VISIBLE
        refreshPanels()
        showBar()
        if (panel === b.panelFonts) scrollFontsToCurrent()
    }

    private fun hidePanels() {
        b.panelFonts.visibility = View.GONE
        b.panelSize.visibility = View.GONE
        b.panelFormat.visibility = View.GONE
        showBar()
    }

    private fun refreshPanels() {
        val t = b.canvas.selected as? TText ?: return
        fontAdapter.notifyDataSetChanged()
        settingSeek = true
        b.seekSize.progress = (t.size.toInt() - 6).coerceIn(0, b.seekSize.max)
        settingSeek = false
        b.tvSizeValue.text = t.size.toInt().toString()
        colorAdapter.notifyDataSetChanged()
        b.btnBold.isSelected = t.bold
        b.btnItalic.isSelected = t.italic
        b.btnUnderline.isSelected = t.underline
        b.btnStrike.isSelected = t.strike
        b.btnBullets.isSelected = t.list == 1
        b.btnNumbers.isSelected = t.list == 2
    }

    // ---- Fonts: "Current font", then every font in its own style, with an A–Z index

    private sealed class FontRow {
        class Label(val text: String) : FontRow()
        class Item(val font: TplFonts.Font, val current: Boolean) : FontRow()
    }

    private val fontRows: List<FontRow> by lazy {
        buildList {
            add(FontRow.Label(getString(R.string.tpl_current_font)))
            add(FontRow.Item(TplFonts.ALL.first(), true))   // replaced on bind by the selected text's font
            add(FontRow.Label(getString(R.string.tpl_fonts)))
            TplFonts.ALL.forEach { add(FontRow.Item(it, false)) }
        }
    }

    private val fontAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = fontRows.size
        override fun getItemViewType(position: Int) = if (fontRows[position] is FontRow.Label) 0 else 1
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            val v = if (viewType == 0) ItemTplFontLabelBinding.inflate(inf, parent, false).root else ItemTplFontBinding.inflate(inf, parent, false).root
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val t = b.canvas.selected as? TText
            val currentName = t?.font ?: TplFonts.DEFAULT
            when (val r = fontRows[position]) {
                is FontRow.Label -> (holder.itemView as TextView).text = r.text
                is FontRow.Item -> {
                    val tv = holder.itemView as TextView
                    val name = if (r.current) currentName else r.font.name
                    tv.text = name
                    tv.typeface = TplFonts.typeface(name, false, false)
                    val on = name == currentName
                    tv.setTextColor(if (on) getColor(R.color.primary) else getColor(R.color.text_primary))
                    tv.setOnClickListener {
                        val sel = b.canvas.selected as? TText ?: return@setOnClickListener
                        if (sel.font != name) {
                            sel.font = name
                            b.canvas.invalidate()
                            onChanged()
                        }
                    }
                }
            }
        }
    }

    private fun setupFonts() {
        b.rvFonts.layoutManager = LinearLayoutManager(this)
        b.rvFonts.adapter = fontAdapter
        for (c in 'A'..'Z') {
            val tv = TextView(this).apply {
                text = c.toString()
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.text_secondary))
                setOnClickListener { jumpToLetter(c) }
            }
            b.fontIndex.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }

    private fun jumpToLetter(c: Char) {
        val start = fontRows.indexOfLast { it is FontRow.Label }
        var pos = -1
        for (i in start + 1 until fontRows.size) {
            val r = fontRows[i] as FontRow.Item
            if (r.font.name.first().uppercaseChar() >= c) { pos = i; break }
        }
        if (pos < 0) pos = fontRows.size - 1
        (b.rvFonts.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
    }

    private fun scrollFontsToCurrent() {
        (b.rvFonts.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(0, 0)
    }

    // ---- Size & Color

    private var settingSeek = false
    private var sizeChanged = false

    private val colorAdapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = COLORS.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = parent.context
            val ring = FrameLayout(ctx)
            ring.layoutParams = RecyclerView.LayoutParams(ctx.dp(44), ctx.dp(44))
            val dot = View(ctx)
            ring.addView(dot, FrameLayout.LayoutParams(ctx.dp(32), ctx.dp(32), Gravity.CENTER))
            return object : RecyclerView.ViewHolder(ring) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val color = COLORS[position]
            val ring = holder.itemView as FrameLayout
            val dot = ring.getChildAt(0)
            val t = b.canvas.selected as? TText
            val on = t != null && t.color == color
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                if (color == Color.WHITE) setStroke(dp(1), 0xFFD5DAE2.toInt())
            }
            ring.background = if (on) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(2), getColor(R.color.primary))
            } else null
            ring.setOnClickListener {
                val sel = b.canvas.selected as? TText ?: return@setOnClickListener
                if (sel.color != color) {
                    sel.color = color
                    b.canvas.invalidate()
                    onChanged()
                }
            }
        }
    }

    private fun setupSizeColor() {
        b.seekSize.max = 128 - 6
        b.seekSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (settingSeek || !fromUser) return
                val t = b.canvas.selected as? TText ?: return
                t.size = (progress + 6).toFloat()
                b.tvSizeValue.text = (progress + 6).toString()
                sizeChanged = true
                b.canvas.invalidate()
            }
            override fun onStartTrackingTouch(sb: SeekBar) { sizeChanged = false }
            override fun onStopTrackingTouch(sb: SeekBar) { if (sizeChanged) onChanged() }
        })
        b.rvColors.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        b.rvColors.adapter = colorAdapter
    }

    // ---- Format

    private fun setupFormat() {
        fun edit(change: (TText) -> Unit) {
            val t = b.canvas.selected as? TText ?: return
            change(t)
            b.canvas.invalidate()
            onChanged()
        }
        b.btnBold.setOnClickListener { edit { it.bold = !it.bold } }
        b.btnItalic.setOnClickListener { edit { it.italic = !it.italic } }
        b.btnUnderline.setOnClickListener { edit { it.underline = !it.underline } }
        b.btnStrike.setOnClickListener { edit { it.strike = !it.strike } }
        b.btnBullets.setOnClickListener { edit { it.list = if (it.list == 1) 0 else 1 } }
        b.btnNumbers.setOnClickListener { edit { it.list = if (it.list == 2) 0 else 2 } }
    }

    // ------------------------------------------------------------------ add / pictures

    private fun addText() {
        TextEntryDialog.show(this, "") { text ->
            if (text.isBlank()) return@show
            val size = 18f
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; typeface = TplFonts.typeface(TplFonts.DEFAULT, false, false) }
            val widest = text.split('\n').maxOf { StaticLayout.getDesiredWidth(it, paint) }
            val w = min(PAGE_W - 40f, max(40f, widest + 6f))
            b.canvas.addCentered(TText(text, 0f, 0f, w, size))
        }
    }

    /** Copies the picked picture into the app (so the template keeps working) → path, width, height. */
    private fun copyPicture(uri: android.net.Uri): Triple<String, Float, Float>? {
        val bmp = PdfEditor.decode(this, uri, 1600) ?: return null
        return try {
            val dir = File(filesDir, "tpl_images").apply { mkdirs() }
            val png = bmp.hasAlpha()
            val f = File(dir, "img_${System.currentTimeMillis()}." + if (png) "png" else "jpg")
            FileOutputStream(f).use { bmp.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
            Triple(f.absolutePath, bmp.width.toFloat(), bmp.height.toFloat())
        } catch (e: Exception) {
            null
        }
    }

    private fun crop() {
        val im = b.canvas.selected as? TImage ?: return
        val bmp = TplRenderer.bitmap(this, im) ?: return
        TplCropDialog.show(this, bmp, im.crop) { r ->
            val cx = im.x + im.w / 2; val cy = im.y + im.h / 2
            im.crop.set(r)
            val aspect = (r.width() * bmp.width) / max(1f, r.height() * bmp.height)
            im.h = im.w / aspect
            im.x = cx - im.w / 2; im.y = cy - im.h / 2
            b.canvas.invalidate()
            onChanged()
        }
    }

    // ------------------------------------------------------------------ saving

    private fun showFormatSheet(afterSave: (() -> Unit)? = null) {
        val dialog = BottomSheetDialog(this, R.style.Theme_DocReader_BottomSheet)
        val s = SheetTplFormatBinding.inflate(layoutInflater)
        var fmt = Prefs.raw.getInt(KEY_FORMAT, FMT_JPG)
        val rows = listOf(s.rowPng to s.rowPngRadio, s.rowJpg to s.rowJpgRadio, s.rowPdf to s.rowPdfRadio)
        val titles = listOf(s.rowPngTitle, s.rowJpgTitle, s.rowPdfTitle)
        fun refresh() = rows.forEachIndexed { i, (row, radio) ->
            row.isSelected = i == fmt
            titles[i].isSelected = i == fmt
            radio.isChecked = i == fmt
        }
        rows.forEachIndexed { i, (row, _) -> row.setOnClickListener { fmt = i; refresh() } }
        refresh()
        s.btnSave.setOnClickListener {
            Prefs.raw.edit().putInt(KEY_FORMAT, fmt).apply()
            dialog.dismiss()
            save(fmt, afterSave)
        }
        dialog.setContentView(s.root)
        dialog.show()
    }

    private fun save(fmt: Int, afterSave: (() -> Unit)?) {
        val snapshot = page.copyPage()
        progress.show(getString(R.string.saving))
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                try {
                    val ext = when (fmt) { FMT_PNG -> "png"; FMT_JPG -> "jpg"; else -> "pdf" }
                    val name = Outputs.defaultName()
                    val temp = File(File(cacheDir, "tpl_out").apply { mkdirs() }, "$name.$ext")
                    export(snapshot, fmt, temp)
                    // phone Documents/AllDocumentReader/create; SavedFiles then adds it to Recent and Drive
                    val f = Outputs.publish(applicationContext, temp, "create", name, ext)
                    SavedFiles.onSaved(applicationContext, f)
                    f
                } catch (e: Throwable) {
                    null
                }
            }
            progress.dismiss()
            if (out == null) { toast(R.string.tpl_save_failed); return@launch }
            lastSaved = out
            savedIndex = index
            if (afterSave != null) {
                com.theoccess.alldocreader.ads.Ads.showInterstitial(this@TemplateEditorActivity) { afterSave() }
                return@launch
            }
            b.bannerSaved.removeCallbacks(hideBanner)
            b.bannerSaved.visibility = View.VISIBLE
            b.bannerSaved.postDelayed(hideBanner, 5000)
            com.theoccess.alldocreader.ads.Ads.showInterstitial(this@TemplateEditorActivity)
        }
    }

    /** Draws the page into a PNG / JPG (2× A4 in pixels) or a one-page PDF. */
    private fun export(p: TPage, fmt: Int, file: File) {
        if (fmt == FMT_PDF) {
            val doc = PdfDocument()
            try {
                val pg = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), 1).create())
                TplRenderer.draw(applicationContext, pg.canvas, p)
                doc.finishPage(pg)
                FileOutputStream(file).use { doc.writeTo(it) }
            } finally {
                doc.close()
            }
        } else {
            val s = 2f
            val bmp = Bitmap.createBitmap((PAGE_W * s).toInt(), (PAGE_H * s).toInt(), Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.WHITE)
            c.scale(s, s)
            TplRenderer.draw(applicationContext, c, p)
            FileOutputStream(file).use {
                bmp.compress(if (fmt == FMT_PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 95, it)
            }
            bmp.recycle()
        }
    }

    private fun share() {
        b.canvas.select(null)
        val snapshot = page.copyPage()
        progress.show(getString(R.string.saving))
        lifecycleScope.launch {
            val f = withContext(Dispatchers.IO) {
                try {
                    val dir = File(cacheDir, "share").apply { mkdirs() }
                    File(dir, Outputs.defaultName() + ".pdf").also { export(snapshot, FMT_PDF, it) }
                } catch (e: Throwable) {
                    null
                }
            }
            progress.dismiss()
            if (f == null) toast(R.string.tpl_save_failed) else FileActions.share(this@TemplateEditorActivity, DocFile.from(f))
        }
    }

    // ------------------------------------------------------------------ leaving

    private fun goBack() {
        when {
            b.panelFonts.visibility == View.VISIBLE || b.panelSize.visibility == View.VISIBLE || b.panelFormat.visibility == View.VISIBLE -> hidePanels()
            b.canvas.selected != null -> b.canvas.select(null)
            dirty -> showQuitSheet()
            else -> finish()
        }
    }

    private fun showQuitSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_DocReader_BottomSheet)
        val s = SheetConfirmBinding.inflate(layoutInflater)
        s.tvTitle.setText(R.string.tpl_quit_title)
        s.tvMessage.setText(R.string.tpl_quit_message)
        s.btnCancel.setText(R.string.discard)
        s.btnOk.setText(R.string.tpl_save)
        s.btnCancel.setOnClickListener { dialog.dismiss(); finish() }
        s.btnOk.setOnClickListener {
            dialog.dismiss()
            save(Prefs.raw.getInt(KEY_FORMAT, FMT_JPG)) {
                setResult(RESULT_OK, Intent().putExtra(EXTRA_SAVED, true))
                finish()
            }
        }
        dialog.setContentView(s.root)
        dialog.show()
    }

    override fun onDestroy() {
        if (::b.isInitialized) b.bannerSaved.removeCallbacks(hideBanner)
        progressPill?.dismiss()
        super.onDestroy()
    }
}
