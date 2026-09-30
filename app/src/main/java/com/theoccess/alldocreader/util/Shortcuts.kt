package com.theoccess.alldocreader.util

import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.ui.files.OpenFileActivity

/** "To home screen": pins a launcher shortcut that opens the document. */
object Shortcuts {

    /** MIUI/HyperOS block pinned shortcuts until "Home screen shortcuts" is allowed in app permissions. */
    val needsVendorPermission: Boolean
        get() = Build.MANUFACTURER.lowercase() in setOf("xiaomi", "redmi", "poco")

    fun isSupported(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun pin(context: Context, doc: DocFile): Boolean {
        if (!isSupported(context)) return false
        val intent = Intent(context, OpenFileActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(OpenFileActivity.EXTRA_PATH, doc.path)
        val label = doc.name.ifEmpty { "Document" }
        val info = ShortcutInfoCompat.Builder(context, "file_" + doc.path.hashCode())
            .setShortLabel(label.take(24))
            .setLongLabel(label)
            .setIcon(iconFor(context, doc))
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, info, null)
    }

    /** Launchers handle bitmap icons more reliably than vector resources. */
    private fun iconFor(context: Context, doc: DocFile): IconCompat {
        val d = ContextCompat.getDrawable(context, doc.type.icon)
            ?: return IconCompat.createWithResource(context, R.mipmap.ic_launcher)
        val size = (48 * context.resources.displayMetrics.density).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        val pad = size / 5
        d.setBounds(pad, pad, size - pad, size - pad)
        d.draw(canvas)
        return IconCompat.createWithBitmap(bmp)
    }

    /** Opens the app permission screen (MIUI permission editor when available). */
    fun openPermissionSettings(context: Context) {
        val miui = Intent("miui.intent.action.APP_PERM_EDITOR")
            .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
            .putExtra("extra_pkgname", context.packageName)
        try {
            context.startActivity(miui)
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            } catch (ignored: ActivityNotFoundException) {
            }
        }
    }
}
