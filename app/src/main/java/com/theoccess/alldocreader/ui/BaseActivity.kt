package com.theoccess.alldocreader.ui

import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.theoccess.alldocreader.App

/**
 * Every screen of the app extends this. It notes when a screen opens something (another screen,
 * the Files app, share sheet, Google sign-in, settings …), so coming back from there does not
 * count as "reopening the app" — only leaving with Home / Recents / another app does, which
 * shows "Welcome back…" and the App Open ad (see [App.WelcomeBackTracker]).
 */
open class BaseActivity : AppCompatActivity() {

    /** startActivity, activity-result launchers and fragments all end up here. */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        App.WelcomeBackTracker.launchedSomething = true
        super.startActivityForResult(intent, requestCode, options)
    }

    /** Google sign-in (Save to Google Drive) and similar go through here. */
    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun startIntentSenderForResult(
        intent: IntentSender, requestCode: Int, fillInIntent: Intent?,
        flagsMask: Int, flagsValues: Int, extraFlags: Int, options: Bundle?
    ) {
        App.WelcomeBackTracker.launchedSomething = true
        super.startIntentSenderForResult(intent, requestCode, fillInIntent, flagsMask, flagsValues, extraFlags, options)
    }
}
