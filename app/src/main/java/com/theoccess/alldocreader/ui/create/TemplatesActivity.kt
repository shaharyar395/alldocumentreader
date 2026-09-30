package com.theoccess.alldocreader.ui.create

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityTemplatesBinding
import com.theoccess.alldocreader.databinding.ItemTplHeaderBinding
import com.theoccess.alldocreader.databinding.ItemTplMoreBinding
import com.theoccess.alldocreader.databinding.ItemTplThumbBinding
import com.theoccess.alldocreader.ui.settings.FeedbackActivity
import com.theoccess.alldocreader.ui.templates.Category
import com.theoccess.alldocreader.ui.templates.Template
import com.theoccess.alldocreader.ui.templates.TemplateEditorActivity
import com.theoccess.alldocreader.ui.templates.TplLibrary
import com.theoccess.alldocreader.ui.templates.TplRenderer
import com.theoccess.alldocreader.util.TopPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Select a template": tabs Resume / Letter / Briefing / Poster above one long list of
 * sections (the tab follows the scrolling, tapping a tab jumps to its section), and a
 * "Need more templates? Tell us" card at the end. Tapping a template opens it in the editor.
 */
class TemplatesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTemplatesBinding

    private sealed class Row {
        class Header(val cat: Category) : Row()
        class Thumb(val tpl: Template) : Row()
        object More : Row()
    }

    private val rows: List<Row> = buildList {
        for (cat in Category.values()) {
            add(Row.Header(cat))
            TplLibrary.ALL.filter { it.category == cat }.forEach { add(Row.Thumb(it)) }
        }
        add(Row.More)
    }

    private val thumbs = HashMap<String, Bitmap>()
    private var syncingTabs = false

    private val editor = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK && r.data?.getBooleanExtra(TemplateEditorActivity.EXTRA_SAVED, false) == true) {
            TopPill.show(this, getString(R.string.saved_successfully), R.drawable.ic_check_circle, 112, R.drawable.bg_success_pill)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTemplatesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }

        Category.values().forEach { binding.tabs.addTab(binding.tabs.newTab().setText(label(it))) }
        binding.tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { if (!syncingTabs) jumpTo(Category.values()[tab.position]) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) { jumpTo(Category.values()[tab.position]) }
        })

        val grid = GridLayoutManager(this, 2)
        grid.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int) = if (rows[position] is Row.Thumb) 1 else 2
        }
        binding.rvList.layoutManager = grid
        binding.rvList.adapter = Adapter()
        binding.rvList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                // (0, 0) comes from layouts (tab jumps, thumbnails arriving): keep the tapped tab
                if (dx == 0 && dy == 0) return
                val first = grid.findFirstVisibleItemPosition()
                if (first < 0) return
                // the section of the first row on screen (the last tab once the end is reached)
                val cat = if (!rv.canScrollVertically(1)) Category.values().last() else categoryAt(first)
                if (binding.tabs.selectedTabPosition != cat.ordinal) {
                    syncingTabs = true
                    binding.tabs.getTabAt(cat.ordinal)?.select()
                    syncingTabs = false
                }
            }
        })

        // thumbnails are drawn in the background, section by section
        val w = (resources.displayMetrics.widthPixels / 2).coerceAtMost(520)
        lifecycleScope.launch {
            for (tpl in TplLibrary.ALL) {
                val bmp = withContext(Dispatchers.Default) { TplRenderer.thumbnail(applicationContext, tpl.build(), w) }
                thumbs[tpl.id] = bmp
                val pos = rows.indexOfFirst { it is Row.Thumb && it.tpl.id == tpl.id }
                binding.rvList.adapter?.notifyItemChanged(pos)
            }
        }
    }

    private fun categoryAt(pos: Int): Category {
        for (i in pos downTo 0) {
            when (val r = rows[i]) {
                is Row.Header -> return r.cat
                is Row.Thumb -> return r.tpl.category
                else -> {}
            }
        }
        return Category.RESUME
    }

    private fun jumpTo(cat: Category) {
        val pos = rows.indexOfFirst { it is Row.Header && it.cat == cat }
        (binding.rvList.layoutManager as GridLayoutManager).scrollToPositionWithOffset(pos, 0)
    }

    private fun label(cat: Category) = getString(
        when (cat) {
            Category.RESUME -> R.string.tpl_resume
            Category.LETTER -> R.string.tpl_letter
            Category.BRIEFING -> R.string.tpl_briefing
            Category.POSTER -> R.string.tpl_poster
        }
    )

    private inner class Adapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = rows.size
        override fun getItemViewType(position: Int) = when (rows[position]) {
            is Row.Header -> 0
            is Row.Thumb -> 1
            Row.More -> 2
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            val v = when (viewType) {
                0 -> ItemTplHeaderBinding.inflate(inf, parent, false).root
                1 -> ItemTplThumbBinding.inflate(inf, parent, false).root
                else -> ItemTplMoreBinding.inflate(inf, parent, false).root
            }
            return object : RecyclerView.ViewHolder(v) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val r = rows[position]) {
                is Row.Header -> ItemTplHeaderBinding.bind(holder.itemView).tvHeader.text = label(r.cat)
                is Row.Thumb -> {
                    val b = ItemTplThumbBinding.bind(holder.itemView)
                    val bmp = thumbs[r.tpl.id]
                    if (bmp != null) b.ivThumb.setImageBitmap(bmp) else b.ivThumb.setImageDrawable(null)
                    b.card.setOnClickListener {
                        editor.launch(Intent(this@TemplatesActivity, TemplateEditorActivity::class.java)
                            .putExtra(TemplateEditorActivity.EXTRA_ID, r.tpl.id))
                    }
                }
                Row.More -> ItemTplMoreBinding.bind(holder.itemView).rowMore.setOnClickListener {
                    startActivity(Intent(this@TemplatesActivity, FeedbackActivity::class.java))
                }
            }
        }
    }
}
