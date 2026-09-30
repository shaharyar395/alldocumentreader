package com.theoccess.alldocreader.ui.files

import android.content.Context
import android.content.ContextWrapper
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileOps
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.data.SortMode
import com.theoccess.alldocreader.databinding.ItemFileActionBinding
import com.theoccess.alldocreader.databinding.SheetDeleteConfirmBinding
import com.theoccess.alldocreader.databinding.SheetFileInfoBinding
import com.theoccess.alldocreader.databinding.SheetFileMenuBinding
import com.theoccess.alldocreader.databinding.SheetFilterBinding
import com.theoccess.alldocreader.databinding.SheetRenameBinding
import com.theoccess.alldocreader.databinding.SheetShortcutPermissionBinding
import com.theoccess.alldocreader.databinding.SheetNumberInputBinding
import com.theoccess.alldocreader.databinding.SheetUnsupportedBinding
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.Shortcuts
import com.theoccess.alldocreader.util.formatSize
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bottom sheets used by the file lists (⋮ menu, info, rename, delete, filter, shortcut permission). */
object FileSheets {

    private fun sheet(context: Context): BottomSheetDialog =
        BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet).apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        }

    private fun Context.lifecycleOwner(): LifecycleOwner? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is LifecycleOwner) return c
            c = c.baseContext
        }
        return null
    }

    // ------------------------------------------------------------------ ⋮ menu

    /**
     * Header (icon, name, path, ›) + Share / Rename / To home screen / Delete.
     * [onChanged] runs after a rename or delete so the caller can refresh.
     */
    fun showMenu(context: Context, doc: DocFile, onChanged: (() -> Unit)? = null) {
        val dialog = sheet(context)
        val b = SheetFileMenuBinding.inflate(LayoutInflater.from(context))
        b.ivIcon.setImageResource(doc.type.icon)
        b.tvName.text = doc.name
        b.tvPath.text = doc.path
        b.header.setOnClickListener {
            dialog.dismiss()
            showInfo(context, doc)
        }

        fun action(a: ItemFileActionBinding, icon: Int, label: Int, onClick: () -> Unit) {
            a.ivIcon.setImageResource(icon)
            a.tvLabel.setText(label)
            a.root.setOnClickListener {
                dialog.dismiss()
                onClick()
            }
        }
        action(b.actShare, R.drawable.ic_share, R.string.share) { FileActions.share(context, doc) }
        action(b.actRename, R.drawable.ic_rename, R.string.rename) { showRename(context, doc) { onChanged?.invoke() } }
        action(b.actHome, R.drawable.ic_home_add, R.string.to_home_screen) { addToHomeScreen(context, doc) }
        action(b.actDelete, R.drawable.ic_delete, R.string.delete) {
            confirmDelete(context, listOf(doc)) { onChanged?.invoke() }
        }

        dialog.setContentView(b.root)
        dialog.show()
    }

    // ------------------------------------------------------------------ info

    fun showInfo(context: Context, doc: DocFile) {
        val dialog = sheet(context)
        val b = SheetFileInfoBinding.inflate(LayoutInflater.from(context))
        b.ivIcon.setImageResource(doc.type.icon)
        b.tvName.text = doc.name
        b.tvSize.text = formatSize(doc.size)
        b.tvPath.text = doc.path
        b.tvModified.text = SimpleDateFormat("HH:mm  MM/dd/yyyy", Locale.getDefault()).format(Date(doc.modified))
        b.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(b.root)
        dialog.show()
    }

    // ------------------------------------------------------------------ rename

    fun showRename(context: Context, doc: DocFile, onRenamed: (DocFile) -> Unit) {
        val dialog = sheet(context)
        val b = SheetRenameBinding.inflate(LayoutInflater.from(context))
        b.etName.setText(FileOps.baseName(doc.name))
        b.btnClear.setOnClickListener { b.etName.setText("") }
        b.etName.doAfterTextChanged {
            b.tvError.visibility = View.GONE
            b.btnClear.visibility = if (it.isNullOrEmpty()) View.INVISIBLE else View.VISIBLE
        }
        b.btnCancel.setOnClickListener { dialog.dismiss() }

        fun submit() {
            val owner = context.lifecycleOwner() ?: return
            b.btnOk.isEnabled = false
            owner.lifecycleScope.launch {
                val result = FileOps.rename(context, doc, b.etName.text.toString())
                b.btnOk.isEnabled = true
                val error = when (result) {
                    is FileOps.RenameResult.Success -> {
                        dialog.dismiss()
                        if (result.file.path != doc.path) context.toast(R.string.rename_done)
                        onRenamed(result.file)
                        null
                    }
                    FileOps.RenameResult.Empty -> R.string.rename_empty
                    FileOps.RenameResult.Invalid -> R.string.rename_invalid
                    FileOps.RenameResult.Exists -> R.string.rename_exists
                    FileOps.RenameResult.Failed -> R.string.rename_failed
                }
                if (error != null) {
                    b.tvError.setText(error)
                    b.tvError.visibility = View.VISIBLE
                }
            }
        }
        b.btnOk.setOnClickListener { submit() }
        b.etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }

        dialog.setContentView(b.root)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnShowListener {
            b.etName.requestFocus()
            b.etName.selectAll()
            b.etName.postDelayed({
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(b.etName, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
        }
        dialog.show()
    }

    // ------------------------------------------------------------------ delete

    /** "Move to recycle bin or delete?" for one or many files. */
    fun confirmDelete(context: Context, docs: List<DocFile>, onDone: () -> Unit) {
        if (docs.isEmpty()) return
        val dialog = sheet(context)
        val b = SheetDeleteConfirmBinding.inflate(LayoutInflater.from(context))

        fun run(toBin: Boolean) {
            dialog.dismiss()
            val owner = context.lifecycleOwner() ?: return
            owner.lifecycleScope.launch {
                val r = FileOps.delete(context, docs, toBin)
                if (r.done > 0) {
                    val res = if (toBin) R.plurals.moved_to_bin_count else R.plurals.deleted_count
                    context.toast(context.resources.getQuantityString(res, r.done, r.done))
                }
                if (r.failed > 0) context.toast(R.string.delete_failed)
                onDone()
            }
        }
        b.btnRecycle.setOnClickListener { run(toBin = true) }
        b.btnDeleteDirect.setOnClickListener { run(toBin = false) }
        dialog.setContentView(b.root)
        dialog.show()
    }

    // ------------------------------------------------------------------ filter

    /** "Filter by": Name / Date / File size + Ascending / Descending. */
    fun showFilter(context: Context, onApplied: () -> Unit) {
        val dialog = sheet(context)
        val b = SheetFilterBinding.inflate(LayoutInflater.from(context))
        var mode = Prefs.sortMode
        var asc = Prefs.sortAscending

        fun render() {
            b.rbName.isChecked = mode == SortMode.NAME
            b.rbDate.isChecked = mode == SortMode.DATE
            b.rbSize.isChecked = mode == SortMode.SIZE
            b.rbAsc.isChecked = asc
            b.rbDesc.isChecked = !asc
        }
        b.rbName.setOnClickListener { mode = SortMode.NAME; render() }
        b.rbDate.setOnClickListener { mode = SortMode.DATE; render() }
        b.rbSize.setOnClickListener { mode = SortMode.SIZE; render() }
        b.rbAsc.setOnClickListener { asc = true; render() }
        b.rbDesc.setOnClickListener { asc = false; render() }
        render()

        b.btnOk.setOnClickListener {
            Prefs.sortMode = mode
            Prefs.sortAscending = asc
            dialog.dismiss()
            onApplied()
        }
        dialog.setContentView(b.root)
        dialog.show()
    }

    // ------------------------------------------------------------------ number input

    /** Small sheet asking for a number in [min]..[max] (page jump, page count). */
    fun askNumber(context: Context, title: String, initial: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
        val dialog = sheet(context)
        val b = SheetNumberInputBinding.inflate(LayoutInflater.from(context))
        b.tvTitle.text = title
        b.etValue.setText(initial.toString())
        b.tvHint.text = "$min – $max"
        b.tvHint.visibility = View.VISIBLE
        b.btnClear.setOnClickListener { b.etValue.setText("") }
        b.btnCancel.setOnClickListener { dialog.dismiss() }
        fun submit() {
            val v = b.etValue.text.toString().toIntOrNull()
            if (v == null || v < min || v > max) {
                b.tvHint.setTextColor(context.getColor(R.color.danger))
                return
            }
            dialog.dismiss()
            onValue(v)
        }
        b.btnOk.setOnClickListener { submit() }
        b.etValue.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }
        dialog.setContentView(b.root)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnShowListener {
            b.etValue.requestFocus()
            b.etValue.selectAll()
            b.etValue.postDelayed({
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(b.etValue, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
        }
        dialog.show()
    }

    /** Text version of [askNumber] (file names). */
    fun askText(context: Context, title: String, initial: String, onValue: (String) -> Unit) {
        val dialog = sheet(context)
        val b = SheetNumberInputBinding.inflate(LayoutInflater.from(context))
        b.tvTitle.text = title
        b.etValue.inputType = android.text.InputType.TYPE_CLASS_TEXT
        b.etValue.filters = arrayOf(android.text.InputFilter.LengthFilter(120))
        b.etValue.setText(initial)
        b.btnClear.setOnClickListener { b.etValue.setText("") }
        b.btnCancel.setOnClickListener { dialog.dismiss() }
        fun submit() {
            val v = b.etValue.text.toString().trim()
            if (v.isEmpty()) {
                b.tvHint.setText(R.string.rename_empty)
                b.tvHint.setTextColor(context.getColor(R.color.danger))
                b.tvHint.visibility = View.VISIBLE
                return
            }
            dialog.dismiss()
            onValue(v)
        }
        b.btnOk.setOnClickListener { submit() }
        b.etValue.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { submit(); true } else false }
        dialog.setContentView(b.root)
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnShowListener {
            b.etValue.requestFocus()
            b.etValue.selectAll()
            b.etValue.postDelayed({
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(b.etValue, InputMethodManager.SHOW_IMPLICIT)
            }, 150)
        }
        dialog.show()
    }

    // ------------------------------------------------------------------ unsupported type

    /** "File type not supported" with OK and FAQ. */
    fun showUnsupported(context: Context) {
        val dialog = sheet(context)
        val b = SheetUnsupportedBinding.inflate(LayoutInflater.from(context))
        b.btnOk.setOnClickListener { dialog.dismiss() }
        b.btnFaq.setOnClickListener {
            dialog.dismiss()
            com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle(R.string.faq)
                .setMessage(R.string.faq_message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
        dialog.setContentView(b.root)
        dialog.show()
    }

    // ------------------------------------------------------------------ to home screen

    fun addToHomeScreen(context: Context, doc: DocFile) {
        if (!Shortcuts.isSupported(context)) {
            context.toast(R.string.shortcut_not_supported)
            return
        }
        if (Shortcuts.needsVendorPermission && !Prefs.shortcutPermissionAsked) {
            showShortcutPermission(context) {
                Prefs.shortcutPermissionAsked = true
                Shortcuts.openPermissionSettings(context)
            }
            return
        }
        if (Shortcuts.pin(context, doc)) context.toast(R.string.shortcut_added)
        else context.toast(R.string.shortcut_not_supported)
    }

    private fun showShortcutPermission(context: Context, onAllow: () -> Unit) {
        val dialog = sheet(context)
        val b = SheetShortcutPermissionBinding.inflate(LayoutInflater.from(context))
        @Suppress("DEPRECATION")
        b.tvMessage.text = Html.fromHtml(context.getString(R.string.shortcut_permission_message))
        b.btnClose.setOnClickListener { dialog.dismiss() }
        b.btnAllow.setOnClickListener {
            dialog.dismiss()
            onAllow()
        }
        dialog.setContentView(b.root)
        dialog.show()
    }
}
