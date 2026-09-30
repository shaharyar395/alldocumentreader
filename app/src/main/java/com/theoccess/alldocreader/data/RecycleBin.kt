package com.theoccess.alldocreader.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * App-private recycle bin. Files are moved (not copied) into
 * Android/data/<package>/files/RecycleBin and can be restored within 30 days.
 * The restore UI comes in a later step; [restore] is ready for it.
 */
object RecycleBin {

    data class Item(val binName: String, val originalPath: String, val deletedAt: Long, val size: Long)

    private val KEEP_MS = TimeUnit.DAYS.toMillis(30)
    private const val INDEX = "index.json"

    fun dir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        return File(base, "RecycleBin").apply { mkdirs() }
    }

    @Synchronized
    fun items(context: Context): List<Item> = readIndex(context)

    /** Moves [file] into the bin. Returns false if it could not be moved. */
    @Synchronized
    fun moveToBin(context: Context, file: File): Boolean {
        if (!file.exists()) return false
        val binDir = dir(context)
        // Same-named files deleted in the same millisecond must not overwrite each other.
        var stamp = System.currentTimeMillis()
        while (File(binDir, "${stamp}_${file.name}").exists()) stamp++
        val binName = "${stamp}_${file.name}"
        val target = File(binDir, binName)
        val size = file.length()
        val moved = file.renameTo(target) || copyThenDelete(file, target)
        if (!moved) return false
        val list = readIndex(context) + Item(binName, file.absolutePath, System.currentTimeMillis(), size)
        writeIndex(context, list)
        return true
    }

    /** Puts a binned file back where it was. */
    @Synchronized
    fun restore(context: Context, item: Item): Boolean {
        val src = File(dir(context), item.binName)
        val dst = File(item.originalPath)
        if (!src.exists() || dst.exists()) return false
        dst.parentFile?.mkdirs()
        val ok = src.renameTo(dst) || copyThenDelete(src, dst)
        if (ok) writeIndex(context, readIndex(context).filterNot { it.binName == item.binName })
        return ok
    }

    /** Deletes one item for good. */
    @Synchronized
    fun deletePermanently(context: Context, item: Item): Boolean {
        val f = File(dir(context), item.binName)
        val ok = !f.exists() || f.delete()
        if (ok) writeIndex(context, readIndex(context).filterNot { it.binName == item.binName })
        return ok
    }

    /** Empties the whole bin. */
    @Synchronized
    fun clear(context: Context) {
        readIndex(context).forEach { File(dir(context), it.binName).delete() }
        writeIndex(context, emptyList())
    }

    fun fileOf(context: Context, item: Item): File = File(dir(context), item.binName)

    /** Days left before [item] is removed automatically. */
    fun daysLeft(item: Item): Int {
        val left = KEEP_MS - (System.currentTimeMillis() - item.deletedAt)
        return maxOf(0, TimeUnit.MILLISECONDS.toDays(left).toInt() + 1).coerceAtMost(30)
    }

    /** Permanently removes items older than 30 days. Call on app start. */
    @Synchronized
    fun purgeExpired(context: Context) {
        val now = System.currentTimeMillis()
        val (expired, keep) = readIndex(context).partition { now - it.deletedAt > KEEP_MS }
        if (expired.isEmpty()) return
        expired.forEach { File(dir(context), it.binName).delete() }
        writeIndex(context, keep)
    }

    private fun copyThenDelete(src: File, dst: File): Boolean = try {
        src.copyTo(dst, overwrite = true)
        if (src.delete()) true else { dst.delete(); false }
    } catch (e: Exception) {
        dst.delete()
        false
    }

    private fun readIndex(context: Context): List<Item> = try {
        val f = File(dir(context), INDEX)
        if (!f.exists()) emptyList() else {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Item(o.getString("bin"), o.getString("orig"), o.getLong("at"), o.optLong("size"))
            }
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun writeIndex(context: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach {
            arr.put(JSONObject().put("bin", it.binName).put("orig", it.originalPath).put("at", it.deletedAt).put("size", it.size))
        }
        File(dir(context), INDEX).writeText(arr.toString())
    }
}
