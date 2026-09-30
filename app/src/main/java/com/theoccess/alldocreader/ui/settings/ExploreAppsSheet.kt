package com.theoccess.alldocreader.ui.settings

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ItemExploreAppBinding
import com.theoccess.alldocreader.databinding.SheetExploreAppsBinding
import com.theoccess.alldocreader.util.Links

/**
 * "Explore more apps": thank-you header and your other apps, each with Open (Google Play).
 * Add your apps to [APPS]; "See all our apps" opens your developer page.
 */
object ExploreAppsSheet {

    /** name, short description, Play package name, icon. Fill in your own apps. */
    data class App(val name: String, val description: String, val packageName: String, val icon: Int)

    val APPS: List<App> = emptyList()

    fun show(context: Context) {
        val dialog = BottomSheetDialog(context, R.style.Theme_DocReader_BottomSheet)
        val b = SheetExploreAppsBinding.inflate(LayoutInflater.from(context))
        APPS.forEach { app ->
            val r = ItemExploreAppBinding.inflate(LayoutInflater.from(context), b.apps, false)
            r.ivIcon.setImageResource(app.icon)
            r.tvName.text = app.name
            r.tvDesc.text = app.description
            val open = { Links.open(context, "https://play.google.com/store/apps/details?id=${app.packageName}") }
            r.btnOpen.setOnClickListener { open() }
            r.root.setOnClickListener { open() }
            b.apps.addView(r.root)
        }
        b.btnMore.setOnClickListener { Links.developerPage(context) }
        b.btnClose.setOnClickListener { dialog.dismiss() }
        dialog.setContentView(b.root)
        dialog.show()
    }
}
