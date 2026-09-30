package com.theoccess.alldocreader.ui.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.SheetAddWidgetBinding
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.toast
import com.theoccess.alldocreader.widget.EditPdfSmallWidget
import com.theoccess.alldocreader.widget.EditPdfWidget
import com.theoccess.alldocreader.widget.ReaderWidget
import com.theoccess.alldocreader.widget.ToolsWidget

/** "Add widget": swipe through the home-screen widgets and pin one with "+ Add widget". */
object WidgetSheet {

    private class W(val title: Int, val subtitle: Int, val layout: Int, val provider: Class<*>, val wDp: Int, val hDp: Int)

    private val widgets = listOf(
        W(R.string.w_tools_title, R.string.w_tools_desc, R.layout.widget_tools, ToolsWidget::class.java, 270, 96),
        W(R.string.w_reader_title, R.string.w_reader_desc, R.layout.widget_reader, ReaderWidget::class.java, 270, 96),
        W(R.string.w_editor_title, R.string.w_editor_desc, R.layout.widget_edit_big, EditPdfWidget::class.java, 128, 128),
        W(R.string.w_editor_title, R.string.w_editor_desc, R.layout.widget_edit_small, EditPdfSmallWidget::class.java, 76, 76)
    )

    fun show(context: Context) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetAddWidgetBinding.inflate(LayoutInflater.from(context))

        b.pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount() = widgets.size
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val stage = FrameLayout(parent.context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setPadding(parent.context.dp(6), 0, parent.context.dp(6), 0)
                }
                val card = FrameLayout(parent.context).apply {
                    background = ContextCompat.getDrawable(parent.context, R.drawable.bg_widget_stage)
                }
                stage.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                return object : RecyclerView.ViewHolder(stage) {}
            }
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val card = (holder.itemView as FrameLayout).getChildAt(0) as FrameLayout
                card.removeAllViews()
                val w = widgets[position]
                val preview = LayoutInflater.from(card.context).inflate(w.layout, card, false)
                preview.elevation = card.context.dp(3).toFloat()
                card.addView(preview, FrameLayout.LayoutParams(card.context.dp(w.wDp), card.context.dp(w.hDp), Gravity.CENTER))
            }
        }
        widgets.indices.forEach { _ ->
            b.dots.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(context.dp(6), context.dp(6)).apply { marginStart = context.dp(3); marginEnd = context.dp(3) } })
        }
        fun select(i: Int) {
            b.tvTitle.setText(widgets[i].title)
            b.tvSubtitle.setText(widgets[i].subtitle)
            for (d in 0 until b.dots.childCount) {
                val dot = b.dots.getChildAt(d)
                dot.layoutParams = (dot.layoutParams as LinearLayout.LayoutParams).apply { width = context.dp(if (d == i) 14 else 6) }
                dot.background = GradientDrawable().apply {
                    cornerRadius = context.dp(3).toFloat()
                    setColor(if (d == i) ContextCompat.getColor(context, R.color.primary) else 0xFFD5DAE1.toInt())
                }
            }
        }
        b.pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = select(position)
        })
        select(0)
        b.btnClose.setOnClickListener { dialog.dismiss() }
        b.btnAdd.setOnClickListener {
            if (pin(context, widgets[b.pager.currentItem].provider)) dialog.dismiss()
        }
        dialog.setContentView(b.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        dialog.show()
    }

    /** Asks the launcher to place the widget (Android 8+); otherwise explains how to add it. */
    private fun pin(context: Context, provider: Class<*>): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(AppWidgetManager::class.java)
            if (manager != null && manager.isRequestPinAppWidgetSupported) {
                return try {
                    manager.requestPinAppWidget(ComponentName(context, provider), null, null)
                } catch (e: Exception) {
                    context.toast(R.string.widget_manual_hint)
                    false
                }
            }
        }
        context.toast(R.string.widget_manual_hint)
        return false
    }
}
