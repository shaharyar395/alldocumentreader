package com.theoccess.alldocreader.ui.directories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.data.RecycleBin
import com.theoccess.alldocreader.data.SavedFiles
import com.theoccess.alldocreader.databinding.ActivityRecycleBinBinding
import com.theoccess.alldocreader.databinding.ItemFileBinding
import com.theoccess.alldocreader.databinding.SheetConfirmBinding
import com.theoccess.alldocreader.util.ActionSheet
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Tools → Recycle bin, like the original: "Files will be permanently deleted 30 days after being
 * moved here.", each file with the days it has left; the check icon starts selection
 * ("N selected", select all) with Restore / Delete at the bottom.
 */
class RecycleBinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecycleBinBinding
    private var items: List<RecycleBin.Item> = emptyList()
    private val chosen = LinkedHashSet<String>()
    private var selecting = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = setSelecting(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecycleBinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backCallback)
        binding.btnBack.setOnClickListener { if (selecting) setSelecting(false) else finish() }
        binding.btnSelect.setOnClickListener {
            if (!selecting) setSelecting(true)
            else {
                if (chosen.size == items.size) chosen.clear() else items.forEach { chosen += it.binName }
                refresh()
            }
        }
        binding.btnRestore.setOnClickListener { restore(items.filter { it.binName in chosen }) }
        binding.btnDelete.setOnClickListener { confirmDelete(items.filter { it.binName in chosen }) }
        binding.rvList.layoutManager = LinearLayoutManager(this)
        binding.rvList.adapter = Adapter()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) {
                RecycleBin.purgeExpired(applicationContext)
                RecycleBin.items(applicationContext).sortedByDescending { it.deletedAt }
            }
            items = list
            chosen.retainAll(list.map { it.binName }.toSet())
            if (list.isEmpty()) selecting = false
            refresh()
        }
    }

    private fun setSelecting(on: Boolean) {
        selecting = on && items.isNotEmpty()
        chosen.clear()
        refresh()
    }

    private fun refresh() {
        binding.tvTitle.text = if (selecting) getString(R.string.selected_count, chosen.size) else getString(R.string.recycle_bin)
        binding.btnSelect.setImageResource(if (selecting) R.drawable.ic_select_all else R.drawable.ic_select)
        binding.btnSelect.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        binding.bottomBar.visibility = if (selecting) View.VISIBLE else View.GONE
        val any = chosen.isNotEmpty()
        listOf(binding.btnRestore, binding.btnDelete).forEach { it.isEnabled = any; it.alpha = if (any) 1f else 0.4f }
        binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        backCallback.isEnabled = selecting
        binding.rvList.adapter?.notifyDataSetChanged()
    }

    private fun showActions(item: RecycleBin.Item) {
        val name = File(item.originalPath).name
        ActionSheet(this)
            .header(FileType.fromName(name).icon, name, item.originalPath)
            .action(R.drawable.ic_restore, getString(R.string.restore)) { restore(listOf(item)) }
            .action(R.drawable.ic_delete_forever, getString(R.string.delete_permanently)) { confirmDelete(listOf(item)) }
            .show()
    }

    private fun restore(list: List<RecycleBin.Item>) {
        if (list.isEmpty()) return
        lifecycleScope.launch {
            val restored = withContext(Dispatchers.IO) {
                list.filter { RecycleBin.restore(applicationContext, it) }
            }
            if (restored.isEmpty()) toast(R.string.restore_failed)
            else {
                toast(resources.getQuantityString(R.plurals.restored_count, restored.size, restored.size))
                restored.forEach { SavedFiles.onSaved(applicationContext, File(it.originalPath)) }
                FileRepository.refresh(applicationContext, force = true)
            }
            selecting = false
            chosen.clear()
            load()
        }
    }

    private fun confirmDelete(list: List<RecycleBin.Item>) {
        if (list.isEmpty()) return
        val dialog = BottomSheetDialog(this, R.style.Theme_DocReader_BottomSheet)
        val b = SheetConfirmBinding.inflate(LayoutInflater.from(this))
        b.tvTitle.setText(R.string.delete_permanently)
        b.tvMessage.text = resources.getQuantityString(R.plurals.delete_forever_message, list.size, list.size)
        b.btnOk.setText(R.string.delete)
        b.btnCancel.setOnClickListener { dialog.dismiss() }
        b.btnOk.setOnClickListener {
            dialog.dismiss()
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { list.forEach { RecycleBin.deletePermanently(applicationContext, it) } }
                toast(R.string.deleted_successfully)
                selecting = false
                chosen.clear()
                load()
            }
        }
        dialog.setContentView(b.root)
        dialog.show()
    }

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false).root) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val ib = ItemFileBinding.bind(holder.itemView)
            val item = items[position]
            val name = File(item.originalPath).name
            ib.ivIcon.setImageResource(FileType.fromName(name).icon)
            ib.tvName.text = name
            val days = RecycleBin.daysLeft(item)
            ib.tvMeta.text = resources.getQuantityString(R.plurals.days_count, days, days)
            ib.btnBookmark.visibility = View.GONE
            ib.btnMore.visibility = View.GONE
            ib.cbSelect.visibility = if (selecting) View.VISIBLE else View.GONE
            ib.cbSelect.isChecked = item.binName in chosen
            ib.root.setOnClickListener {
                if (selecting) {
                    if (!chosen.remove(item.binName)) chosen += item.binName
                    refresh()
                } else showActions(item)
            }
            ib.root.setOnLongClickListener {
                if (!selecting) { selecting = true; chosen += item.binName; refresh() }
                true
            }
        }
    }
}
