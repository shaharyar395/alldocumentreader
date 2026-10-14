package com.theoccess.alldocreader.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.FileType
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.ActivityViewerBinding
import com.theoccess.alldocreader.databinding.ItemBottomToolBinding
import com.theoccess.alldocreader.databinding.ItemFileActionBinding
import com.theoccess.alldocreader.databinding.SheetViewerMenuBinding
import com.theoccess.alldocreader.ui.files.FileSheets
import com.theoccess.alldocreader.ui.pages.PageOrganizerActivity
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.PdfPrint
import com.theoccess.alldocreader.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * In-app reader for PDF, Word (.docx) and TXT files:
 * page chip + jump, vertical/horizontal, rotate, search, invert, share, ⋮ menu and conversions.
 */
class ViewerActivity : com.theoccess.alldocreader.ui.BaseActivity() {

    private lateinit var binding: ActivityViewerBinding
    private lateinit var doc: DocFile
    private var source: PageSource? = null
    private var adapter: PageAdapter? = null
    private val renderExecutor = Executors.newSingleThreadExecutor()
    private val renderDispatcher = renderExecutor.asCoroutineDispatcher()
    /**
     * Search has its own thread: on the page thread a search through a long document (e.g. 1000
     * pages) kept every page from being drawn until it had finished — blank pages with spinners,
     * freezes and white / black screens after "Go to page".
     */
    private val searchDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    /** "Go to page" was used while a search was still running: its result then starts from there. */
    private var jumpedDuringSearch = false
    private val snapHelper = PagerSnapHelper()
    private var horizontal = false
    private var rotation = 0
    private var inverted = false
    private var convertedFile: File? = null
    private var pendingPage: Int? = null
    private var barsHidden = false
    private var swipeTutorial: View? = null

    // search
    private var searching = false
    private var searchJob: Job? = null
    /** One search hit: its page and its boxes (empty when the position on the page is unknown). */
    private class Hit(val page: Int, val boxes: List<android.graphics.RectF>)
    private var hits: List<Hit> = emptyList()
    private var hitIndex = 0

    private val isPdf get() = doc.type == FileType.PDF

    private val organizer = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) reload()
    }

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = closeSearch()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra(EXTRA_PATH)
        val file = path?.let { File(it) }
        if (file == null || !file.exists()) {
            toast(R.string.file_not_found)
            finish()
            return
        }
        doc = DocFile.from(file)
        if (savedInstanceState == null) com.theoccess.alldocreader.data.LibraryStore.addRecent(file.absolutePath)
        binding.tvTitle.text = doc.name
        onBackPressedDispatcher.addCallback(this, backCallback)

        setupToolbar()
        setupBottomBar()
        setupSearch()
        binding.pageScroll.attach(binding.rvPages)
        binding.rvPages.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) = updateChip()
        })
        binding.tvPageChip.setOnClickListener { askJump() }
        // one tap on the document hides the top and bottom bars (only the pages stay); tap again to bring them back
        binding.zoom.onSingleTap = { if (!searching && source != null) setBarsHidden(!barsHidden) }
        binding.btnTipOk.setOnClickListener {
            Prefs.raw.edit().putBoolean(KEY_TIP, true).apply()
            binding.jumpTip.visibility = View.GONE
        }
        binding.fabEdit.setOnClickListener {
            if (isPdf && com.theoccess.alldocreader.premium.PremiumGate.use(this, com.theoccess.alldocreader.premium.PremiumGate.Feature.EDIT_SIGN))
                organizer.launch(com.theoccess.alldocreader.ui.edit.PdfEditActivity.intent(this, doc.file, currentPage()))
        }
        binding.btnBannerClose.setOnClickListener { binding.banner.visibility = View.GONE }
        binding.btnBannerOpen.setOnClickListener {
            convertedFile?.let { f -> FileActions.open(this, DocFile.from(f)) }
            binding.banner.visibility = View.GONE
        }
        load()
    }

    // ------------------------------------------------------------------ loading

    private fun load() {
        binding.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    when (doc.type) {
                        FileType.PDF -> PdfPageSource(doc.file)
                        // documents: only the first pages are laid out before showing; the rest
                        // follow in the background (see show → finishLoading)
                        FileType.TXT -> progressive(ParsedDoc(TxtParser.parse(doc.file), PageGeometry.DEFAULT))
                        FileType.PPT -> PptxPageSource(PptxParser.parse(doc.file))
                        FileType.EXCEL -> progressive(SheetParser.parse(doc.file))
                        else -> progressive(DocxParser.parse(doc.file))
                    }
                } catch (e: SecurityException) {
                    ERROR_PASSWORD
                } catch (e: Throwable) {
                    ERROR_OTHER
                }
            }
            if (isFinishing || isDestroyed) {
                (result as? PageSource)?.close()
                return@launch
            }
            when (result) {
                is PageSource -> show(result)
                ERROR_PASSWORD -> { toast(R.string.pdf_password_protected); finish() }
                else -> { FileSheets.showUnsupported(this@ViewerActivity); binding.loading.visibility = View.GONE }
            }
        }
    }

    private fun progressive(parsed: ParsedDoc): PageSource =
        DocPageSource(DocLayout(parsed, eager = false).apply { layoutUntil(FIRST_PAGES) })

    private fun show(src: PageSource) {
        source?.close()
        source = src
        // the rest of a long document is prepared while the first pages are already on screen
        if (!src.isComplete || src is PdfPageSource) {
            lifecycleScope.launch(Dispatchers.Default) {
                try {
                    src.finishLoading(object : PageSource.LoadListener {
                        override fun onPagesAdded(count: Int) = runOnUiThread {
                            if (source === src) { adapter?.setPageCount(count); updateChip() }
                        }
                        override fun onPagesResized(pages: List<Int>) = runOnUiThread {
                            if (source === src) adapter?.pagesResized(pages)
                        }
                    })
                } catch (e: Throwable) {
                    // closed while loading
                }
                withContext(Dispatchers.Main) { if (source === src) { adapter?.setPageCount(src.pageCount); updateChip() } }
            }
        }
        val a = PageAdapter(src, lifecycleScope, renderDispatcher).apply {
            horizontal = this@ViewerActivity.horizontal
            rotation = this@ViewerActivity.rotation
            inverted = this@ViewerActivity.inverted
        }
        adapter = a
        applyOrientation()
        binding.rvPages.adapter = a
        binding.loading.visibility = View.GONE
        binding.tvPageChip.visibility = View.VISIBLE
        binding.fabEdit.visibility = if (isPdf) View.VISIBLE else View.GONE
        updateScrollGap()
        binding.rvPages.post { updateChip() }
        if (!Prefs.raw.getBoolean(KEY_TIP, false) && src.pageCount > 1) binding.jumpTip.visibility = View.VISIBLE
        // Word files: start reading the words in the page pictures now (low priority), so a search
        // later finds them at once instead of reading 1000+ pictures first
        if (src is DocPageSource && searchPrepared !== src) {
            searchPrepared = src
            Thread {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                try { src.prepareSearch() } catch (e: Throwable) {}
            }.start()
        }
    }

    /** After the page organizer saved changes. */
    private fun reload() {
        doc = DocFile.from(doc.file)
        adapter?.clear()
        load()
    }

    // ------------------------------------------------------------------ toolbar

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnMore.setOnClickListener { showMenu() }
        if (isPdf) {
            binding.btnConvert.setImageResource(R.drawable.ic_convert_word)
            binding.btnConvert.contentDescription = getString(R.string.pdf_to_word)
            binding.btnPages.visibility = View.VISIBLE
            binding.btnPages.setOnClickListener { openOrganizer() }
        }
        binding.btnConvert.setOnClickListener { convert() }
    }

    private fun tool(t: ItemBottomToolBinding, icon: Int, label: Int, onClick: () -> Unit) {
        t.ivIcon.setImageResource(icon)
        t.tvLabel.setText(label)
        t.root.setOnClickListener { onClick() }
    }

    private fun setupBottomBar() {
        tool(binding.tabMode, R.drawable.ic_vertical, R.string.mode_vertical) {
            horizontal = !horizontal
            showPill(
                if (horizontal) R.drawable.ic_horizontal else R.drawable.ic_vertical,
                getString(if (horizontal) R.string.page_by_page else R.string.continuous_pages)
            )
            binding.tabMode.ivIcon.setImageResource(if (horizontal) R.drawable.ic_horizontal else R.drawable.ic_vertical)
            binding.tabMode.tvLabel.setText(if (horizontal) R.string.mode_horizontal else R.string.mode_vertical)
            val page = currentPage()
            adapter?.horizontal = horizontal
            applyOrientation()
            binding.rvPages.scrollToPosition(page)
            if (horizontal) showSwipeTutorial()
        }
        // Rotate turns the screen between portrait and landscape, like the original app
        tool(binding.tabRotate, R.drawable.ic_rotate, R.string.rotate) {
            val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            pendingPage = currentPage()
            requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            binding.tabRotate.root.isSelected = !landscape
        }
        tool(binding.tabSearch, R.drawable.ic_search_text, R.string.search) { openSearch() }
        tool(binding.tabInvert, R.drawable.ic_invert, R.string.invert) {
            inverted = !inverted
            binding.tabInvert.root.isSelected = inverted
            binding.content.setBackgroundColor(if (inverted) 0xFF1B1E24.toInt() else 0xFFEEF1F5.toInt())
            adapter?.inverted = inverted
            showPill(R.drawable.ic_invert, getString(if (inverted) R.string.color_inversion_on else R.string.color_inversion_off))
        }
        tool(binding.tabShare, R.drawable.ic_share, R.string.share) { FileActions.share(this, doc) }
    }

    /** Dark pill under the toolbar that fades out after a moment. */
    private fun showPill(icon: Int, text: String) {
        val pill = binding.tvTopPill
        val d = ContextCompat.getDrawable(this, icon)?.mutate()?.apply {
            val size = (18 * resources.displayMetrics.density).toInt()
            setBounds(0, 0, size, size)
            setTint(Color.WHITE)
        }
        pill.setCompoundDrawablesRelative(d, null, null, null)
        pill.text = text
        pill.removeCallbacks(hidePill)
        pill.animate().cancel()
        pill.alpha = 1f
        pill.visibility = View.VISIBLE
        pill.postDelayed(hidePill, 1600)
    }

    private val hidePill = Runnable {
        binding.tvTopPill.animate().alpha(0f).setDuration(250).withEndAction {
            binding.tvTopPill.visibility = View.GONE
        }.start()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val page = pendingPage ?: currentPage()
        pendingPage = null
        // page sizes depend on the list width: re-measure after the new layout pass
        binding.rvPages.post {
            adapter?.clear()
            adapter?.notifyDataSetChanged()
            (binding.rvPages.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(page, 0)
            binding.rvPages.post { updateChip() }
        }
    }

    private fun applyOrientation() {
        binding.zoom.reset()
        binding.rvPages.layoutManager = LinearLayoutManager(
            this, if (horizontal) RecyclerView.HORIZONTAL else RecyclerView.VERTICAL, false
        )
        snapHelper.attachToRecyclerView(null)
        if (horizontal) snapHelper.attachToRecyclerView(binding.rvPages)
        binding.pageScroll.visibility = if (horizontal) View.GONE else View.VISIBLE
    }

    /**
     * The page slider's lowest position: just above the Edit button when it is shown (so the
     * last page can be reached without the slider covering the button), else near the bottom.
     */
    private fun updateScrollGap() {
        val d = resources.displayMetrics.density
        val fabShown = binding.fabEdit.visibility == View.VISIBLE
        binding.pageScroll.setBottomGap(((if (fabShown) 16 + 52 + 8 else 6) * d).toInt())
    }

    // ------------------------------------------------------------------ full-screen reading / swipe tutorial

    /**
     * Like the original: one tap on the document hides the toolbar, the bottom tools, the page
     * number and the edit button (and the status bar), so only the pages are visible; the next tap
     * brings them back. The ad under the tools stays.
     */
    private fun setBarsHidden(hide: Boolean) {
        barsHidden = hide
        val v = if (hide) View.GONE else View.VISIBLE
        binding.topBar.visibility = v
        binding.bottomBar.visibility = v
        binding.tvPageChip.visibility = v
        binding.fabEdit.visibility = if (!hide && isPdf) View.VISIBLE else View.GONE
        updateScrollGap()
        if (hide) binding.jumpTip.visibility = View.GONE
        val insets = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (hide) {
            insets.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        } else {
            insets.show(androidx.core.view.WindowInsetsCompat.Type.statusBars())
        }
    }

    /** First switch to Horizontal: "Swipe left/right to turn pages" with a hand moving left and right. */
    private fun showSwipeTutorial() {
        if (Prefs.raw.getBoolean(KEY_SWIPE_TIP, false) || swipeTutorial != null) return
        Prefs.raw.edit().putBoolean(KEY_SWIPE_TIP, true).apply()
        val root = findViewById<android.view.ViewGroup>(android.R.id.content)
        val tip = LayoutInflater.from(this).inflate(R.layout.view_swipe_tutorial, root, false)
        val hand = tip.findViewById<View>(R.id.ivHand)
        val travel = 34 * resources.displayMetrics.density
        val anim = android.animation.ObjectAnimator.ofFloat(hand, View.TRANSLATION_X, -travel, travel).apply {
            duration = 900
            repeatMode = android.animation.ValueAnimator.REVERSE
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            start()
        }
        tip.findViewById<View>(R.id.btnSwipeTipOk).setOnClickListener {
            anim.cancel()
            tip.animate().alpha(0f).setDuration(180).withEndAction { root.removeView(tip) }.start()
            swipeTutorial = null
        }
        tip.alpha = 0f
        root.addView(tip)
        tip.animate().alpha(1f).setDuration(200).start()
        swipeTutorial = tip
    }

    // ------------------------------------------------------------------ page chip / jump

    private fun currentPage(): Int {
        val lm = binding.rvPages.layoutManager as? LinearLayoutManager ?: return 0
        val first = lm.findFirstVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION) return 0
        val v = lm.findViewByPosition(first) ?: return first
        return if (!horizontal && v.bottom < binding.rvPages.height / 3) first + 1 else first
    }

    private fun updateChip() {
        if (source == null) return
        val count = adapter?.itemCount ?: return
        val page = (currentPage() + 1).coerceIn(1, count)
        binding.tvPageChip.text = getString(R.string.page_chip, page, count)
    }

    private fun askJump() {
        if (source == null) return
        val count = adapter?.itemCount ?: return
        binding.jumpTip.visibility = View.GONE
        Prefs.raw.edit().putBoolean(KEY_TIP, true).apply()
        FileSheets.askNumber(this, getString(R.string.go_to_page), currentPage() + 1, 1, count) { page ->
            if (searching) {
                // the search field takes the focus back after the dialog: keep the keyboard away
                // so the pages don't shrink and redraw
                hideKeyboard()
                binding.etSearch.clearFocus()
                if (searchJob?.isActive == true) jumpedDuringSearch = true
            }
            (binding.rvPages.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(page - 1, 0)
            binding.rvPages.post { updateChip() }
        }
    }

    // ------------------------------------------------------------------ search

    private fun setupSearch() {
        // the hint names the kind of file being read
        binding.etSearch.setHint(
            when (doc.type) {
                FileType.PDF -> R.string.search_in_pdf
                FileType.PPT -> R.string.search_in_ppt
                FileType.EXCEL -> R.string.search_in_excel
                FileType.TXT -> R.string.search_in_txt
                else -> R.string.search_in_document
            }
        )
        binding.btnCloseSearch.setOnClickListener { closeSearch() }
        binding.etSearch.doAfterTextChanged { scheduleSearch() }
        binding.etSearch.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEARCH) { hideKeyboard(); runSearch(); true } else false
        }
        binding.btnNext.setOnClickListener { stepHit(1) }
        binding.btnPrev.setOnClickListener { stepHit(-1) }
    }

    private var searchPrepared: PageSource? = null

    private fun openSearch() {
        val src = source ?: return
        // read text inside pictures (picture-only Word pages, scanned PDFs) while the user types
        if (searchPrepared !== src) {
            searchPrepared = src
            lifecycleScope.launch(Dispatchers.IO) { try { src.prepareSearch() } catch (e: Throwable) {} }
        }
        searching = true
        backCallback.isEnabled = true
        binding.normalBar.visibility = View.GONE
        binding.searchBar.visibility = View.VISIBLE
        binding.etSearch.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun closeSearch() {
        searching = false
        backCallback.isEnabled = false
        searchJob?.cancel()
        hideKeyboard()
        binding.etSearch.setText("")
        binding.tvSearchCount.text = ""
        hits = emptyList()
        adapter?.clearHighlights()
        binding.searchBar.visibility = View.GONE
        binding.normalBar.visibility = View.VISIBLE
    }

    private fun hideKeyboard() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        jumpedDuringSearch = false
        searchJob = lifecycleScope.launch {
            delay(450)
            runSearchNow()
        }
    }

    private fun runSearch() {
        searchJob?.cancel()
        jumpedDuringSearch = false
        searchJob = lifecycleScope.launch { runSearchNow() }
    }

    /**
     * Finds every occurrence (PDF, Word, TXT, Excel, PowerPoint): all of them are highlighted in
     * yellow, the current one in orange, and the page scrolls so that it is in view. The arrows
     * go to the previous / next occurrence.
     */
    private suspend fun runSearchNow() {
        val src = source ?: return
        val q = binding.etSearch.text.toString().trim()
        if (q.isEmpty()) {
            hits = emptyList()
            binding.tvSearchCount.text = ""
            adapter?.clearHighlights()
            return
        }
        binding.tvSearchCount.text = "…"
        // Results appear while the search goes on: the first match is shown at once and the count
        // grows ("3/12…"). Pages of a long document that are still being laid out are searched
        // as soon as they are ready (before, nothing showed until every page was laid out and read).
        var shownFirst = false
        val found = withContext(searchDispatcher) {
            val list = ArrayList<Hit>()
            var i = 0
            while (true) {
                // stop at once when a newer search (next letter typed) or closing replaces this one
                kotlinx.coroutines.yield()
                if (source !== src) break
                if (i >= src.pageCount) {
                    if (src.isComplete) break
                    delay(60)
                    continue
                }
                val before = list.size
                val boxes = try { src.findMatches(i, q) } catch (e: Exception) { emptyList() }
                if (boxes.isNotEmpty()) {
                    boxes.forEach { list += Hit(i, it) }
                } else {
                    // no positions known for this page: still count the matches in its text
                    val n = try { SearchText.find(src.pageText(i), q).size } catch (e: Exception) { 0 }
                    repeat(n) { list += Hit(i, emptyList()) }
                }
                i++
                if (list.size > before) {
                    val snapshot = ArrayList(list)
                    withContext(Dispatchers.Main) {
                        // the list may not know the newest laid-out pages yet
                        if ((adapter?.itemCount ?: 0) < src.pageCount) adapter?.setPageCount(src.pageCount)
                        hits = snapshot
                        if (!shownFirst) {
                            shownFirst = true
                            val from = if (jumpedDuringSearch) currentPage() else 0
                            hitIndex = snapshot.indexOfFirst { it.page >= from }.let { if (it < 0) 0 else it }
                            if (hitIndex == 0 && from > 0 && snapshot.none { it.page >= from }) {
                                // keep looking for a match after the page the user went to
                                shownFirst = false
                            } else {
                                showHit()
                            }
                        } else {
                            // keep the current match, refresh the highlights and the count
                            val byPage = snapshot.groupBy { it.page }.mapValues { e -> e.value.flatMap { it.boxes } }
                            val cur = snapshot[hitIndex.coerceIn(0, snapshot.size - 1)]
                            adapter?.setHighlights(byPage, cur.page, cur.boxes)
                        }
                        if (shownFirst) binding.tvSearchCount.text = "${hitIndex + 1}/${snapshot.size}…"
                    }
                }
            }
            list
        }
        adapter?.setPageCount(src.pageCount)
        hits = found
        // the user went to another page while searching: show the first match from that page on,
        // instead of pulling them back to the start of the document
        if (!shownFirst) {
            val from = if (jumpedDuringSearch) currentPage() else 0
            hitIndex = found.indexOfFirst { it.page >= from }.coerceAtLeast(0)
        }
        jumpedDuringSearch = false
        if (found.isEmpty()) {
            binding.tvSearchCount.text = "0/0"
            adapter?.clearHighlights()
            toast(R.string.search_no_results)
        } else if (!shownFirst) {
            showHit()
        } else {
            // the first match is already on screen: only the final count
            hitIndex = hitIndex.coerceIn(0, found.size - 1)
            binding.tvSearchCount.text = "${hitIndex + 1}/${found.size}"
        }
    }

    private fun stepHit(delta: Int) {
        if (hits.isEmpty()) return
        hideKeyboard()
        hitIndex = (hitIndex + delta + hits.size) % hits.size
        showHit()
    }

    private fun showHit() {
        val hit = hits[hitIndex]
        binding.tvSearchCount.text = "${hitIndex + 1}/${hits.size}"
        val byPage = hits.groupBy { it.page }.mapValues { e -> e.value.flatMap { it.boxes } }
        adapter?.setHighlights(byPage, hit.page, hit.boxes)
        val lm = binding.rvPages.layoutManager as? LinearLayoutManager ?: return
        val box = hit.boxes.firstOrNull()
        if (horizontal || box == null) {
            lm.scrollToPositionWithOffset(hit.page, 0)
        } else {
            // put the match about a third of the way down the screen
            val a = adapter ?: return
            val inPage = a.offsetInPage(hit.page, box.top, binding.rvPages.width, resources.displayMetrics.density)
            lm.scrollToPositionWithOffset(hit.page, binding.rvPages.height / 3 - inPage)
        }
        binding.rvPages.post { updateChip() }
    }

    // ------------------------------------------------------------------ ⋮ menu

    private fun showMenu() {
        val dialog = BottomSheetDialog(this, R.style.Theme_DocReader_BottomSheet)
        val b = SheetViewerMenuBinding.inflate(LayoutInflater.from(this))
        b.ivIcon.setImageResource(doc.type.icon)
        b.tvName.text = doc.name
        b.tvPath.text = doc.path
        b.header.setOnClickListener {
            dialog.dismiss()
            FileSheets.showInfo(this, doc)
        }

        val bookmarked = LibraryStore.isBookmarked(doc.path)
        val actions = mutableListOf<Triple<Int, String, () -> Unit>>(
            Triple(R.drawable.ic_rename, getString(R.string.rename)) {
                FileSheets.showRename(this, doc) { renamed ->
                    doc = renamed
                    binding.tvTitle.text = renamed.name
                }
            },
            Triple(
                if (bookmarked) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border,
                getString(if (bookmarked) R.string.bookmarked else R.string.bookmark)
            ) { FileActions.toggleBookmark(this, doc) },
            Triple(R.drawable.ic_home_add, getString(R.string.to_home_screen)) { FileSheets.addToHomeScreen(this, doc) },
            Triple(R.drawable.ic_st_drive, getString(R.string.save_to_drive_short)) {
                com.theoccess.alldocreader.ui.files.DriveSaveActivity.start(this, doc.file)
            }
        )
        if (isPdf) {
            actions += Triple(R.drawable.ic_pages, getString(R.string.pages)) { openOrganizer() }
            actions += Triple(R.drawable.ic_print, getString(R.string.print)) { print() }
            actions += Triple(R.drawable.ic_feat_merge, getString(R.string.tool_merge_pdf)) {
                startActivity(com.theoccess.alldocreader.ui.create.PickFileActivity.mergeIntent(this, listOf(doc.path)))
            }
            actions += Triple(R.drawable.ic_split_line, getString(R.string.tool_split_pdf)) {
                startActivity(com.theoccess.alldocreader.ui.pages.SplitPdfActivity.intent(this, doc.file))
            }
        }
        actions += Triple(R.drawable.ic_delete, getString(R.string.delete)) {
            FileSheets.confirmDelete(this, listOf(doc)) { finish() }
        }

        actions.chunked(4).forEach { rowActions ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowActions.forEach { (icon, label, action) ->
                val a = ItemFileActionBinding.inflate(LayoutInflater.from(this), row, false)
                a.ivIcon.setImageResource(icon)
                a.tvLabel.text = label
                a.root.setOnClickListener {
                    dialog.dismiss()
                    action()
                }
                row.addView(a.root)
            }
            repeat(4 - rowActions.size) {
                row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
            }
            b.grid.addView(row)
        }
        dialog.setContentView(b.root)
        dialog.show()
    }

    private fun openOrganizer() {
        if (!isPdf) return
        if (!com.theoccess.alldocreader.premium.PremiumGate.use(this, com.theoccess.alldocreader.premium.PremiumGate.Feature.PAGES)) return
        organizer.launch(PageOrganizerActivity.intent(this, doc.file, currentPage()))
    }

    // ------------------------------------------------------------------ convert

    private fun convert() {
        val src = source ?: return
        if (binding.banner.visibility == View.VISIBLE && binding.bannerProgress.visibility == View.VISIBLE) return
        if (!com.theoccess.alldocreader.premium.PremiumGate.use(this, com.theoccess.alldocreader.premium.PremiumGate.Feature.CONVERT)) return
        showBannerProgress(0)
        val base = doc.name.substringBeforeLast('.')
        lifecycleScope.launch {
            val out = try {
                withContext(Dispatchers.IO) {
                    val progress: (Int) -> Unit = { p -> runOnUiThread { showBannerProgress(p) } }
                    when (src) {
                        is PptxPageSource -> Converters.deckToPdf(applicationContext, src.deck, base, progress)
                        is DocPageSource -> Converters.docToPdf(applicationContext, src.layout, base, progress)
                        else -> Converters.pdfToWord(applicationContext, doc.file, base, progress)
                    }
                }
            } catch (e: Throwable) {
                null
            }
            if (out == null) {
                binding.banner.visibility = View.GONE
                toast(R.string.convert_failed)
                return@launch
            }
            convertedFile = out
            FileRepository.refresh(applicationContext, force = true)
            binding.bannerProgress.visibility = View.GONE
            binding.bannerDone.visibility = View.VISIBLE
            binding.tvBanner.setText(R.string.saved_successfully)
            binding.btnBannerOpen.visibility = View.VISIBLE
            binding.btnBannerClose.visibility = View.VISIBLE
            com.theoccess.alldocreader.ads.Ads.showInterstitial(this@ViewerActivity)
        }
    }

    private fun showBannerProgress(p: Int) {
        binding.banner.visibility = View.VISIBLE
        binding.bannerProgress.visibility = View.VISIBLE
        binding.bannerDone.visibility = View.GONE
        binding.btnBannerOpen.visibility = View.GONE
        binding.btnBannerClose.visibility = View.GONE
        binding.tvBanner.text = getString(R.string.converting_progress, p)
    }

    // ------------------------------------------------------------------ print (PDF)

    private fun print() = PdfPrint.print(this, doc.file, doc.name)

    override fun onDestroy() {
        searchJob?.cancel()
        source?.close()
        renderDispatcher.close()
        searchDispatcher.close()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PATH = "path"
        private const val KEY_TIP = "viewer_jump_tip_shown"
        private const val KEY_SWIPE_TIP = "viewer_swipe_tip_shown"
        /** Pages laid out before a Word / TXT / Excel document is shown. */
        private const val FIRST_PAGES = 3
        private val ERROR_PASSWORD = Any()
        private val ERROR_OTHER = Any()

        /** Pictures (JPG, PNG…) open in [ImageViewerActivity], everything else in this reader. */
        fun intent(context: Context, file: File): Intent =
            if (ImageViewerActivity.canOpen(file.name)) ImageViewerActivity.intent(context, file)
            else Intent(context, ViewerActivity::class.java).putExtra(EXTRA_PATH, file.absolutePath)

        /** Types the in-app viewers can open. */
        fun canOpen(doc: DocFile): Boolean {
            val ext = doc.name.substringAfterLast('.', "").lowercase()
            return doc.type == FileType.PDF || doc.type == FileType.TXT || ImageViewerActivity.canOpen(doc.name) ||
                SheetParser.canOpen(doc.name) ||
                ext == "docx" || ext == "docm" || ext == "dotx" || ext == "pptx" || ext == "ppsx" || ext == "potx"
        }
    }
}
