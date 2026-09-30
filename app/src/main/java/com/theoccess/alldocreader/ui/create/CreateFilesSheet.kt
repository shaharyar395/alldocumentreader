package com.theoccess.alldocreader.ui.create

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ItemConvertTileBinding
import com.theoccess.alldocreader.databinding.ItemCreateCardBinding
import com.theoccess.alldocreader.databinding.SheetCreateFilesBinding
import com.theoccess.alldocreader.ui.pages.InsertBlankPagesActivity
import com.theoccess.alldocreader.ui.scan.ScanStartActivity
import com.theoccess.alldocreader.util.StorageAccess
import com.theoccess.alldocreader.util.toast

/** The "+" sheet: Create files (Import, Templates, Create PDF) and Convert format tools. */
object CreateFilesSheet {

    /** Created files are saved to shared storage, so file access must be granted first. */
    fun ensureAccess(context: Context): Boolean {
        if (StorageAccess.has(context)) return true
        context.toast(R.string.permission_needed_hint)
        if (StorageAccess.usesSettingsPage) StorageAccess.openSettingsPage(context)
        return false
    }

    fun show(context: Context) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetCreateFilesBinding.inflate(LayoutInflater.from(context))

        fun card(c: ItemCreateCardBinding, icon: Int, label: Int, intent: () -> Intent) {
            c.ivIcon.setImageResource(icon)
            c.tvLabel.setText(label)
            c.root.setOnClickListener {
                dialog.dismiss()
                if (ensureAccess(context)) context.startActivity(intent())
            }
        }

        fun tile(t: ItemConvertTileBinding, icon: Int, label: Int, intent: () -> Intent) {
            t.ivIcon.setImageResource(icon)
            t.tvLabel.setText(label)
            t.root.setOnClickListener {
                dialog.dismiss()
                if (ensureAccess(context)) context.startActivity(intent())
            }
        }

        card(b.cardImport, R.drawable.ic_create_import, R.string.import_files) { Intent(context, ImportActivity::class.java) }
        card(b.cardTemplates, R.drawable.ic_create_templates, R.string.use_templates) { Intent(context, TemplatesActivity::class.java) }
        card(b.cardCreatePdf, R.drawable.ic_create_pdf_badge, R.string.create_pdf) { InsertBlankPagesActivity.createIntent(context) }

        tile(b.tileImage, R.drawable.ic_cv_image2pdf, R.string.image_to_pdf) { ScanStartActivity.intent(context, scan = false) }
        tile(b.tileScan, R.drawable.ic_cv_scan, R.string.scan_to_pdf) { ScanStartActivity.intent(context, scan = true) }
        tile(b.tileWord, R.drawable.ic_cv_word2pdf, R.string.word_to_pdf) { PickFileActivity.intent(context, ConvertKind.WORD_TO_PDF) }
        tile(b.tilePdfWord, R.drawable.ic_cv_pdf2word, R.string.pdf_to_word) { PickFileActivity.intent(context, ConvertKind.PDF_TO_WORD) }
        tile(b.tilePpt, R.drawable.ic_cv_ppt2pdf, R.string.ppt_to_pdf) { PickFileActivity.intent(context, ConvertKind.PPT_TO_PDF) }

        dialog.setContentView(b.root)
        dialog.show()
    }
}
