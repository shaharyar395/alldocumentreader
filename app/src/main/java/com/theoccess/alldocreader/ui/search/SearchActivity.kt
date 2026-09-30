package com.theoccess.alldocreader.ui.search

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.databinding.ActivitySearchBinding
import com.theoccess.alldocreader.ui.files.FileAdapter
import com.theoccess.alldocreader.ui.files.ListStateHelper
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.StorageAccess

/** Searches every scanned document by name. */
class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: FileAdapter
    private lateinit var state: ListStateHelper
    private var query = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        state = ListStateHelper(binding.state)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnClear.setOnClickListener { binding.etSearch.setText("") }
        binding.etSearch.doAfterTextChanged {
            query = it?.toString().orEmpty().trim()
            binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
            render()
        }

        adapter = FileAdapter(
            onOpen = { FileActions.open(this, it) },
            onMore = { FileActions.showMenu(this, it) }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(this)
        binding.rvFiles.adapter = adapter

        FileRepository.state.observe(this) { render() }
        LibraryStore.bookmarks.observe(this) { list -> adapter.setBookmarks(list.map { it.path }.toSet()) }
        if (StorageAccess.has(this)) FileRepository.refresh(this)
        binding.etSearch.requestFocus()
    }

    private fun render() {
        val s = FileRepository.current
        if (query.isEmpty()) {
            adapter.submitList(emptyList())
            state.hideAll()
            return
        }
        if (!s.loaded && s.loading) {
            state.showLoading()
            return
        }
        // Documents first, images after.
        val results = s.all()
            .filter { it.name.contains(query, ignoreCase = true) }
            .sortedWith(compareBy({ it.type == FileType.IMAGE }, { -it.modified }))
        adapter.submitList(results)
        state.showContent(results.isEmpty())
    }
}
