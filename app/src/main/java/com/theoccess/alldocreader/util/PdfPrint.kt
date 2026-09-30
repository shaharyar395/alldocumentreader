package com.theoccess.alldocreader.util

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Sends a PDF file to Android's print dialog (printers or "Save as PDF"). */
object PdfPrint {
    fun print(context: Context, file: File, name: String = file.name) {
        val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        pm.print(name, object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?, newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build(),
                    true
                )
            }

            override fun onWrite(
                pages: Array<out PageRange>?, destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?, callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }, null)
    }
}
