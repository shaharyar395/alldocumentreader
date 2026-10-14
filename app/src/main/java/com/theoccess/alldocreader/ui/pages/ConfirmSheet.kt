package com.theoccess.alldocreader.ui.pages

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.SheetConfirmBinding

/** Bottom sheet "Title / message / [Cancel] [OK]" (e.g. "Quit merging?", "Quit splitting?"). */
object ConfirmSheet {
    fun show(context: Context, title: String, message: CharSequence, ok: String, onOk: () -> Unit) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetConfirmBinding.inflate(LayoutInflater.from(context))
        b.tvTitle.text = title
        b.tvMessage.text = message
        b.btnOk.text = ok
        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnOk.setOnClickListener { dialog.dismiss(); onOk() }
        dialog.setContentView(b.root)
        dialog.show()
    }
}
