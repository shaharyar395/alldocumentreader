package com.theoccess.alldocreader.ui.directories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.data.RecycleBin
import com.theoccess.alldocreader.databinding.FragmentDirListBinding
import com.theoccess.alldocreader.databinding.ItemFileBinding
import com.theoccess.alldocreader.util.ActionSheet
import com.theoccess.alldocreader.util.formatSize
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** "Recycle bin" tab: restore or permanently delete binned files. */
class RecycleBinFragment : Fragment() {

    private var _b: FragmentDirListBinding? = null
    private val b get() = _b!!
    private var items: List<RecycleBin.Item> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _b = FragmentDirListBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        b.tvEmptySub.visibility = View.VISIBLE
        b.tvEmptySub.setText(R.string.recycle_message)
        b.btnClearBin.setOnClickListener { confirmClear() }
        b.rvList.layoutManager = LinearLayoutManager(requireContext())
        b.rvList.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = items.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                object : RecyclerView.ViewHolder(
                    ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false).root
                ) {}
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val ib = ItemFileBinding.bind(holder.itemView)
                val item = items[position]
                val name = File(item.originalPath).name
                ib.ivIcon.setImageResource(FileType.fromName(name).icon)
                ib.tvName.text = name
                val days = RecycleBin.daysLeft(item)
                ib.tvMeta.text = resources.getQuantityString(R.plurals.days_left, days, days) + " · " + formatSize(item.size)
                ib.btnBookmark.visibility = View.GONE
                ib.btnMore.setOnClickListener { showActions(item) }
                ib.root.setOnClickListener { showActions(item) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val ctx = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { RecycleBin.items(ctx).sortedByDescending { it.deletedAt } }
            _b ?: return@launch
            items = list
            b.rvList.adapter?.notifyDataSetChanged()
            b.emptyView.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            b.binHeader.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showActions(item: RecycleBin.Item) {
        val ctx = requireContext()
        val name = File(item.originalPath).name
        ActionSheet(ctx)
            .header(FileType.fromName(name).icon, name, item.originalPath)
            .action(R.drawable.ic_restore, getString(R.string.restore)) { restore(item) }
            .action(R.drawable.ic_delete_forever, getString(R.string.delete_permanently)) { deleteForever(item) }
            .show()
    }

    private fun restore(item: RecycleBin.Item) {
        val ctx = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) { RecycleBin.restore(ctx, item) }
            ctx.toast(if (ok) R.string.restored else R.string.restore_failed)
            if (ok) FileRepository.refresh(ctx, force = true)
            load()
        }
    }

    private fun deleteForever(item: RecycleBin.Item) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_permanently)
            .setMessage(File(item.originalPath).name)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val ctx = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { RecycleBin.deletePermanently(ctx, item) }
                    load()
                }
            }
            .show()
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.empty_recycle_bin)
            .setMessage(R.string.empty_recycle_bin_confirm)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val ctx = requireContext().applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    withContext(Dispatchers.IO) { RecycleBin.clear(ctx) }
                    load()
                }
            }
            .show()
    }

    override fun onDestroyView() {
        _b = null
        super.onDestroyView()
    }
}
