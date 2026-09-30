package com.theoccess.alldocreader.ui.viewer

import android.graphics.Bitmap
import java.io.Closeable

/** Something the viewer can show page by page (a PDF, or a laid-out Word/TXT document). */
interface PageSource : Closeable {
    val pageCount: Int

    /** Page size in PDF points (1/72 inch). */
    fun pageWidth(index: Int): Float
    fun pageHeight(index: Int): Float

    /** Renders a page to a bitmap [targetWidth] pixels wide. Called from a single background thread. */
    fun render(index: Int, targetWidth: Int): Bitmap

    /** Plain text of a page, used by search. Called from a background thread. */
    fun pageText(index: Int): String
}
