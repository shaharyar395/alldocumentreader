package com.theoccess.alldocreader.ui.scan

import android.content.Context
import androidx.annotation.StringRes
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Prefs
import java.io.File

/** Look filters offered under the page in the editor, in the original app's order. */
enum class ScanFilter(@StringRes val label: Int) {
    ORIGINAL(R.string.filter_original),
    AUTO(R.string.filter_auto),
    DOCS(R.string.filter_docs),
    COLOR(R.string.filter_color),
    IMAGE(R.string.filter_image),
    SUPER(R.string.filter_super),
    ENHANCE(R.string.filter_enhance),
    ENHANCE2(R.string.filter_enhance2),
    BW(R.string.filter_bw),
    BW2(R.string.filter_bw2),
    GRAY(R.string.filter_gray),
    INVERT(R.string.filter_invert)
}

/**
 * One page of an Image to PDF / Scan to PDF job.
 * [quad] = crop corners TL, TR, BR, BL as 0..1 fractions of the (EXIF-upright) source picture,
 * null = no crop. [rotation] is applied after cropping.
 */
class ScanPage(
    val id: Long,
    var source: File,
    var rotation: Int = 0,
    var quad: FloatArray? = null,
    var filter: ScanFilter = ScanFilter.AUTO,
    /** Final JPEG written when the editor's Done is pressed. */
    var output: File? = null,
    /** [key] the [output] was made from; a different key means it must be made again. */
    var outputKey: String? = null
) {
    val outputReady: Boolean get() = output?.exists() == true && outputKey == key()

    fun snapshot() = ScanPage(id, source, rotation, quad?.copyOf(), filter, output, outputKey)

    /** Changes whenever anything that affects the picture changes (cache key). */
    fun key(filterOverride: ScanFilter? = null): String =
        "$id:${source.name}:$rotation:${quad?.joinToString(",") { String.format(java.util.Locale.US, "%.3f", it) }}:${filterOverride ?: filter}"
}

/** Pages of the job in progress, shared by the camera, editor and "Image to PDF" list screens. */
object ScanSession {
    val pages = ArrayList<ScanPage>()
    private var nextId = 1L

    /** true = Scan to PDF (camera first), false = Image to PDF. */
    var scanMode = false

    fun dir(context: Context): File = File(context.cacheDir, "scan").apply { mkdirs() }

    fun newFile(context: Context, prefix: String, ext: String = "jpg"): File =
        File(dir(context), "${prefix}_${System.currentTimeMillis()}_${nextId++}.$ext")

    /** Every new page starts on "Auto": a clean scan (even white paper, sharp text, natural colours). */
    @Suppress("UNUSED_PARAMETER")
    fun newPage(source: File, fromCamera: Boolean): ScanPage = ScanPage(nextId++, source, filter = ScanFilter.AUTO)

    fun indexOf(id: Long) = pages.indexOfFirst { it.id == id }

    /** Forget everything and delete the working files. */
    fun clear(context: Context) {
        pages.clear()
        dir(context).listFiles()?.forEach { it.delete() }
    }
}

/** "Choose cropping method" answer, remembered when "Don't ask again" is ticked. */
object ScanPrefs {
    private const val KEY_AUTO = "scan_auto_crop"
    private const val KEY_ASKED = "scan_crop_dont_ask"

    var autoCrop: Boolean
        get() = Prefs.raw.getBoolean(KEY_AUTO, true)
        set(v) = Prefs.raw.edit().putBoolean(KEY_AUTO, v).apply()

    var dontAsk: Boolean
        get() = Prefs.raw.getBoolean(KEY_ASKED, false)
        set(v) = Prefs.raw.edit().putBoolean(KEY_ASKED, v).apply()
}
