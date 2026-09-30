package com.theoccess.alldocreader.data

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.json.JSONArray
import org.json.JSONObject

/** Persists the "Recent" and "Bookmarks" lists shown on the home screen. */
object LibraryStore {

    data class Entry(val path: String, val time: Long)

    private const val KEY_RECENT = "recent_files"
    private const val KEY_BOOKMARKS = "bookmarked_files"
    private const val MAX_RECENT = 100

    private lateinit var sp: SharedPreferences

    private val _recents = MutableLiveData<List<Entry>>(emptyList())
    val recents: LiveData<List<Entry>> = _recents

    private val _bookmarks = MutableLiveData<List<Entry>>(emptyList())
    val bookmarks: LiveData<List<Entry>> = _bookmarks

    fun init(context: Context) {
        sp = context.applicationContext.getSharedPreferences("library_store", Context.MODE_PRIVATE)
        _recents.value = load(KEY_RECENT)
        _bookmarks.value = load(KEY_BOOKMARKS)
    }

    fun addRecent(path: String) {
        val list = listOf(Entry(path, System.currentTimeMillis())) +
            _recents.value.orEmpty().filterNot { it.path == path }
        update(KEY_RECENT, _recents, list.take(MAX_RECENT))
    }

    fun removeRecent(path: String) {
        update(KEY_RECENT, _recents, _recents.value.orEmpty().filterNot { it.path == path })
    }

    fun isBookmarked(path: String): Boolean = _bookmarks.value.orEmpty().any { it.path == path }

    /** @return true if the file is bookmarked after the call. */
    fun toggleBookmark(path: String): Boolean {
        val current = _bookmarks.value.orEmpty()
        return if (current.any { it.path == path }) {
            update(KEY_BOOKMARKS, _bookmarks, current.filterNot { it.path == path })
            false
        } else {
            update(KEY_BOOKMARKS, _bookmarks, listOf(Entry(path, System.currentTimeMillis())) + current)
            true
        }
    }

    /** Keeps recent/bookmark entries pointing at a renamed file. */
    fun renamePath(oldPath: String, newPath: String) {
        update(KEY_RECENT, _recents, _recents.value.orEmpty().map { if (it.path == oldPath) it.copy(path = newPath) else it })
        update(KEY_BOOKMARKS, _bookmarks, _bookmarks.value.orEmpty().map { if (it.path == oldPath) it.copy(path = newPath) else it })
    }

    /** Removes a deleted/missing file from both lists. */
    fun purge(path: String) {
        removeRecent(path)
        update(KEY_BOOKMARKS, _bookmarks, _bookmarks.value.orEmpty().filterNot { it.path == path })
    }

    private fun update(key: String, live: MutableLiveData<List<Entry>>, list: List<Entry>) {
        live.value = list
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("p", it.path).put("t", it.time)) }
        sp.edit().putString(key, arr.toString()).apply()
    }

    private fun load(key: String): List<Entry> = try {
        val arr = JSONArray(sp.getString(key, "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Entry(o.getString("p"), o.optLong("t"))
        }
    } catch (e: Exception) {
        emptyList()
    }
}
