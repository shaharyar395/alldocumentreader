package com.theoccess.alldocreader.ui.files

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.databinding.ItemFileBinding
import com.theoccess.alldocreader.util.formatSize
import com.theoccess.alldocreader.util.formatWhen

/**
 * List row used by Recent, Bookmarks, category lists, search and directories.
 * Supports a selection mode where the bookmark/⋮ buttons turn into a checkbox.
 */
class FileAdapter(
    private val onOpen: (DocFile) -> Unit,
    private val onMore: (DocFile) -> Unit,
    /** Time to show in the subtitle; defaults to the file's modified date. */
    private val timeOf: (DocFile) -> Long = { it.modified },
    /** Long-press handler (used to start selection). */
    private val onLongPress: ((DocFile) -> Unit)? = null,
    /** Called whenever the selection changes. */
    private val onSelectionChanged: ((Int) -> Unit)? = null,
    /** Show the bookmark and ⋮ buttons (hidden in Directories lists, like the original). */
    private val showActions: Boolean = true
) : ListAdapter<DocFile, FileAdapter.VH>(DIFF) {

    private var bookmarked: Set<String> = LibraryStore.bookmarks.value.orEmpty().map { it.path }.toSet()

    var selectionMode = false
        private set
    private val selected = LinkedHashSet<String>()

    val selectedFiles: List<DocFile> get() = currentList.filter { it.path in selected }
    val selectedCount: Int get() = selected.size
    val allSelected: Boolean get() = currentList.isNotEmpty() && selected.size == currentList.size

    fun setBookmarks(paths: Set<String>) {
        if (paths == bookmarked) return
        bookmarked = paths
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
    }

    fun startSelection(first: DocFile? = null) {
        selectionMode = true
        selected.clear()
        first?.let { selected += it.path }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
        onSelectionChanged?.invoke(selected.size)
    }

    fun endSelection() {
        selectionMode = false
        selected.clear()
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
        onSelectionChanged?.invoke(0)
    }

    fun toggleSelectAll() {
        if (allSelected) selected.clear() else currentList.forEach { selected += it.path }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_STATE)
        onSelectionChanged?.invoke(selected.size)
    }

    private fun toggle(item: DocFile, position: Int) {
        if (!selected.remove(item.path)) selected += item.path
        notifyItemChanged(position, PAYLOAD_STATE)
        onSelectionChanged?.invoke(selected.size)
    }

    override fun submitList(list: List<DocFile>?) {
        // Drop selections of files that disappeared (deleted / filtered out).
        if (list != null && selected.isNotEmpty()) {
            val paths = list.map { it.path }.toSet()
            if (selected.retainAll(paths)) onSelectionChanged?.invoke(selected.size)
        }
        super.submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    override fun onBindViewHolder(holder: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_STATE)) holder.bindState(getItem(position))
        else super.onBindViewHolder(holder, position, payloads)
    }

    inner class VH(private val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: DocFile) {
            b.ivIcon.setImageResource(item.type.icon)
            b.tvName.text = item.name
            b.tvMeta.text = "${formatWhen(timeOf(item))} · ${formatSize(item.size)}"
            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                if (selectionMode) toggle(item, pos) else onOpen(item)
            }
            b.root.setOnLongClickListener {
                val handler = onLongPress ?: return@setOnLongClickListener false
                if (selectionMode) return@setOnLongClickListener false
                handler(item)
                true
            }
            b.btnMore.setOnClickListener { onMore(item) }
            b.btnBookmark.setOnClickListener { com.theoccess.alldocreader.util.FileActions.toggleBookmark(it.context, item) }
            bindState(item)
        }

        fun bindState(item: DocFile) {
            val actions = showActions && !selectionMode
            b.btnBookmark.visibility = if (actions) View.VISIBLE else View.GONE
            b.btnMore.visibility = if (actions) View.VISIBLE else View.GONE
            b.cbSelect.visibility = if (selectionMode) View.VISIBLE else View.GONE
            b.cbSelect.isChecked = item.path in selected

            val on = item.path in bookmarked
            b.btnBookmark.setImageResource(if (on) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border)
            ImageViewCompat.setImageTintList(
                b.btnBookmark,
                ColorStateList.valueOf(
                    ContextCompat.getColor(b.root.context, if (on) R.color.bookmark_yellow else R.color.text_hint)
                )
            )
            b.btnBookmark.contentDescription =
                b.root.context.getString(if (on) R.string.remove_bookmark else R.string.add_bookmark)
        }
    }

    companion object {
        private const val PAYLOAD_STATE = "state"
        private val DIFF = object : DiffUtil.ItemCallback<DocFile>() {
            override fun areItemsTheSame(a: DocFile, b: DocFile) = a.path == b.path
            override fun areContentsTheSame(a: DocFile, b: DocFile) = a == b
        }
    }
}
