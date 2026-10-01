package com.theoccess.alldocreader.ui.edit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.ImageViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.databinding.ActivityPdfEditBinding
import com.theoccess.alldocreader.databinding.ItemBottomToolBinding
import com.theoccess.alldocreader.databinding.ItemEditPageBinding
import com.theoccess.alldocreader.databinding.SheetConfirmBinding
import com.theoccess.alldocreader.ui.pages.PdfEditor
import com.theoccess.alldocreader.ui.viewer.Converters
import com.theoccess.alldocreader.util.TopPill
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.toast
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * PDF editor (Tools → Edit, the red pen in the reader, and the result screen tools):
 * Edit text (replace real text lines), Add text (tap to type; move / rotate / resize / delete),
 * Add image, Annotate (pen, highlight, underline, strikethrough, eraser; colour and size),
 * Sign (signature pad, saved signatures). Undo / redo, Done saves into the PDF and stays open
 * with "Saved successfully"; "i" shows the animated how-to.
 */
class PdfEditActivity : AppCompatActivity(), EditHost {

    enum class Mode { NONE, EDIT_TEXT, ADD_TEXT, ANNOTATE, SIGN }

    private lateinit var binding: ActivityPdfEditBinding
    private lateinit var file: File
    private var renderer: PdfRenderer? = null
    private var fd: ParcelFileDescriptor? = null
    private var sizes: List<Pair<Float, Float>> = emptyList()
    private val renderDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val cache = object : LruCache<Int, Bitmap>((Runtime.getRuntime().maxMemory() / 6).toInt()) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
    }
    private val adapter = PageAdapter()

    private val pageItems = HashMap<Int, MutableList<EditItem>>()
    private var textLines: MutableMap<Int, MutableList<TextLine>> = HashMap()
    private var linesLoaded = false
    private var panel = Mode.NONE
    private var annTool = EditTool.PEN
    private var saving = false

    override var selected: EditItem? = null

    // tool settings
    private val palette = intArrayOf(
        0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF8A8F98.toInt(), 0xFFE53935.toInt(), 0xFF22C55E.toInt(),
        0xFF1E3FE6.toInt(), 0xFFE91E8C.toInt(), 0xFF22D3EE.toInt(), 0xFFFF6A13.toInt(), 0xFFFBBF24.toInt(),
        0xFFA3E635.toInt(), 0xFF2DD4BF.toInt(), 0xFF38BDF8.toInt(), 0xFF8B3CF6.toInt(), 0xFFF87171.toInt()
    )
    private var textColor = 0xFFE91E8C.toInt()
    private var textSizeValue = 72
    private var annColor = 0xFFE53935.toInt()
    private var annSize = 30

    // history
    private val undoStack = ArrayDeque<Map<Int, List<EditItem>>>()
    private val redoStack = ArrayDeque<Map<Int, List<EditItem>>>()
    private var last: Map<Int, List<EditItem>> = emptyMap()

    /** Picture / signature being replaced with the pencil handle (null = add a new one). */
    private var replaceTarget: ImageItem? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val target = replaceTarget
        replaceTarget = null
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { PdfEditor.decode(this@PdfEditActivity, uri, 1600) }
            if (bmp == null) { toast(R.string.convert_failed); return@launch }
            if (target != null) replaceBitmap(target, bmp) else placeImage(bmp, 0.5f)
        }
    }

    private val signPad = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val target = replaceTarget
        replaceTarget = null
        val path = r.data?.getStringExtra(SignaturePadActivity.EXTRA_PATH)
        if (r.resultCode == Activity.RESULT_OK && path != null) {
            refreshSignatures()
            SignatureStore.load(File(path))?.let { if (target != null) replaceBitmap(target, it) else placeImage(it, 0.35f, signature = true) }
        }
    }

    private val textSizes = intArrayOf(6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 36, 48, 56, 72)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPdfEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        file = File(intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return })
        if (!openRenderer()) { toast(R.string.pdf_password_protected); finish(); return }

        binding.btnClose.setOnClickListener { close() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = if (panel != Mode.NONE) openPanel(Mode.NONE) else close()
        })
        binding.btnInfo.setOnClickListener { showTutorial() }
        binding.btnUndo.setOnClickListener { undo() }
        binding.btnRedo.setOnClickListener { redo() }
        binding.btnDone.setOnClickListener { save() }

        tool(binding.toolEditText, R.drawable.ic_et_edit_text, R.string.edit_text) {
            if (!linesLoaded) toast(R.string.loading)
            else if (textLines.isEmpty()) toast(R.string.no_editable_text)
            else openPanel(Mode.EDIT_TEXT)
        }
        tool(binding.toolAddText, R.drawable.ic_et_add_text, R.string.add_text) { openPanel(Mode.ADD_TEXT) }
        tool(binding.toolAddImage, R.drawable.ic_et_add_image, R.string.add_image) {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        tool(binding.toolAnnotate, R.drawable.ic_et_annotate, R.string.annotate) { openPanel(Mode.ANNOTATE) }
        tool(binding.toolSign, R.drawable.ic_et_sign, R.string.sign) { openPanel(Mode.SIGN) }

        setupTextPanel()
        setupAnnotatePanel()
        setupSignPanel()

        binding.rvPages.layoutManager = LinearLayoutManager(this)
        binding.rvPages.adapter = adapter
        binding.pbLoading.visibility = View.GONE
        loadLines(initial = Mode.entries.getOrElse(intent.getIntExtra(EXTRA_MODE, 0)) { Mode.NONE })
        binding.rvPages.post { scrollToPage(intent.getIntExtra(EXTRA_PAGE, 0)) }
        updateBars()
    }

    private fun tool(t: ItemBottomToolBinding, icon: Int, label: Int, onClick: () -> Unit) {
        t.ivIcon.setImageResource(icon)
        t.tvLabel.setText(label)
        t.root.setOnClickListener { if (!saving) onClick() }
    }

    private fun openRenderer(): Boolean = try {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        fd = pfd
        val r = PdfRenderer(pfd)
        renderer = r
        sizes = (0 until r.pageCount).map { i -> r.openPage(i).use { it.width.toFloat() to it.height.toFloat() } }
        sizes.isNotEmpty()
    } catch (e: Exception) {
        false
    }

    @Synchronized
    private fun closeRenderer() {
        try { renderer?.close() } catch (ignored: Exception) {}
        try { fd?.close() } catch (ignored: Exception) {}
        renderer = null
        fd = null
    }

    /** Real text lines (for Edit text); then opens the panel the screen was started with. */
    private fun loadLines(initial: Mode) {
        linesLoaded = false
        lifecycleScope.launch {
            val found = withContext(Dispatchers.IO) {
                try { PdfTextLines.extract(file) } catch (e: Throwable) { emptyMap() }
            }
            textLines = found.mapValues { it.value.toMutableList() }.toMutableMap()
            linesLoaded = true
            binding.toolEditText.root.alpha = if (textLines.isEmpty()) 0.4f else 1f
            when (initial) {
                Mode.EDIT_TEXT -> if (textLines.isEmpty()) {
                    toast(R.string.no_editable_text)
                    openPanel(Mode.ADD_TEXT)
                } else openPanel(Mode.EDIT_TEXT)
                Mode.NONE -> Unit
                else -> openPanel(initial)
            }
        }
    }

    // ------------------------------------------------------------------ EditHost

    override val tool: EditTool
        get() = when (panel) {
            Mode.NONE -> if (pageItems.values.any { list -> list.any { it is TextItem || it is ImageItem } }) EditTool.OBJECTS else EditTool.NONE
            Mode.EDIT_TEXT -> EditTool.EDIT_TEXT
            Mode.ADD_TEXT, Mode.SIGN -> EditTool.OBJECTS
            Mode.ANNOTATE -> annTool
        }

    override fun items(page: Int): MutableList<EditItem> = pageItems.getOrPut(page) { ArrayList() }
    /** Text lines still editable: the ones not already covered by an edited-text box (undo brings them back). */
    override fun lines(page: Int): List<TextLine> {
        val all = textLines[page] ?: return emptyList()
        val covers = pageItems[page]?.mapNotNull { (it as? TextItem)?.cover }.orEmpty()
        if (covers.isEmpty()) return all
        return all.filter { l -> covers.none { c -> c.contains(l.rect.centerX(), l.rect.centerY()) } }
    }
    override fun penColor() = annColor
    override fun penWidth() = (1f + annSize / 100f * 14f) / 600f
    override fun markColor() = if (annTool == EditTool.HIGHLIGHT && annColor == 0xFFE53935.toInt()) 0xFFFBBF24.toInt() else annColor

    override fun onTapEmpty(page: Int, x: Float, y: Float) {
        if (panel != Mode.ADD_TEXT) return
        TextEntryDialog.show(this, "") { text -> addText(page, text, x, y, centred = false) }
    }

    /** The "Tap anywhere to add text" bar: type first, the text lands in the middle of the page on screen. */
    private fun addTextFromBar() {
        if (panel == Mode.EDIT_TEXT) { toast(R.string.tap_text_to_edit); return }
        val page = currentPage()
        val (pw, ph) = sizes.getOrElse(page) { 595f to 842f }
        TextEntryDialog.show(this, "") { text -> addText(page, text, 0.5f, visibleCentreY(page, ph / pw), centred = true) }
    }

    private fun addText(page: Int, text: String, x: Float, y: Float, centred: Boolean) {
        val t = text.trim()
        if (t.isEmpty()) return
        val size = textSizeValue / 600f
        val width = EditRenderer.measure(t, size).coerceAtMost(0.96f)
        val cx = if (centred) 0.5f else (x + width / 2).coerceIn(width / 2, 1f - width / 2 + 0.2f)
        val item = TextItem(t, cx, y, width, size, textColor)
        items(page).add(item)
        selected = item
        commit()
    }

    /** Middle of the part of [page] that is on screen, in page-width units. */
    private fun visibleCentreY(page: Int, pageHeight: Float): Float {
        val v = (binding.rvPages.layoutManager as LinearLayoutManager).findViewByPosition(page) ?: return pageHeight / 2
        val w = v.width.coerceAtLeast(1).toFloat()
        // the page (and its edit layer) sits inside the row's vertical padding
        val offset = v.top + v.paddingTop
        val top = max(0, -offset).toFloat()
        val bottom = min(v.height - v.paddingTop - v.paddingBottom, binding.rvPages.height - offset).toFloat()
        return ((top + bottom) / 2f / w).coerceIn(0.05f, max(0.05f, pageHeight - 0.05f))
    }

    override fun onEditTextItem(page: Int, item: TextItem) {
        TextEntryDialog.show(this, item.text) { text ->
            val t = text.trim()
            if (t.isEmpty()) { items(page).remove(item); if (selected === item) selected = null }
            else {
                item.text = t
                item.width = max(item.width, EditRenderer.measure(t, item.size).coerceAtMost(0.96f))
            }
            commit()
        }
    }

    override fun onLineTap(page: Int, line: TextLine) {
        TextEntryDialog.show(this, line.text) { text ->
            val t = text.trim()
            val cover = android.graphics.RectF(line.rect.left - 0.004f, line.rect.top - 0.003f, line.rect.right + 0.004f, line.rect.bottom + 0.003f)
            val size = (line.size * 0.95f).coerceAtLeast(0.008f)
            val width = max(line.rect.width(), EditRenderer.measure(t.ifEmpty { " " }, size)).coerceAtMost(1f)
            val item = TextItem(t.ifEmpty { " " }, line.rect.left + width / 2, (line.rect.top + line.rect.bottom) / 2, width, size,
                if (textColor == 0xFFFFFFFF.toInt()) Color.BLACK else textColor, cover = cover)
            items(page).add(item)
            selected = item
            commit()
        }
    }

    override fun onDelete(page: Int, item: EditItem) {
        items(page).remove(item)
        if (selected === item) selected = null
        commit()
    }

    override fun onDuplicate(page: Int, item: EditItem) {
        val copy = item.copy()
        when (copy) {
            is TextItem -> { copy.cx += 0.04f; copy.cy += 0.06f }
            is ImageItem -> { copy.cx += 0.04f; copy.cy += 0.06f }
            else -> Unit
        }
        items(page).add(copy)
        selected = copy
        onSelectionChanged()
        commit()
    }

    /** Pencil on a picture / signature: draw a new signature or pick a new picture in its place. */
    override fun onEditImage(page: Int, item: ImageItem) {
        replaceTarget = item
        if (item.signature) signPad.launch(SignaturePadActivity.intent(this))
        else pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    private fun replaceBitmap(item: ImageItem, bmp: Bitmap) {
        item.bitmap = bmp
        selected = item
        commit()
    }

    override fun onSelectionChanged() {
        (selected as? TextItem)?.let { t ->
            textColor = t.color
            textSizeValue = (t.size * 600f).toInt().coerceIn(6, 72)
            refreshTextPanel()
        }
        invalidatePages()
    }

    /** A change is finished: push it on the undo stack. */
    override fun commit() {
        undoStack.addLast(last)
        redoStack.clear()
        last = snapshot()
        invalidatePages()
        updateBars()
    }

    private fun snapshot(): Map<Int, List<EditItem>> = pageItems.mapValues { e -> e.value.map { it.copy() } }

    private fun restore(state: Map<Int, List<EditItem>>) {
        pageItems.clear()
        state.forEach { (k, v) -> pageItems[k] = v.map { it.copy() }.toMutableList() }
        selected = null
        last = snapshot()
        invalidatePages()
        updateBars()
    }

    private fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        restore(prev)
    }

    private fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        restore(next)
    }

    private val hasChanges get() = pageItems.values.any { it.isNotEmpty() }

    private fun invalidatePages() {
        val rv = binding.rvPages
        for (i in 0 until rv.childCount) rv.getChildAt(i).findViewById<View>(R.id.layer)?.invalidate()
    }

    // ------------------------------------------------------------------ panels

    private fun openPanel(mode: Mode) {
        panel = mode
        if (mode == Mode.NONE) selected = null
        binding.mainTools.visibility = if (mode == Mode.NONE) View.VISIBLE else View.GONE
        binding.textPanel.visibility = if (mode == Mode.ADD_TEXT || mode == Mode.EDIT_TEXT) View.VISIBLE else View.GONE
        binding.annotatePanel.visibility = if (mode == Mode.ANNOTATE) View.VISIBLE else View.GONE
        binding.signPanel.visibility = if (mode == Mode.SIGN) View.VISIBLE else View.GONE
        binding.tvTextHint.setText(if (mode == Mode.EDIT_TEXT) R.string.tap_text_to_edit else R.string.tap_anywhere_add_text)
        if (mode == Mode.EDIT_TEXT && textColor == 0xFFE91E8C.toInt()) textColor = Color.BLACK
        if (mode == Mode.SIGN) refreshSignatures()
        refreshTextPanel()
        refreshAnnotatePanel()
        invalidatePages()
        updateBars()
    }

    private fun updateBars() {
        val editing = panel != Mode.NONE || hasChanges || undoStack.isNotEmpty()
        binding.editActions.visibility = if (editing) View.VISIBLE else View.GONE
        binding.btnInfo.visibility = if (!editing || panel == Mode.SIGN) View.VISIBLE else View.GONE
        binding.btnUndo.isEnabled = undoStack.isNotEmpty()
        binding.btnRedo.isEnabled = redoStack.isNotEmpty()
    }

    private fun colorDot(selectedColor: Int, c: Int, onClick: () -> Unit): FrameLayout = FrameLayout(this).apply {
        layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) }
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(c)
            if (c == Color.WHITE) setStroke(dp(1), 0xFFD5DAE1.toInt())
        }
        if (c == selectedColor) addView(ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dp(16), dp(16), Gravity.CENTER)
            setImageResource(R.drawable.ic_check)
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(if (c == Color.WHITE || c == 0xFFFBBF24.toInt() || c == 0xFFA3E635.toInt()) Color.BLACK else Color.WHITE))
        })
        setOnClickListener { onClick() }
    }

    private fun setupTextPanel() {
        binding.btnTextBack.setOnClickListener { openPanel(Mode.NONE) }
        binding.tvTextHint.setOnClickListener { addTextFromBar() }
        binding.tvTextSize.setOnClickListener { showSizeList() }
        binding.sbTextSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                textSizeValue = p + 6
                binding.tvTextSize.text = textSizeValue.toString()
                if (fromUser) applySizeToSelection()
            }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) { if (selected is TextItem) commit() }
        })
    }

    private fun applySizeToSelection() {
        (selected as? TextItem)?.let { t ->
            val k = (textSizeValue / 600f) / t.size
            t.size = textSizeValue / 600f
            t.width *= k
            invalidatePages()
        }
    }

    /** "72 ▾": list of common sizes above the field, like the original. */
    private fun showSizeList() {
        val anchor = binding.tvTextSize
        val popup = androidx.appcompat.widget.ListPopupWindow(this)
        val labels = textSizes.map { it.toString() }
        popup.setAdapter(object : android.widget.ArrayAdapter<String>(this, R.layout.item_size_option, labels) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent) as android.widget.TextView
                v.setTextColor(androidx.core.content.ContextCompat.getColor(context,
                    if (textSizes[position] == textSizeValue) R.color.primary else R.color.text_primary))
                return v
            }
        })
        popup.anchorView = anchor
        popup.width = dp(56)
        popup.height = dp(200)
        popup.isModal = true
        popup.setBackgroundDrawable(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.bg_size_popup))
        popup.setOnItemClickListener { _, _, position, _ ->
            textSizeValue = textSizes[position]
            binding.sbTextSize.progress = textSizeValue - 6
            binding.tvTextSize.text = textSizeValue.toString()
            if (selected is TextItem) { applySizeToSelection(); commit() }
            popup.dismiss()
        }
        popup.setOnDismissListener { binding.tvTextSize.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_caret_down, 0) }
        binding.tvTextSize.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_caret_up, 0)
        popup.show()
        textSizes.indexOf(textSizeValue).takeIf { it >= 0 }?.let { popup.setSelection(it) }
    }

    private fun refreshTextPanel() {
        binding.textColors.removeAllViews()
        palette.forEach { c ->
            binding.textColors.addView(colorDot(textColor, c) {
                textColor = c
                (selected as? TextItem)?.let { it.color = c; commit() }
                refreshTextPanel()
            })
        }
        binding.sbTextSize.progress = (textSizeValue - 6).coerceIn(0, 66)
        binding.tvTextSize.text = textSizeValue.toString()
    }

    private fun setupAnnotatePanel() {
        binding.btnAnnBack.setOnClickListener { openPanel(Mode.NONE) }
        binding.tabColor.setOnClickListener { showAnnColors(true) }
        binding.tabSize.setOnClickListener { showAnnColors(false) }
        binding.annEraser.setOnClickListener { annTool = EditTool.ERASER; refreshAnnotatePanel() }
        binding.annHighlight.setOnClickListener { annTool = EditTool.HIGHLIGHT; refreshAnnotatePanel() }
        binding.annUnderline.setOnClickListener { annTool = EditTool.UNDERLINE; refreshAnnotatePanel() }
        binding.annStrike.setOnClickListener { annTool = EditTool.STRIKE; refreshAnnotatePanel() }
        binding.annPen.setOnClickListener { annTool = EditTool.PEN; refreshAnnotatePanel() }
        binding.sbAnnSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) { annSize = p }
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
        showAnnColors(true)
    }

    private fun showAnnColors(colors: Boolean) {
        binding.annColorsScroll.visibility = if (colors) View.VISIBLE else View.GONE
        binding.sbAnnSize.visibility = if (colors) View.GONE else View.VISIBLE
        binding.tabColor.isSelected = colors
        binding.tabSize.isSelected = !colors
        binding.tabColor.alpha = if (colors) 1f else 0.55f
        binding.tabSize.alpha = if (colors) 0.55f else 1f
    }

    private fun refreshAnnotatePanel() {
        binding.annColors.removeAllViews()
        palette.forEach { c ->
            binding.annColors.addView(colorDot(annColor, c) { annColor = c; refreshAnnotatePanel() })
        }
        binding.tabColorDot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(annColor) }
        binding.sbAnnSize.progress = annSize
        binding.annEraser.isSelected = annTool == EditTool.ERASER
        binding.annHighlight.isSelected = annTool == EditTool.HIGHLIGHT
        binding.annUnderline.isSelected = annTool == EditTool.UNDERLINE
        binding.annStrike.isSelected = annTool == EditTool.STRIKE
        binding.annPen.isSelected = annTool == EditTool.PEN
    }

    private fun setupSignPanel() {
        binding.btnSignBack.setOnClickListener { openPanel(Mode.NONE) }
        binding.btnAddSignatureWide.setOnClickListener { signPad.launch(SignaturePadActivity.intent(this)) }
        binding.btnAddSignature.setOnClickListener { signPad.launch(SignaturePadActivity.intent(this)) }
    }

    /** "+ Add signature", or "+ Add" followed by the saved signatures (tap to place, long-press to delete). */
    private fun refreshSignatures() {
        val saved = SignatureStore.list(this)
        binding.btnAddSignatureWide.visibility = if (saved.isEmpty()) View.VISIBLE else View.GONE
        binding.signListScroll.visibility = if (saved.isEmpty()) View.GONE else View.VISIBLE
        val list = binding.signList
        while (list.childCount > 1) list.removeViewAt(1)
        saved.forEach { f ->
            val askDelete = {
                confirm(getString(R.string.delete_signature), getString(R.string.delete_signature_message), getString(R.string.delete)) {
                    f.delete()
                    refreshSignatures()
                }
            }
            val cell = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(98), dp(56)).apply { marginStart = dp(8) }
            }
            val iv = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(90), dp(48), Gravity.BOTTOM or Gravity.START)
                background = androidx.core.content.ContextCompat.getDrawable(this@PdfEditActivity, R.drawable.bg_signature_item)
                setPadding(dp(6), dp(4), dp(6), dp(4))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(SignatureStore.load(f))
                contentDescription = getString(R.string.sign)
                setOnClickListener { SignatureStore.load(f)?.let { placeImage(it, 0.35f, signature = true) } }
                setOnLongClickListener { askDelete(); true }
            }
            val x = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP or Gravity.END)
                setImageResource(R.drawable.ic_sign_remove)
                contentDescription = getString(R.string.delete)
                setOnClickListener { askDelete() }
            }
            cell.addView(iv)
            cell.addView(x)
            list.addView(cell)
        }
    }

    /** Puts a picture / signature in the middle of the page on screen, selected. */
    private fun placeImage(bmp: Bitmap, width: Float, signature: Boolean = false) {
        val page = currentPage()
        val (pw, ph) = sizes.getOrElse(page) { 595f to 842f }
        val item = ImageItem(bmp, 0.5f, visibleCentreY(page, ph / pw), width, signature = signature)
        items(page).add(item)
        selected = item
        commit()
    }

    private fun currentPage(): Int {
        val lm = binding.rvPages.layoutManager as LinearLayoutManager
        val first = lm.findFirstVisibleItemPosition().coerceAtLeast(0)
        val last = lm.findLastVisibleItemPosition().coerceAtLeast(first)
        val mid = binding.rvPages.height / 2
        return (first..last).minByOrNull { i ->
            val v = lm.findViewByPosition(i) ?: return@minByOrNull Int.MAX_VALUE
            kotlin.math.abs((v.top + v.bottom) / 2 - mid)
        } ?: first
    }

    private fun scrollToPage(page: Int) {
        if (page in sizes.indices) (binding.rvPages.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(page, 0)
    }

    private fun showTutorial() {
        EditTutorialSheet.show(this) { sign ->
            if (sign) openPanel(Mode.SIGN)
            else if (textLines.isNotEmpty()) openPanel(Mode.EDIT_TEXT) else openPanel(Mode.ADD_TEXT)
        }
    }

    // ------------------------------------------------------------------ save / close

    private fun save() {
        if (saving) return
        if (!hasChanges) { openPanel(Mode.NONE); return }
        saving = true
        selected = null
        invalidatePages()
        binding.tvDone.visibility = View.INVISIBLE
        binding.pbDone.visibility = View.VISIBLE
        val snapshot = snapshot()
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val tmp = File(file.parentFile, ".${file.name}.${System.currentTimeMillis()}.tmp")
                try {
                    write(file, tmp, snapshot)
                    closeRenderer()
                    if (!tmp.renameTo(file)) tmp.copyTo(file, overwrite = true)
                    com.theoccess.alldocreader.data.SavedFiles.onSaved(applicationContext, file)
                    true
                } catch (e: Throwable) {
                    false
                } finally {
                    if (tmp.exists()) tmp.delete()
                }
            }
            saving = false
            binding.tvDone.visibility = View.VISIBLE
            binding.pbDone.visibility = View.GONE
            if (!ok) { toast(R.string.save_failed); return@launch }
            FileRepository.replace(file.absolutePath, DocFile.from(file))
            setResult(Activity.RESULT_OK)
            pageItems.clear()
            undoStack.clear(); redoStack.clear(); last = emptyMap()
            cache.evictAll()
            closeRenderer()
            openRenderer()
            adapter.notifyDataSetChanged()
            openPanel(Mode.NONE)
            loadLines(Mode.NONE)
            TopPill.show(this@PdfEditActivity, getString(R.string.saved_successfully), R.drawable.ic_check_circle_outline, 60)
            com.theoccess.alldocreader.ads.Ads.showInterstitial(this@PdfEditActivity)
        }
    }

    /** Draws each edited page's layer into a picture and stamps it on the page. */
    private fun write(src: File, out: File, state: Map<Int, List<EditItem>>) {
        PDDocument.load(src).use { doc ->
            doc.isAllSecurityToBeRemoved = true
            state.forEach { (index, list) ->
                if (list.isEmpty() || index >= doc.numberOfPages) return@forEach
                val page = doc.getPage(index)
                val box = page.cropBox
                val rot = ((page.rotation % 360) + 360) % 360
                val shownW = if (rot % 180 == 0) box.width else box.height
                val shownH = if (rot % 180 == 0) box.height else box.width
                val scale = min(3f, 2600f / max(shownW, shownH))
                val bw = (shownW * scale).toInt().coerceAtLeast(1)
                val bh = (shownH * scale).toInt().coerceAtLeast(1)
                var bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                // the screen layer uses the page aspect from PdfRenderer; keep y in the same units
                EditRenderer.draw(android.graphics.Canvas(bmp), list, bw.toFloat())
                if (rot != 0) {
                    val r = Bitmap.createBitmap(bmp, 0, 0, bw, bh, Matrix().apply { postRotate(-rot.toFloat()) }, true)
                    bmp.recycle()
                    bmp = r
                }
                val img = LosslessFactory.createFromImage(doc, bmp)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use {
                    it.drawImage(img, box.lowerLeftX, box.lowerLeftY, box.width, box.height)
                }
                bmp.recycle()
            }
            doc.save(out)
        }
    }

    private fun close() {
        if (saving) return
        if (hasChanges) confirm(getString(R.string.discard_title), getString(R.string.discard_message), getString(R.string.discard)) { finish() }
        else finish()
    }

    private fun confirm(title: String, message: String, ok: String, onOk: () -> Unit) {
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

    override fun onDestroy() {
        renderDispatcher.close()
        closeRenderer()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ pages

    private inner class PageAdapter : RecyclerView.Adapter<PageVH>() {
        override fun getItemCount() = sizes.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            PageVH(ItemEditPageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        override fun onBindViewHolder(holder: PageVH, position: Int) = holder.bind(position)
        override fun onViewRecycled(holder: PageVH) { holder.job?.cancel() }
    }

    private inner class PageVH(val b: ItemEditPageBinding) : RecyclerView.ViewHolder(b.root) {
        var job: Job? = null

        fun bind(index: Int) {
            val (pw, ph) = sizes[index]
            val w = (b.root.parent as? View)?.width?.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
            b.pageBox.layoutParams = b.pageBox.layoutParams.apply { height = (w * ph / pw).toInt() }
            b.layer.host = this@PdfEditActivity
            b.layer.page = index
            b.layer.invalidate()
            job?.cancel()
            val cached = cache.get(index)
            if (cached != null) {
                b.ivPage.setImageBitmap(cached)
                b.pbPage.visibility = View.GONE
                return
            }
            b.ivPage.setImageDrawable(null)
            b.pbPage.visibility = View.VISIBLE
            val target = w.coerceAtMost(1600)
            job = lifecycleScope.launch {
                val bmp = withContext(renderDispatcher) {
                    try {
                        synchronized(this@PdfEditActivity) {
                            renderer?.openPage(index)?.use { p ->
                                Bitmap.createBitmap(target, (target * p.height / p.width.toFloat()).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(Color.WHITE)
                                    p.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        null
                    }
                } ?: return@launch
                cache.put(index, bmp)
                if (bindingAdapterPosition == index) {
                    b.ivPage.setImageBitmap(bmp)
                    b.pbPage.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_PAGE = "page"
        private const val EXTRA_MODE = "mode"

        fun intent(context: Context, file: File, page: Int = 0, mode: Mode = Mode.NONE) =
            Intent(context, PdfEditActivity::class.java)
                .putExtra(EXTRA_PATH, file.absolutePath)
                .putExtra(EXTRA_PAGE, page)
                .putExtra(EXTRA_MODE, mode.ordinal)
    }
}
