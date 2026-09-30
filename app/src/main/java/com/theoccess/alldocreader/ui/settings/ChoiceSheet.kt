package com.theoccess.alldocreader.ui.settings

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ItemChoiceOptionBinding
import com.theoccess.alldocreader.databinding.SheetChoiceBinding

/** "Scan settings" / "App theme": a title, radio rows (chosen row highlighted) and OK. */
object ChoiceSheet {
    fun show(context: Context, title: String, options: List<String>, selected: Int, onOk: (Int) -> Unit) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetChoiceBinding.inflate(LayoutInflater.from(context))
        b.tvTitle.text = title
        var choice = selected
        val rows = options.mapIndexed { i, label ->
            ItemChoiceOptionBinding.inflate(LayoutInflater.from(context), b.options, false).also { r ->
                r.tvLabel.text = label
                b.options.addView(r.root)
            }
        }
        fun refresh() = rows.forEachIndexed { i, r ->
            val on = i == choice
            r.root.isSelected = on
            r.tvLabel.isSelected = on
            r.rb.isChecked = on
        }
        rows.forEachIndexed { i, r -> r.root.setOnClickListener { choice = i; refresh() } }
        refresh()
        b.btnOk.setOnClickListener {
            dialog.dismiss()
            onOk(choice)
        }
        dialog.setContentView(b.root)
        dialog.show()
    }
}
