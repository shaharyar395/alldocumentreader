package com.theoccess.alldocreader.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.theoccess.alldocreader.ui.viewer.Converters
import java.io.File

/**
 * Every file the app writes (edited PDF, extracted pages, created / converted / imported file)
 * goes through here, so it shows up at the top of Recent right away and the home screen
 * reloads its counts ("Loading files…" → "Loaded successfully") when the user comes back.
 */
object SavedFiles {

    /** Set when something was saved; the home screen clears it after reloading. */
    @Volatile
    var pendingReload = false

    private val main = Handler(Looper.getMainLooper())

    fun onSaved(context: Context, file: File) {
        Converters.scan(context.applicationContext, file)
        pendingReload = true
        Prefs.savesSinceHelpful = Prefs.savesSinceHelpful + 1   // "Is it helpful?" shows sometimes after saving
        val update = {
            LibraryStore.addRecent(file.absolutePath)
            if (file.exists()) FileRepository.replace(file.absolutePath, DocFile.from(file))
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update() else main.post(update)
    }
}
