package com.theoccess.alldocreader.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import com.theoccess.alldocreader.R

/** Opening things outside the app: web pages, Google Play, the system file manager, e-mail. */
object Links {
    /** Developer name on Google Play, used by "Explore more apps". Change it to yours. */
    const val DEVELOPER = "The Occess"
    const val SUPPORT_EMAIL = "app@theoccess.com"
    const val PRIVACY_POLICY = "https://sites.google.com/view/mob-apps-inc/privacy-policy"

    fun open(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        context.toast(R.string.no_app_to_open)
        false
    }

    /** Play Store page of this app (Play app first, then the website). */
    fun playStore(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")))
        } catch (e: ActivityNotFoundException) {
            open(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
        }
    }

    fun developerPage(context: Context) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=pub:${Uri.encode(DEVELOPER)}")))
        } catch (e: ActivityNotFoundException) {
            open(context, "https://play.google.com/store/apps/developer?id=${Uri.encode(DEVELOPER)}")
        }
    }

    fun manageSubscriptions(context: Context) =
        open(context, "https://play.google.com/store/account/subscriptions?sku=${com.theoccess.alldocreader.premium.Billing.PRODUCT_ID}&package=${context.packageName}")

    /** Android's own Files app (the "File manager" row). */
    fun fileManager(context: Context) {
        val tries = ArrayList<Intent>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tries += Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_FILES)
        }
        tries += Intent(Intent.ACTION_VIEW).setDataAndType(
            DocumentsContract.buildRootUri("com.android.externalstorage.documents", "primary"),
            "vnd.android.document/root"
        )
        tries += Intent(Intent.ACTION_VIEW).setDataAndType(
            Uri.parse("content://com.android.externalstorage.documents/root/primary"), DocumentsContract.Document.MIME_TYPE_DIR
        )
        for (i in tries) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (ignored: Exception) {
            }
        }
        context.toast(R.string.no_app_to_open)
    }

    fun shareApp(context: Context) {
        val text = context.getString(R.string.share_app_text, context.getString(R.string.app_name)) +
            "\nhttps://play.google.com/store/apps/details?id=${context.packageName}"
        context.startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                context.getString(R.string.share)
            )
        )
    }
}
