package com.theoccess.alldocreader.ui.edit

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.DialogTextEntryBinding

/** Full-screen dark "Enter text" screen with X, ✓ and Clear (used by Add text / Edit text). */
object TextEntryDialog {
    fun show(context: Context, initial: String, onDone: (String) -> Unit) {
        val dialog = Dialog(context, R.style.Theme_DocReader_TextEntry)
        val b = DialogTextEntryBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(b.root)
        dialog.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        b.etText.setText(initial)
        b.etText.setSelection(b.etText.text.length)
        b.btnClose.setOnClickListener { dialog.dismiss() }
        b.btnClear.setOnClickListener { b.etText.setText("") }
        b.btnOk.setOnClickListener {
            val text = b.etText.text.toString()
            dialog.dismiss()
            onDone(text)
        }
        dialog.setOnShowListener {
            b.etText.requestFocus()
            b.etText.post {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(b.etText, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }
}
