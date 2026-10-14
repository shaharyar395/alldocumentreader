package com.theoccess.alldocreader.data

import android.content.Context
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

    /** When the last scan finished (elapsed realtime), for [refreshIfStale]. */
    private var lastScanAt = 0L
    /** A refresh was asked for while a scan was running: scan once more when it ends. */
    private var again = false
    /** The last scan ran with "All files access" (it saw every document, not only pictures). */
    @Volatile private var scannedWithAccess = false

    /**
     * Starts a scan unless one is already running. Two steps: the system's media index first
     * (fast, the lists fill at once), then a walk through the storage folders for files the
     * index doesn't know yet (just copied over USB, downloaded, received by Bluetooth…) — those
     * are added to the lists and handed to the media scanner.
     */
    fun refresh(context: Context, force: Boolean = false) {
        val app = context.applicationContext
        watch(app)
        // a scan made before "All files access" was on only saw pictures and the app's own files
        // (Android hides other apps' documents): scan again as soon as access is there
        val access = com.theoccess.alldocreader.util.StorageAccess.has(app)
        if (job?.isActive == true) {
            if (force || (access && !scannedWithAccess)) again = true
            return
        }
        if (!force && current.loaded && (scannedWithAccess || !access)) return
        again = false
        scannedWithAccess = access
        _state.value = current.copy(loading = true)
        val known = current.all()
        job = scope.launch {
            val indexed = withContext(Dispatchers.IO) { scanIndex(app) }
            // show what the index has right away (plus files found by the last walk that are
            // still there, so they don't blink out); the folder walk then adds new ones
            if (indexed.isNotEmpty()) {
                val first = withContext(Dispatchers.IO) {
                    LinkedHashMap(indexed).also { m ->
                        known.forEach { f -> if (f.path !in m && File(f.path).exists()) m[f.path] = f }
                    }
                }
                _state.value = build(first, loading = true)
            }
            val all = withContext(Dispatchers.IO) {
                addUnindexed(app, indexed).also { NewFiles.onScan(it.keys) }   // "New" tags
            }
            _state.value = build(all, loading = false)
            lastScanAt = SystemClock.elapsedRealtime()
            if (again) {
                again = false
                job = null
                refresh(app, force = true)
            }
        }
    }

    /** Back in the app (or a list screen): rescan quietly when the last scan is older than [maxAgeMs]. */
    fun refreshIfStale(context: Context, maxAgeMs: Long = 8_000L) {
        if (!current.loaded) return refresh(context)
        if (SystemClock.elapsedRealtime() - lastScanAt > maxAgeMs) refresh(context, force = true)
    }

    // ------------------------------------------------------------------ auto refresh

    private var observer: ContentObserver? = null
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private val debounced = Runnable { appContext?.let { if (current.loaded) refresh(it, force = true) } }

    /** Files added / removed by other apps while ours is open: rescan (debounced). */
    private fun watch(app: Context) {
        if (observer != null) return
        appContext = app
        val o = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(debounced)
                handler.postDelayed(debounced, 2_500L)
            }
        }
        try {
            app.contentResolver.registerContentObserver(MediaStore.Files.getContentUri("external"), true, o)
            observer = o
        } catch (e: Exception) {
            Log.w(TAG, "Could not watch MediaStore", e)
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
        if (oldPath != newFile.path) NewFiles.renamed(oldPath, newFile.path)
        val s = current
        if (!s.loaded) return
        val map = s.files.mapValues { (_, list) -> list.filterNot { it.path == oldPath } }.toMutableMap()
        if (newFile.type != FileType.OTHER) {
            map[newFile.type] = (listOf(newFile) + map[newFile.type].orEmpty()).sortedByDescending { it.modified }
        }
        _state.value = s.copy(files = map)
    }

    private fun scanIndex(context: Context): Map<String, DocFile> {
        val found = LinkedHashMap<String, DocFile>()
        try {
            queryMediaStore(context, found)
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query failed, falling back to a file walk", e)
        }
        return found
    }

    /** Walks the storage for files the media index doesn't list yet; asks the scanner to index them. */
    private fun addUnindexed(context: Context, indexed: Map<String, DocFile>): Map<String, DocFile> {
        val walked = LinkedHashMap<String, DocFile>()
        try {
            walk(Environment.getExternalStorageDirectory(), walked, depth = 0)
        } catch (e: Exception) {
            Log.w(TAG, "File walk failed", e)
        }
        val missing = walked.keys.filter { it !in indexed }
        if (missing.isEmpty()) return indexed
        // each file is handed to the scanner once (its index change would otherwise trigger rescans)
        val toScan = synchronized(handedToScanner) { missing.filter { handedToScanner.add(it) }.take(500) }
        if (toScan.isNotEmpty()) try {
            MediaScannerConnection.scanFile(context, toScan.toTypedArray(), null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Media scan failed", e)
        }
        val all = LinkedHashMap(indexed)
        missing.forEach { all[it] = walked.getValue(it) }
        return all
    }

    private val handedToScanner = HashSet<String>()

    private fun build(found: Map<String, DocFile>, loading: Boolean): ScanState {
        val grouped = found.values
            .groupBy { it.type }
            .mapValues { (_, list) -> list.sortedByDescending { it.modified } }
        val (used, total) = storageStats()
        return ScanState(loading = loading, loaded = true, files = grouped, storageUsed = used, storageTotal = total)
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
        // LIKE already ignores upper / lower case; LOWER() is left out because newer Android
        // versions check the query strictly and may refuse it
        val selection = extensions.joinToString(" OR ") { "$dataCol LIKE ?" }
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

    /** Folder walk (files the media index hasn't picked up yet, or devices without one). */
    private fun walk(dir: File, out: MutableMap<String, DocFile>, depth: Int) {
        if (depth > 8) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                // Android/ holds app data, except Android/media (WhatsApp / Telegram documents)
                if (depth == 0 && f.name == "Android") {
                    File(f, "media").takeIf { it.isDirectory }?.let { walk(it, out, depth + 2) }
                    continue
                }
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
