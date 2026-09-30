package com.theoccess.alldocreader.ui.edit

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.SheetEditTutorialBinding

/** The "i" sheet: animated Edit PDF / Sign demos with Try now and Later. */
object EditTutorialSheet {
    fun show(context: Context, onTry: (sign: Boolean) -> Unit) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetEditTutorialBinding.inflate(LayoutInflater.from(context))
        fun select(sign: Boolean) {
            b.tabEdit.isSelected = !sign
            b.tabSign.isSelected = sign
            b.demo.demo = if (sign) TutorialDemoView.Demo.SIGN else TutorialDemoView.Demo.EDIT
            b.tvTitle.setText(if (sign) R.string.tut_sign_title else R.string.tut_edit_title)
        }
        select(false)
        b.tabEdit.setOnClickListener { select(false) }
        b.tabSign.setOnClickListener { select(true) }
        b.demo.onLoop = { select(b.demo.demo == TutorialDemoView.Demo.EDIT) }
        b.btnTry.setOnClickListener {
            dialog.dismiss()
            onTry(b.demo.demo == TutorialDemoView.Demo.SIGN)
        }
        b.btnLater.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(b.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }
}
