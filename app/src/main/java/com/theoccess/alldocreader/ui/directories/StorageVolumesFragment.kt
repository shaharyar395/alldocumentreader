package com.theoccess.alldocreader.ui.directories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.FragmentDirListBinding
import com.theoccess.alldocreader.databinding.ItemFolderBinding
import com.theoccess.alldocreader.util.StorageAccess
import com.theoccess.alldocreader.util.StorageVolumes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** "Storage" tab: one row per volume ("0" = phone, "0000-000C" = SD card). */
class StorageVolumesFragment : Fragment() {

    private var _b: FragmentDirListBinding? = null
    private val b get() = _b!!
    private var volumes: List<Pair<File, Int>> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentDirListBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.rvList.layoutManager = LinearLayoutManager(requireContext())
        b.rvList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = volumes.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                object : RecyclerView.ViewHolder(
                    ItemFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
                ) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ib = ItemFolderBinding.bind(holder.itemView)
                val (root, count) = volumes[position]
                ib.tvName.text = StorageVolumes.displayName(root)
                ib.tvMeta.text = resources.getQuantityString(R.plurals.files_count, count, count)
                ib.root.setOnClickListener { startActivity(StorageBrowserActivity.intent(requireContext(), root)) }
            }
        }
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
        if (volumes.isEmpty()) b.progress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                StorageVolumes.roots(ctx).map { it to (it.listFiles()?.size ?: 0) }
            }
            val first = volumes.isEmpty()
            volumes = list
            _b ?: return@launch
            b.progress.visibility = View.GONE
            b.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            b.rvList.adapter?.notifyDataSetChanged()
            if (first) b.rvList.scheduleLayoutAnimation()
        }
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
