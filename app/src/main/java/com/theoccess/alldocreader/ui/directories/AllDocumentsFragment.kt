package com.theoccess.alldocreader.ui.directories

import android.content.Context
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.databinding.FragmentDirListBinding
import com.theoccess.alldocreader.ui.files.FileAdapter
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** "All documents" tab: every file on the device, newest first (like the original). */
class AllDocumentsFragment : Fragment() {

    private var _b: FragmentDirListBinding? = null
    private val b get() = _b!!
    private lateinit var adapter: FileAdapter
    private var loaded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentDirListBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = FileAdapter(
            onOpen = { FileActions.open(requireContext(), it) },
            onMore = { FileActions.showMenu(requireContext(), it) { load() } },
            onLongPress = { FileActions.showMenu(requireContext(), it) { load() } },
            showActions = false
        )
        b.rvList.layoutManager = LinearLayoutManager(requireContext())
        b.rvList.adapter = adapter
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val ctx = context ?: return
        if (!StorageAccess.has(ctx)) {
            b.emptyView.visibility = View.VISIBLE
            b.tvEmptyTitle.setText(R.string.permission_needed_hint)
            return
        }
        if (!loaded) b.progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { queryAll(ctx) }
            _b ?: return@launch
            b.progress.visibility = View.GONE
            adapter.submitList(files)
            if (!loaded && files.isNotEmpty()) b.rvList.scheduleLayoutAnimation()
            loaded = true
            b.emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }

    companion object {
        private const val LIMIT = 3000

        /** All non-folder files known to MediaStore, newest first. */
        fun queryAll(context: Context): List<DocFile> {
            val out = ArrayList<DocFile>()
            @Suppress("DEPRECATION")
            val dataCol = MediaStore.Files.FileColumns.DATA
            val projection = arrayOf(
                dataCol, MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            try {
                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"), projection, null, null,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { c ->
                    val iData = c.getColumnIndexOrThrow(dataCol)
                    val iSize = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val iDate = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                    while (c.moveToNext() && out.size < LIMIT) {
                        val path = c.getString(iData) ?: continue
                        val f = File(path)
                        if (!f.isFile) continue
                        val name = f.name
                        out += DocFile(path, name, c.getLong(iSize).takeIf { it > 0 } ?: f.length(),
                            c.getLong(iDate) * 1000L, FileType.fromName(name))
                    }
                }
            } catch (ignored: Exception) {
            }
            return out
        }
    }
}
