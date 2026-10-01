package com.theoccess.alldocreader.ui.scan

import android.app.Activity
import android.net.Uri
import android.util.Log
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Taking photos of documents with Google's **ML Kit Document Scanner** (the scanner used by
 * Google Drive): live page-edge detection, auto-capture, automatic crop + straightening, shadow /
 * stain clean-up and filters, all on the phone. Its pages come back as JPEGs, already cropped,
 * and are copied into the scan job folder.
 *
 * Needs Google Play services and ≥ 1.7 GB RAM; when the scanner can't start, [onUnavailable] is
 * called so the app can use its own camera instead. Create it as a field of the activity (the
 * result launcher must be registered before the activity starts).
 */
class DocScanner(
    private val activity: AppCompatActivity,
    private val onPages: (List<File>) -> Unit,
    private val onCancelled: () -> Unit,
    private val onUnavailable: () -> Unit
) {

    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val result = if (r.resultCode == Activity.RESULT_OK) GmsDocumentScanningResult.fromActivityResultIntent(r.data) else null
        val uris = result?.pages?.map { it.imageUri }.orEmpty()
        if (uris.isEmpty()) onCancelled() else copy(uris)
    }

    /** Opens the scanner; [maxPages] = 1 for "Retake". */
    fun start(maxPages: Int = MAX_PAGES) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)   // the app has its own "Choose from gallery"
            .setPageLimit(maxPages)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        try {
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { e ->
                    Log.w(TAG, "document scanner unavailable: ${e.message}")
                    onUnavailable()
                }
        } catch (e: Exception) {
            Log.w(TAG, "document scanner failed: ${e.message}")
            onUnavailable()
        }
    }

    private fun copy(uris: List<Uri>) {
        activity.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val f = ScanSession.newFile(activity, "scan")
                    try {
                        activity.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { input.copyTo(it) } }
                        if (f.length() > 0) f else null
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            if (files.isEmpty()) onCancelled() else onPages(files)
        }
    }

    companion object {
        private const val TAG = "DocScanner"
        const val MAX_PAGES = 50
    }
}
