package com.theoccess.alldocreader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Category
import com.theoccess.alldocreader.ui.create.PickFileActivity
import com.theoccess.alldocreader.ui.edit.PdfEditActivity
import com.theoccess.alldocreader.ui.files.FileListActivity
import com.theoccess.alldocreader.ui.main.MainActivity
import com.theoccess.alldocreader.ui.search.SearchActivity

/** Shared helpers: every widget button opens its screen on top of the app's home screen. */
internal object WidgetIntents {
    private fun home(context: Context) =
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    fun open(context: Context, requestCode: Int, target: Intent?, bookmarks: Boolean = false): PendingIntent {
        val start = home(context).putExtra(MainActivity.EXTRA_OPEN_BOOKMARKS, bookmarks)
        val intents = if (target == null) arrayOf(start) else arrayOf(start, target)
        return PendingIntent.getActivities(
            context, requestCode, intents, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun search(c: Context) = Intent(c, SearchActivity::class.java)
    fun editPdf(c: Context) = PickFileActivity.editIntent(c, PdfEditActivity.Mode.NONE)
    fun list(c: Context, category: Category, recent: Boolean = false) = FileListActivity.intent(c, category, recent)

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray, layout: Int, bind: (RemoteViews) -> Unit) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, layout)
            bind(views)
            manager.updateAppWidget(id, views)
        }
    }
}

/** "Document Tools": search, Home, Recent, Bookmarks, Edit PDF. */
class ToolsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        WidgetIntents.update(context, manager, ids, R.layout.widget_tools) { v ->
            v.setOnClickPendingIntent(R.id.wSearch, WidgetIntents.open(context, 10, WidgetIntents.search(context)))
            v.setOnClickPendingIntent(R.id.wHome, WidgetIntents.open(context, 11, null))
            v.setOnClickPendingIntent(R.id.wRecent, WidgetIntents.open(context, 12, WidgetIntents.list(context, Category.ALL, recent = true)))
            v.setOnClickPendingIntent(R.id.wBookmarks, WidgetIntents.open(context, 13, null, bookmarks = true))
            v.setOnClickPendingIntent(R.id.wEdit, WidgetIntents.open(context, 14, WidgetIntents.editPdf(context)))
        }
}

/** "All-in-One Reader": search, Edit PDF, PDF / Word / Excel / PPT lists. */
class ReaderWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        WidgetIntents.update(context, manager, ids, R.layout.widget_reader) { v ->
            v.setOnClickPendingIntent(R.id.wSearch, WidgetIntents.open(context, 20, WidgetIntents.search(context)))
            v.setOnClickPendingIntent(R.id.wEditPill, WidgetIntents.open(context, 21, WidgetIntents.editPdf(context)))
            v.setOnClickPendingIntent(R.id.wPdf, WidgetIntents.open(context, 22, WidgetIntents.list(context, Category.PDF)))
            v.setOnClickPendingIntent(R.id.wWord, WidgetIntents.open(context, 23, WidgetIntents.list(context, Category.WORD)))
            v.setOnClickPendingIntent(R.id.wExcel, WidgetIntents.open(context, 24, WidgetIntents.list(context, Category.EXCEL)))
            v.setOnClickPendingIntent(R.id.wPpt, WidgetIntents.open(context, 25, WidgetIntents.list(context, Category.PPT)))
        }
}

/** "PDF Editor" (2×2 picture tile). */
class EditPdfWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        WidgetIntents.update(context, manager, ids, R.layout.widget_edit_big) { v ->
            v.setOnClickPendingIntent(R.id.wRoot, WidgetIntents.open(context, 30, WidgetIntents.editPdf(context)))
        }
}

/** "PDF Editor" (1×1 icon). */
class EditPdfSmallWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        WidgetIntents.update(context, manager, ids, R.layout.widget_edit_small) { v ->
            v.setOnClickPendingIntent(R.id.wRoot, WidgetIntents.open(context, 31, WidgetIntents.editPdf(context)))
        }
}
