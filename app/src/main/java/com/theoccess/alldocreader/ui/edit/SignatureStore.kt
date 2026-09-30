package com.theoccess.alldocreader.ui.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/** Signatures drawn in the signature pad, kept for next time (app-private storage). */
object SignatureStore {
    private fun dir(context: Context) = File(context.filesDir, "signatures").apply { mkdirs() }

    fun list(context: Context): List<File> =
        dir(context).listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() }.orEmpty()

    fun save(context: Context, bmp: Bitmap): File {
        val f = File(dir(context), "sign_${System.currentTimeMillis()}.png")
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun load(file: File): Bitmap? = BitmapFactory.decodeFile(file.absolutePath)
}
