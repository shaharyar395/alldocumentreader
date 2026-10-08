package com.theoccess.alldocreader.ui.create

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.databinding.ActivityConvertBinding
import com.theoccess.alldocreader.ui.scan.ConvertResultActivity
import com.theoccess.alldocreader.ui.scan.ProgressPill
import com.theoccess.alldocreader.ui.viewer.Converters
import com.theoccess.alldocreader.ui.viewer.DocLayout
import com.theoccess.alldocreader.ui.viewer.DocPageSource
import com.theoccess.alldocreader.ui.viewer.DocxParser
import com.theoccess.alldocreader.ui.viewer.PageAdapter
import com.theoccess.alldocreader.ui.viewer.PageSource
import com.theoccess.alldocreader.ui.viewer.PdfPageSource
import com.theoccess.alldocreader.ui.viewer.PptxPageSource
import com.theoccess.alldocreader.ui.viewer.PptxParser
import com.theoccess.alldocreader.ui.viewer.TxtParser
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Preview of the chosen file (title = file name, pages on grey) with "Convert to PDF" /
 * "Convert to Word" at the bottom → "Converting… (x%)" → Converted successfully screen.
 */
class ConvertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConvertBinding
    private lateinit var kind: ConvertKind
    private lateinit var source: File
    private var pages: PageSource? = null
    private var converting = false
    private val renderExecutor = Executors.newSingleThreadExecutor()
    private val renderDispatcher = renderExecutor.asCoroutineDispatcher()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConvertBinding.inflate(layoutInflater)
        setContentView(binding.root)
        kind = ConvertKind.entries.getOrElse(intent.getIntExtra(EXTRA_KIND, 0)) { ConvertKind.WORD_TO_PDF }
        source = File(intent.getStringExtra(EXTRA_PATH) ?: run { finish(); return })
        if (!source.exists()) { toast(R.string.file_not_found); finish(); return }
        LibraryStore.addRecent(source.absolutePath)

        binding.tvTitle.text = source.name
        binding.btnConvert.setText(if (kind.targetExt == "docx") R.string.convert_to_word else R.string.convert_to_pdf)
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (!converting) finish() }
        })
        binding.btnConvert.setOnClickListener { convert() }
        binding.rvPages.layoutManager = LinearLayoutManager(this)
        load()
    }

    private fun load() {
        lifecycleScope.launch {
            val src = withContext(Dispatchers.IO) {
                try {
                    val ext = source.extension.lowercase()
                    when {
                        kind == ConvertKind.PDF_TO_WORD -> PdfPageSource(source)
                        kind == ConvertKind.PPT_TO_PDF -> PptxPageSource(PptxParser.parse(source))
                        ext == "txt" -> DocPageSource(DocLayout(TxtParser.parse(source)))
                        else -> DocPageSource(DocLayout(DocxParser.parse(source)))
                    }
                } catch (e: Throwable) {
                    null
                }
            }
            if (isFinishing || isDestroyed) { src?.close(); return@launch }
            binding.pbLoading.visibility = View.GONE
            if (src == null) {
                toast(if (kind == ConvertKind.PDF_TO_WORD) R.string.pdf_password_protected else R.string.convert_failed)
                finish()
                return@launch
            }
            pages = src
            binding.rvPages.adapter = PageAdapter(src, lifecycleScope, renderDispatcher)
            binding.btnConvert.isEnabled = true
        }
    }

    private fun convert() {
        val src = pages ?: return
        if (converting) return
        converting = true
        binding.btnConvert.isEnabled = false
        val pill = ProgressPill(this)
        pill.show(getString(R.string.converting_progress, 0))
        val base = source.nameWithoutExtension
        lifecycleScope.launch {
            val out = try {
                withContext(Dispatchers.IO) {
                    val progress: (Int) -> Unit = { p ->
                        runOnUiThread { pill.show(getString(R.string.converting_progress, p.coerceIn(0, 100))) }
                    }
                    when {
                        src is DocPageSource -> Converters.docToPdf(applicationContext, src.layout, base, progress)
                        src is PptxPageSource -> Converters.deckToPdf(applicationContext, src.deck, base, progress)
                        else -> Converters.pdfToWord(applicationContext, source, base, progress)
                    }
                }
            } catch (e: Throwable) {
                null
            }
            pill.dismiss()
            converting = false
            if (out == null) {
                binding.btnConvert.isEnabled = true
                toast(R.string.convert_failed)
                return@launch
            }
            com.theoccess.alldocreader.data.SavedFiles.onSaved(applicationContext, out)
            startActivity(ConvertResultActivity.intent(this@ConvertActivity, out))
            setResult(Activity.RESULT_OK)
            finish()
        }
    }

    override fun onDestroy() {
        pages?.close()
        renderDispatcher.close()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PATH = "path"

        fun intent(context: Context, kind: ConvertKind, path: String) =
            Intent(context, ConvertActivity::class.java)
                .putExtra(EXTRA_KIND, kind.ordinal)
                .putExtra(EXTRA_PATH, path)
    }
}
