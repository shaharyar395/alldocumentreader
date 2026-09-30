package com.theoccess.alldocreader.ui.directories

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.databinding.ActivityStorageBrowserBinding
import com.theoccess.alldocreader.databinding.ItemFileBinding
import com.theoccess.alldocreader.databinding.ItemFolderBinding
import com.theoccess.alldocreader.ui.files.ListStateHelper
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.StorageVolumes
import com.theoccess.alldocreader.util.formatSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Folder browser opened from the Storage tab ("Storage" title + path line). */
class StorageBrowserActivity : AppCompatActivity() {

    private sealed class Entry {
        data class Folder(val file: File, val childCount: Int, val modified: Long) : Entry()
        data class Doc(val doc: DocFile) : Entry()
    }

    private lateinit var binding: ActivityStorageBrowserBinding
    private lateinit var state: ListStateHelper
    private lateinit var root: File
    private var current: File = File("/")
    private var entries: List<Entry> = emptyList()
    private var query = ""
    private var searching = false
    private var loadJob: Job? = null
    private val adapter = EntryAdapter()
    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                searching -> closeSearch()
                current.absolutePath != root.absolutePath -> current.parentFile?.let { load(it) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorageBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        state = ListStateHelper(binding.state)
        binding.state.tvEmpty.setText(R.string.empty_folder)

        root = intent.getStringExtra(EXTRA_ROOT)?.let { File(it) }
            ?: StorageVolumes.roots(this).firstOrNull() ?: android.os.Environment.getExternalStorageDirectory()
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnSearch.setOnClickListener { openSearch() }
        binding.btnCancelSearch.setOnClickListener { closeSearch() }
        binding.etSearch.doAfterTextChanged {
            query = it?.toString().orEmpty().trim()
            render()
        }
        onBackPressedDispatcher.addCallback(this, backCallback)

        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = adapter

        val start = savedInstanceState?.getString(STATE_DIR)?.let { File(it) }
            ?.takeIf { it.absolutePath.startsWith(root.absolutePath) } ?: root
        load(start)
    }

    override fun onRestart() {
        super.onRestart()
        load(current, animate = false) // files may have been renamed/deleted from a viewer
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DIR, current.absolutePath)
    }

    private fun updateBack() {
        backCallback.isEnabled = searching || current.absolutePath != root.absolutePath
    }

    private fun load(dir: File, animate: Boolean = true) {
        current = dir
        updateBack()
        val rootName = StorageVolumes.displayName(root)
        val relative = dir.absolutePath.removePrefix(root.absolutePath).trim('/')
        binding.tvPath.text = buildString {
            append(rootName)
            if (relative.isNotEmpty()) relative.split('/').forEach { append("  ›  ").append(it) }
        }
        binding.pathScroll.post { binding.pathScroll.fullScroll(View.FOCUS_RIGHT) }

        if (animate) {
            binding.progress.visibility = View.VISIBLE
            state.hideAll()
        }
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { list(dir) }
            entries = list
            binding.progress.visibility = View.GONE
            render()
            if (animate) {
                binding.rvList.scrollToPosition(0)
                binding.rvList.scheduleLayoutAnimation()
            }
        }
    }

    private fun render() {
        val shown = if (query.isEmpty()) entries else entries.filter {
            val name = when (it) { is Entry.Folder -> it.file.name; is Entry.Doc -> it.doc.name }
            name.contains(query, ignoreCase = true)
        }
        adapter.submitList(shown)
        state.showContent(shown.isEmpty())
    }

    /** Folders first, then files; newest first like the original app. Hidden items are shown too. */
    private fun list(dir: File): List<Entry> {
        val children = dir.listFiles() ?: return emptyList()
        val folders = children.filter { it.isDirectory }
            .map { Entry.Folder(it, it.listFiles()?.size ?: 0, it.lastModified()) }
            .sortedByDescending { it.modified }
        val files = children.filter { it.isFile }
            .map { Entry.Doc(DocFile.from(it)) }
            .sortedByDescending { it.doc.modified }
        return folders + files
    }

    private fun openSearch() {
        searching = true
        binding.normalBar.visibility = View.GONE
        binding.searchBar.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        updateBack()
    }

    private fun closeSearch() {
        searching = false
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        binding.etSearch.setText("")
        binding.searchBar.visibility = View.GONE
        binding.normalBar.visibility = View.VISIBLE
        updateBack()
    }

    private inner class EntryAdapter : ListAdapter<Entry, RecyclerView.ViewHolder>(DIFF) {
        override fun getItemViewType(position: Int) = if (getItem(position) is Entry.Folder) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == 0) FolderVH(ItemFolderBinding.inflate(inf, parent, false))
            else DocVH(ItemFileBinding.inflate(inf, parent, false))
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val e = getItem(position)) {
                is Entry.Folder -> (holder as FolderVH).bind(e)
                is Entry.Doc -> (holder as DocVH).bind(e.doc)
            }
        }
    }

    private inner class FolderVH(val b: ItemFolderBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(e: Entry.Folder) {
            b.tvName.text = e.file.name
            b.tvMeta.text = resources.getQuantityString(R.plurals.files_count, e.childCount, e.childCount)
            b.root.setOnClickListener {
                if (searching) closeSearch()
                load(e.file)
            }
        }
    }

    private inner class DocVH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(doc: DocFile) {
            b.ivIcon.setImageResource(doc.type.icon)
            b.tvName.text = doc.name
            b.tvMeta.text = "${dateFormat.format(Date(doc.modified))} · ${formatSize(doc.size)}"
            b.btnBookmark.visibility = View.GONE
            b.btnMore.visibility = View.GONE
            b.root.setOnClickListener { FileActions.open(this@StorageBrowserActivity, doc) }
            b.root.setOnLongClickListener {
                FileActions.showMenu(this@StorageBrowserActivity, doc) { load(current, animate = false) }
                true
            }
        }
    }

    companion object {
        private const val EXTRA_ROOT = "root"
        private const val STATE_DIR = "dir"

        fun intent(context: Context, root: File) =
            Intent(context, StorageBrowserActivity::class.java).putExtra(EXTRA_ROOT, root.absolutePath)

        private val DIFF = object : DiffUtil.ItemCallback<Entry>() {
            override fun areItemsTheSame(a: Entry, b: Entry): Boolean = when {
                a is Entry.Folder && b is Entry.Folder -> a.file == b.file
                a is Entry.Doc && b is Entry.Doc -> a.doc.path == b.doc.path
                else -> false
            }
            override fun areContentsTheSame(a: Entry, b: Entry) = a == b
        }
    }
}
