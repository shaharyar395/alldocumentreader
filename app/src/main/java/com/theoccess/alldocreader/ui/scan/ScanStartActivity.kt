package com.theoccess.alldocreader.ui.scan

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.theoccess.alldocreader.databinding.ActivityScanStartBinding

/**
 * Start of Image to PDF / Scan to PDF: Image to PDF first asks
 * "Take a photo / Choose from gallery / Cancel"; Scan to PDF opens the camera directly.
 * The chosen pictures go through "Choose cropping method" into the page editor.
 */
class ScanStartActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanStartBinding

    private val flow = AddPagesFlow(this) { pages ->
        ScanSession.pages.addAll(pages)
        startActivity(ScanEditActivity.intent(this, 0))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanStartBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnBack.setOnClickListener { finish() }
        flow.onCancelled = { finish() }
        if (savedInstanceState == null) {
            ScanSession.clear(this)
            val scan = intent.getBooleanExtra(EXTRA_SCAN, false)
            ScanSession.scanMode = scan
            if (scan) flow.openCamera() else flow.chooseSource()
        }
    }

    companion object {
        private const val EXTRA_SCAN = "scan"

        fun intent(context: Context, scan: Boolean) =
            Intent(context, ScanStartActivity::class.java).putExtra(EXTRA_SCAN, scan)
    }
}
