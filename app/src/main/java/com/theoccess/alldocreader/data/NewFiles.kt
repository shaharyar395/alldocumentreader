package com.theoccess.alldocreader.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.io.File

/**
 * "New" files: documents that appeared on the phone since the app last looked (downloaded,
 * received, copied over USB…). They get a "New" tag and a light-gray row in the file lists
 * until the user opens them, and are counted next to "Recently added" on the home screen.
 *
 * Every finished scan is compared with the paths the app already knew ([onScan]). The very
 * first scan only records what is there (nothing is "new" right after installing). Files the
 * app saves itself and files the user opens are never / no longer new ([markOpened]).
 */
object NewFiles {

    private const val KNOWN_FILE = "known_files.txt"
    private const val NEW_FILE = "new_files.txt"
    /** A "new" tag goes away by itself after this long, even when the file is never opened. */
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    /** More new files than this in one scan = a memory card / permission came back: not "new". */
    private const val MAX_BURST = 300

    private var dir: File? = null
    private var known: MutableSet<String>? = null
    /** path → when it was found */
    @Volatile private var fresh: Map<String, Long> = emptyMap()
    @Volatile private var loaded = false

    private val _changes = MutableLiveData(0)
    /** Changes whenever the set of new files changes (lists re-draw their rows). */
    val changes: LiveData<Int> = _changes

    fun init(context: Context) {
        if (dir == null) dir = context.applicationContext.filesDir
    }

    fun isNew(path: String): Boolean {
        ensureLoaded()
        return path in fresh
    }

    val count: Int get() { ensureLoaded(); return fresh.size }

    /** When [path] was found (for sorting the Recently added list), or null. */
    fun foundAt(path: String): Long? { ensureLoaded(); return fresh[path] }

    /** The user opened the file (or the app saved it): no "New" tag any more. */
    fun markOpened(path: String) {
        ensureLoaded()
        synchronized(this) {
            known?.add(path)
            if (path !in fresh) return   // (not written now: opened files are in Recent, which onScan skips)
            fresh = fresh - path
            save()
        }
        notifyChanged()
    }

    /** Renamed in the app: same file, keeps its state. */
    fun renamed(oldPath: String, newPath: String) {
        ensureLoaded()
        synchronized(this) {
            known?.add(newPath)
            val at = fresh[oldPath]
            if (at != null) fresh = fresh - oldPath + (newPath to at)
            save()
        }
        notifyChanged()
    }

    /** After every complete storage scan (background thread). */
    fun onScan(paths: Collection<String>) {
        if (paths.isEmpty()) return   // no access / nothing found: don't treat everything as new later
        ensureLoaded()
        val changed: Boolean
        synchronized(this) {
            val k = known
            val now = System.currentTimeMillis()
            if (k == null) {
                // first scan after installing: remember what is there, nothing is "new"
                known = HashSet(paths)
                save()
                return
            }
            // files the user opened or the app saved are in Recent: never "new"
            val recent = LibraryStore.recents.value.orEmpty().mapTo(HashSet()) { it.path }
            val added = paths.filter { it !in k && it !in recent }
            k.addAll(added)
            val present = paths.toHashSet()
            var map = fresh.filter { (p, at) -> p in present && now - at < MAX_AGE_MS }
            if (added.isNotEmpty() && added.size <= MAX_BURST) map = map + added.associateWith { now }
            changed = map.keys != fresh.keys
            fresh = map
            if (changed || added.isNotEmpty()) save()
        }
        if (changed) notifyChanged()
    }

    // ------------------------------------------------------------------ storage

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val d = dir
            if (d != null) {
                val kf = File(d, KNOWN_FILE)
                if (kf.exists()) known = try { kf.readLines().filter { it.isNotEmpty() }.toHashSet() } catch (e: Exception) { HashSet() }
                val nf = File(d, NEW_FILE)
                if (nf.exists()) fresh = try {
                    nf.readLines().mapNotNull { line ->
                        val tab = line.indexOf('\t')
                        if (tab <= 0) null else line.substring(tab + 1) to (line.substring(0, tab).toLongOrNull() ?: 0L)
                    }.toMap()
                } catch (e: Exception) { emptyMap() }
            }
            loaded = true
        }
    }

    private fun save() {
        val d = dir ?: return
        try {
            known?.let { k -> File(d, KNOWN_FILE).writeText(k.joinToString("\n")) }
            File(d, NEW_FILE).writeText(fresh.entries.joinToString("\n") { "${it.value}\t${it.key}" })
        } catch (ignored: Exception) {
        }
    }

    private fun notifyChanged() {
        _changes.postValue((_changes.value ?: 0) + 1)
    }
}
