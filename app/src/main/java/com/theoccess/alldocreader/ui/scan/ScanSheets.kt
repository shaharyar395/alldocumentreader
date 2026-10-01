package com.theoccess.alldocreader.ui.scan

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.DialogProgressPillBinding
import com.theoccess.alldocreader.databinding.SheetCropMethodBinding
import com.theoccess.alldocreader.databinding.SheetImageSourceBinding

/** "Take a photo / Choose from gallery / Cancel" action sheet. */
object SourceSheet {
    fun show(context: Context, onCamera: () -> Unit, onGallery: () -> Unit, onCancel: () -> Unit = {}) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet_Transparent)
        val b = SheetImageSourceBinding.inflate(LayoutInflater.from(context))
        var picked = false
        b.btnCamera.setOnClickListener { picked = true; dialog.dismiss(); onCamera() }
        b.btnGallery.setOnClickListener { picked = true; dialog.dismiss(); onGallery() }
        b.btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener { if (!picked) onCancel() }
        dialog.setContentView(b.root)
        dialog.show()
    }
}

/**
 * "Choose cropping method": Auto crop (find the document edges) or No crop.
 * Remembered with "Don't ask again"; can be changed later in Settings → Scan settings.
 */
object CropMethodSheet {
    fun show(context: Context, fromSettings: Boolean = false, onChosen: (autoCrop: Boolean) -> Unit) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetCropMethodBinding.inflate(LayoutInflater.from(context))
        // after taking photos the sheet always starts on "No crop"; in Settings it shows the saved choice
        var auto = if (fromSettings) ScanPrefs.autoCrop else false
        var dontAsk = ScanPrefs.dontAsk
        var chosen = false
        fun refresh() {
            b.cbAuto.isChecked = auto
            b.cbNone.isChecked = !auto
            b.ivIllustration.setImageResource(if (auto) R.drawable.ill_crop_auto else R.drawable.ill_crop_none)
            b.rbDontAsk.isChecked = dontAsk
        }
        b.cbAuto.setOnClickListener { auto = true; refresh() }
        b.cbNone.setOnClickListener { auto = false; refresh() }
        b.rbDontAsk.setOnClickListener { dontAsk = !dontAsk; refresh() }
        b.btnOk.setOnClickListener {
            chosen = true
            ScanPrefs.autoCrop = auto
            ScanPrefs.dontAsk = dontAsk
            dialog.dismiss()
            onChosen(auto)
        }
        // closing the sheet without OK still continues with the highlighted choice
        dialog.setOnCancelListener { if (!chosen && !fromSettings) onChosen(auto) }
        refresh()
        dialog.setContentView(b.root)
        dialog.show()
    }
}

/** Centered white pill with a spinner: "Processing… (6%)", "Converting… (99%)". */
class ProgressPill(context: Context) {
    private val dialog = Dialog(context)
    private val b = DialogProgressPillBinding.inflate(LayoutInflater.from(context))

    init {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(b.root)
        dialog.setCancelable(false)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
            setDimAmount(0.45f)
        }
    }

    fun show(text: CharSequence) {
        b.tvText.text = text
        if (!dialog.isShowing) dialog.show()
    }

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }
}
