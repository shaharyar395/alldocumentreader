package com.theoccess.alldocreader.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.theoccess.alldocreader.data.Category
import com.theoccess.alldocreader.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private val items = Category.entries.toList()
    private var counts: Map<Category, String> = emptyMap()
    private var loading = false

    fun update(counts: Map<Category, String>, loading: Boolean) {
        this.counts = counts
        this.loading = loading
        notifyItemRangeChanged(0, items.size)
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])

    inner class VH(private val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: Category) {
            b.ivIcon.setImageResource(c.icon)
            b.tvLabel.setText(c.label)
            val text = counts[c]
            b.pbCount.visibility = if (loading && text == null) View.VISIBLE else View.GONE
            b.tvCount.text = text.orEmpty()
            b.root.setOnClickListener { onClick(c) }
        }
    }
}
