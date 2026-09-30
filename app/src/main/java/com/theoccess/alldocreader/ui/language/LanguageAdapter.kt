package com.theoccess.alldocreader.ui.language

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.AppLanguage
import com.theoccess.alldocreader.data.Languages
import com.theoccess.alldocreader.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val items: List<AppLanguage>,
    selected: String
) : RecyclerView.Adapter<LanguageAdapter.VH>() {

    var selectedTag: String = selected
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLanguageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemLanguageBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: AppLanguage) {
            b.tvName.text = if (item.tag == Languages.DEFAULT_TAG)
                b.root.context.getString(R.string.language_default) else item.displayName
            b.rbSelected.isChecked = item.tag == selectedTag
            b.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION || selectedTag == item.tag) return@setOnClickListener
                val old = items.indexOfFirst { it.tag == selectedTag }
                selectedTag = item.tag
                if (old >= 0) notifyItemChanged(old)
                notifyItemChanged(pos)
            }
        }
    }
}
