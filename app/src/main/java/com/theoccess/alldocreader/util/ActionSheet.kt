package com.theoccess.alldocreader.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.DrawableRes
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ItemSheetActionBinding
import com.theoccess.alldocreader.databinding.SheetActionsBinding

/** Rounded bottom sheet with an optional header and a list of actions. */
class ActionSheet(private val context: Context) {

    data class Action(
        @DrawableRes val icon: Int,
        val label: String,
        val checked: Boolean? = null,
        val onClick: () -> Unit
    )

    private var headerIcon: Int = 0
    private var title: CharSequence? = null
    private var subtitle: CharSequence? = null
    private val actions = mutableListOf<Action>()

    fun header(@DrawableRes icon: Int, title: CharSequence, subtitle: CharSequence? = null) = apply {
        headerIcon = icon; this.title = title; this.subtitle = subtitle
    }

    fun title(title: CharSequence) = apply { this.title = title }

    fun action(@DrawableRes icon: Int, label: String, checked: Boolean? = null, onClick: () -> Unit) = apply {
        actions += Action(icon, label, checked, onClick)
    }

    fun show(): BottomSheetDialog {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetActionsBinding.inflate(LayoutInflater.from(context))
        if (title == null) {
            b.header.visibility = View.GONE
            b.headerDivider.visibility = View.GONE
        } else {
            b.tvTitle.text = title
            b.tvSubtitle.text = subtitle
            b.tvSubtitle.visibility = if (subtitle.isNullOrEmpty()) View.GONE else View.VISIBLE
            if (headerIcon != 0) b.ivIcon.setImageResource(headerIcon) else b.ivIcon.visibility = View.GONE
        }
        actions.forEach { a ->
            val row = ItemSheetActionBinding.inflate(LayoutInflater.from(context), b.actions, false)
            row.ivIcon.setImageResource(a.icon)
            row.tvLabel.text = a.label
            if (a.checked != null) {
                row.rbCheck.visibility = View.VISIBLE
                row.rbCheck.isChecked = a.checked
            }
            row.root.setOnClickListener {
                dialog.dismiss()
                a.onClick()
            }
            b.actions.addView(row.root)
        }
        dialog.setContentView(b.root)
        dialog.show()
        return dialog
    }
}
