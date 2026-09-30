package com.theoccess.alldocreader.ui.files

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.toast
import java.io.File

/** Target of home-screen shortcuts: opens the document and closes itself. */
class OpenFileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH)
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            toast(R.string.file_not_found)
        } else {
            FileActions.open(this, DocFile.from(file))
        }
        finish()
    }

    companion object {
        const val EXTRA_PATH = "path"
    }
}
