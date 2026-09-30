package com.theoccess.alldocreader.ui.files

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Category
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.data.sortedByMode
import com.theoccess.alldocreader.databinding.ActivityFileListBinding
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.toast

/**
 * "All files" (and PDF / Word / Excel / PPT / TXT / Image) list.
 * Toolbar: search, select, filter. Rows: bookmark + ⋮ menu. Rows fade in on load.
 */
class FileListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFileListBinding
    private lateinit var adapter: FileAdapter
    private lateinit var state: ListStateHelper
    private lateinit var category: Category

    private var query = ""
    /** Type chosen in the search tabs (null = All). */
    private var tabType: FileType? = null
    private var searching = false
    private var animatedOnce = false
    /** "Recently added" from the home screen: files from the last 30 days. */
    private var recentlyAdded = false

    /** Tabs shown while searching inside "All files". */
    private val searchTabs = listOf<Pair<Int, FileType?>>(
        R.string.cat_all to null,
        R.string.cat_pdf to FileType.PDF,
        R.string.cat_word to FileType.WORD,
        R.string.cat_excel to FileType.EXCEL,
        R.string.cat_ppt to FileType.PPT,
        R.string.cat_txt to FileType.TXT,
        R.string.cat_image to FileType.IMAGE
    )

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                adapter.selectionMode -> exitSelection()
                searching -> closeSearch()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Prefs.visitedFileList = true

        category = Category.entries.getOrElse(intent.getIntExtra(EXTRA_CATEGORY, 0)) { Category.ALL }
        state = ListStateHelper(binding.state)
        recentlyAdded = intent.getBooleanExtra(EXTRA_RECENT, false)
        if (recentlyAdded) Prefs.recentSeenAt = System.currentTimeMillis()
        binding.tvTitle.setText(
            when {
                recentlyAdded -> R.string.recently_added
                category == Category.ALL -> R.string.all_files_title
                else -> category.label
            }
        )

        setupToolbar()
        setupList()
        setupSearchTabs()
        onBackPressedDispatcher.addCallback(this, backCallback)

        FileRepository.state.observe(this) { render() }
        LibraryStore.bookmarks.observe(this) { list -> adapter.setBookmarks(list.map { it.path }.toSet()) }
        FileRepository.refresh(this)
    }

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSearch.setOnClickListener { openSearch() }
        binding.btnSelect.setOnClickListener {
            if (adapter.itemCount == 0) toast(R.string.no_files_found) else enterSelection(null)
        }
        binding.btnFilter.setOnClickListener {
            FileSheets.showFilter(this) {
                render()
                binding.rvFiles.scrollToPosition(0)
                binding.rvFiles.scheduleLayoutAnimation()
            }
        }

        binding.btnExitSelect.setOnClickListener { exitSelection() }
        binding.btnSelectAll.setOnClickListener { adapter.toggleSelectAll() }
        binding.actionShare.setOnClickListener {
            val files = adapter.selectedFiles
            if (files.isEmpty()) toast(R.string.select_files_first) else FileActions.shareMany(this, files)
        }
        binding.actionDelete.setOnClickListener {
            val files = adapter.selectedFiles
            if (files.isEmpty()) toast(R.string.select_files_first)
            else FileSheets.confirmDelete(this, files) { exitSelection() }
        }

        binding.etSearch.doAfterTextChanged {
            query = it?.toString().orEmpty().trim()
            binding.btnClearSearch.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            render()
        }
        binding.btnClearSearch.setOnClickListener { binding.etSearch.setText("") }
        binding.btnCancelSearch.setOnClickListener { closeSearch() }
    }

    private fun setupList() {
        adapter = FileAdapter(
            onOpen = { FileActions.open(this, it) },
            onMore = { FileActions.showMenu(this, it) },
            onLongPress = { enterSelection(it) },
            onSelectionChanged = { count -> onSelectionChanged(count) }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter
    }

    private fun setupSearchTabs() {
        // Type tabs only make sense for "All files".
        if (category != Category.ALL) return
        searchTabs.forEach { (label, _) ->
            binding.typeTabs.addTab(binding.typeTabs.newTab().setText(label))
        }
        binding.typeTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tabType = searchTabs[tab.position].second
                render()
                binding.rvFiles.scrollToPosition(0)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    // ------------------------------------------------------------------ render

    private fun currentFiles(): List<DocFile> {
        val s = FileRepository.current
        val since = System.currentTimeMillis() - RECENT_WINDOW_MS
        return s.filesFor(category)
            .filter { !recentlyAdded || it.modified >= since }
            .filter { tabType == null || it.type == tabType }
            .filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }
            .sortedByMode(Prefs.sortMode, Prefs.sortAscending)
    }

    private fun render() {
        val s = FileRepository.current
        if (!s.loaded) {
            // First scan still running: spinner like the original app.
            binding.progress.visibility = View.VISIBLE
            state.hideAll()
            adapter.submitList(emptyList())
            return
        }
        binding.progress.visibility = View.GONE
        val files = currentFiles()
        val firstFill = !animatedOnce && files.isNotEmpty()
        adapter.submitList(files)
        if (firstFill) {
            animatedOnce = true
            binding.rvFiles.scheduleLayoutAnimation()
        }
        state.showContent(files.isEmpty())
    }

    // ------------------------------------------------------------------ search

    private fun openSearch() {
        searching = true
        binding.normalBar.visibility = View.GONE
        binding.searchBar.visibility = View.VISIBLE
        if (category == Category.ALL) binding.typeTabs.visibility = View.VISIBLE
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
        binding.typeTabs.visibility = View.GONE
        binding.normalBar.visibility = View.VISIBLE
        if (tabType != null) {
            tabType = null
            binding.typeTabs.getTabAt(0)?.select()
        }
        render()
        updateBack()
    }

    // ------------------------------------------------------------------ selection

    private fun enterSelection(first: DocFile?) {
        if (searching) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                .hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        }
        adapter.startSelection(first)
        binding.searchBar.visibility = View.GONE
        binding.selectBar.visibility = View.VISIBLE
        binding.selectActions.visibility = View.VISIBLE
        binding.selectActions.translationY = binding.selectActions.height.toFloat().coerceAtLeast(120f)
        binding.selectActions.animate().translationY(0f).setDuration(200).start()
        updateBack()
    }

    private fun exitSelection() {
        if (!adapter.selectionMode) return
        adapter.endSelection()
        binding.selectBar.visibility = View.GONE
        binding.selectActions.visibility = View.GONE
        if (searching) binding.searchBar.visibility = View.VISIBLE
        updateBack()
    }

    private fun onSelectionChanged(count: Int) {
        binding.tvSelected.text = getString(R.string.selected_count, count)
        binding.btnSelectAll.alpha = if (adapter.allSelected) 1f else 0.75f
        val enabled = count > 0
        binding.actionShare.alpha = if (enabled) 1f else 0.4f
        binding.actionDelete.alpha = if (enabled) 1f else 0.4f
    }

    private fun updateBack() {
        backCallback.isEnabled = adapter.selectionMode || searching
    }

    companion object {
        private const val EXTRA_CATEGORY = "category"
        private const val EXTRA_RECENT = "recently_added"
        private const val RECENT_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

        fun intent(context: Context, category: Category, recentlyAdded: Boolean = false) =
            Intent(context, FileListActivity::class.java)
                .putExtra(EXTRA_CATEGORY, category.ordinal)
                .putExtra(EXTRA_RECENT, recentlyAdded)
    }
}
