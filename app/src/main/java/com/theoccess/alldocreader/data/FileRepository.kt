package com.theoccess.alldocreader.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds every supported document/image on the device and keeps the result in memory
 * so all screens (home counts, category lists, search) share one scan.
 */
object FileRepository {

    data class ScanState(
        val loading: Boolean = false,
        val loaded: Boolean = false,
        val files: Map<FileType, List<DocFile>> = emptyMap(),
        val storageUsed: Long = 0L,
        val storageTotal: Long = 0L
    ) {
        fun count(type: FileType) = files[type]?.size ?: 0
        val totalCount: Int get() = files.values.sumOf { it.size }
        fun all(): List<DocFile> = files.values.flatten()
        fun filesFor(category: Category): List<DocFile> =
            if (category.type == null) all() else files[category.type].orEmpty()
    }

    private const val TAG = "FileRepository"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private val _state = MutableLiveData(ScanState())
    val state: LiveData<ScanState> = _state
    val current: ScanState get() = _state.value ?: ScanState()

    /** Starts a scan unless one is already running. */
    fun refresh(context: Context, force: Boolean = false) {
        if (job?.isActive == true) return
        if (!force && current.loaded) return
        val app = context.applicationContext
        _state.value = current.copy(loading = true)
        job = scope.launch {
            val result = withContext(Dispatchers.IO) { scan(app) }
            _state.value = result
        }
    }

    /** Drops a file from the cache (e.g. it no longer exists). */
    fun forget(path: String) = forgetAll(setOf(path))

    fun forgetAll(paths: Set<String>) {
        val s = current
        if (!s.loaded) return
        _state.value = s.copy(files = s.files.mapValues { (_, list) -> list.filterNot { it.path in paths } })
    }

    /** Swaps a renamed file in the cache (its type may change with the extension). */
    fun replace(oldPath: String, newFile: DocFile) {
        val s = current
        if (!s.loaded) return
        val map = s.files.mapValues { (_, list) -> list.filterNot { it.path == oldPath } }.toMutableMap()
        if (newFile.type != FileType.OTHER) {
            map[newFile.type] = (listOf(newFile) + map[newFile.type].orEmpty()).sortedByDescending { it.modified }
        }
        _state.value = s.copy(files = map)
    }

    private fun scan(context: Context): ScanState {
        val found = LinkedHashMap<String, DocFile>()
        try {
            queryMediaStore(context, found)
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query failed, falling back to a file walk", e)
        }
        if (found.isEmpty()) {
            walk(Environment.getExternalStorageDirectory(), found, depth = 0)
        }
        val grouped = found.values
            .groupBy { it.type }
            .mapValues { (_, list) -> list.sortedByDescending { it.modified } }
        val (used, total) = storageStats()
        return ScanState(loading = false, loaded = true, files = grouped, storageUsed = used, storageTotal = total)
    }

    private fun queryMediaStore(context: Context, out: MutableMap<String, DocFile>) {
        val uri = MediaStore.Files.getContentUri("external")
        @Suppress("DEPRECATION")
        val dataCol = MediaStore.Files.FileColumns.DATA
        val projection = arrayOf(
            dataCol,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val extensions = FileType.supported.flatMap { it.extensions }
        val selection = extensions.joinToString(" OR ") { "LOWER($dataCol) LIKE ?" }
        val args = extensions.map { "%.$it" }.toTypedArray()

        context.contentResolver.query(uri, projection, selection, args, null)?.use { c ->
            val iData = c.getColumnIndexOrThrow(dataCol)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            while (c.moveToNext()) {
                val path = c.getString(iData) ?: continue
                if (path.contains("/.")) continue // hidden folders / files
                val name = path.substringAfterLast('/')
                val type = FileType.fromName(name)
                if (type == FileType.OTHER) continue
                var size = c.getLong(iSize)
                var modified = c.getLong(iDate) * 1000L
                val f = File(path)
                if (!f.exists()) continue
                if (size <= 0L) size = f.length()
                if (modified <= 0L) modified = f.lastModified()
                if (size <= 0L) continue
                out[path] = DocFile(path, name, size, modified, type)
            }
        }
    }

    /** Fallback when MediaStore gives nothing (e.g. very old devices). */
    private fun walk(dir: File, out: MutableMap<String, DocFile>, depth: Int) {
        if (depth > 8) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                if (depth == 0 && f.name == "Android") continue
                walk(f, out, depth + 1)
            } else {
                val type = FileType.fromName(f.name)
                if (type != FileType.OTHER && f.length() > 0) out[f.absolutePath] = DocFile.from(f)
            }
        }
    }

    fun storageStats(): Pair<Long, Long> = try {
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        (total - free) to total
    } catch (e: Exception) {
        0L to 0L
    }
}
