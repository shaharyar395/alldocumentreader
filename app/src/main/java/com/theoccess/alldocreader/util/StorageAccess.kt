package com.theoccess.alldocreader.util

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/** Helpers for the "All files access" (Android 11+) / READ_EXTERNAL_STORAGE permission. */
object StorageAccess {

    fun has(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }

    /** True when access is granted through a system settings page instead of a runtime dialog. */
    val usesSettingsPage: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    val runtimePermissions: Array<String>
        get() = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    /** Opens the "All files access" settings page for this app (Android 11+). */
    fun openSettingsPage(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:${context.packageName}"))
            )
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (ignored: ActivityNotFoundException) {
            }
        }
    }
}
