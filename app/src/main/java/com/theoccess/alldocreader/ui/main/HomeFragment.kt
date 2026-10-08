package com.theoccess.alldocreader.ui.main

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.data.Category
import com.theoccess.alldocreader.data.DocFile
import com.theoccess.alldocreader.data.FileRepository
import com.theoccess.alldocreader.data.LibraryStore
import com.theoccess.alldocreader.data.Prefs
import com.theoccess.alldocreader.databinding.DialogPermissionBinding
import com.theoccess.alldocreader.databinding.FragmentHomeBinding
import com.theoccess.alldocreader.ui.directories.DirectoriesActivity
import com.theoccess.alldocreader.ui.create.CreateFilesSheet
import com.theoccess.alldocreader.ui.pages.InsertBlankPagesActivity
import com.theoccess.alldocreader.ui.files.FileAdapter
import com.theoccess.alldocreader.ui.files.FileListActivity
import com.theoccess.alldocreader.ui.search.SearchActivity
import com.theoccess.alldocreader.util.ActionSheet
import com.theoccess.alldocreader.util.FileActions
import com.theoccess.alldocreader.util.StorageAccess
import com.theoccess.alldocreader.util.dp
import com.theoccess.alldocreader.util.formatSize
import com.theoccess.alldocreader.util.toast
import java.io.File

/** "All files" tab – the app's home/lobby screen. */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var fileAdapter: FileAdapter
    private var selectedTab = TAB_RECENT
    private var planeAnim: AnimatorSet? = null
    private var permissionDialog: Dialog? = null
    private var pendingCategory: Category? = null
    /** 0 = hidden, 1 = "Loading files…", 2 = "Loaded successfully". */
    private var loadStatus = 0

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            maybeReloadAfterSave()
            renderList()
            updateRecentBadge(FileRepository.current)
        }
    }

    /** Something was saved elsewhere in the app: reload the file counts like the original. */
    private fun maybeReloadAfterSave() {
        val ctx = context ?: return
        if (!com.theoccess.alldocreader.data.SavedFiles.pendingReload || !StorageAccess.has(ctx)) return
        com.theoccess.alldocreader.data.SavedFiles.pendingReload = false
        setLoadStatus(1)
        FileRepository.refresh(ctx, force = true)
    }

    private fun setLoadStatus(status: Int) {
        val b = _binding ?: return
        loadStatus = status
        b.rowLoadStatus.visibility = if (status == 0) View.GONE else View.VISIBLE
        b.rowRecentlyAdded.visibility = if (status == 0) View.VISIBLE else View.GONE
        b.pbLoadStatus.visibility = if (status == 1) View.VISIBLE else View.GONE
        b.ivLoadStatus.visibility = if (status == 2) View.VISIBLE else View.GONE
        b.tvLoadStatus.setText(if (status == 2) R.string.loaded_successfully else R.string.loading_files)
        if (status == 2) b.rowLoadStatus.postDelayed({ if (loadStatus == 2) setLoadStatus(0) }, 1500)
    }

    private val legacyPermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onAccessMaybeChanged() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedTab = savedInstanceState?.getInt(STATE_TAB)
            ?: if (requireActivity().intent.getBooleanExtra(MainActivity.EXTRA_OPEN_BOOKMARKS, false)) TAB_BOOKMARKS else TAB_RECENT
        setupHeader()
        setupCategories()
        setupTabs()
        setupList()
        setupFab()

        FileRepository.state.observe(viewLifecycleOwner) { renderScan(it) }
        LibraryStore.recents.observe(viewLifecycleOwner) { renderList() }
        LibraryStore.bookmarks.observe(viewLifecycleOwner) { entries ->
            fileAdapter.setBookmarks(entries.map { it.path }.toSet())
            renderList()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) maybeReloadAfterSave()
        onAccessMaybeChanged()
        updateRecentBadge(FileRepository.current)
        maybeShowRating()
    }

    /**
     * Like the original: the 5-star rating sheet once, after the user comes back from a file list
     * (not at the same moment as the "Is it helpful?" sheet).
     */
    private fun maybeShowRating() {
        if (!Prefs.visitedFileList || Prefs.rateShown || !Prefs.coachShown || HelpfulSheet.shouldShow()) return
        binding.root.postDelayed({
            if (_binding != null && isResumed && isVisible && !Prefs.rateShown && !HelpfulSheet.isShowing) RateSheet.show(requireContext())
        }, 350)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_TAB, selectedTab)
    }

    // ---------------------------------------------------------------- setup

    private fun setupHeader() {
        binding.rowRecentlyAdded.setOnClickListener {
            if (!StorageAccess.has(requireContext())) showPermissionDialog()
            else {
                Prefs.recentSeenAt = System.currentTimeMillis()
                updateRecentBadge(FileRepository.current)
                startActivity(FileListActivity.intent(requireContext(), Category.ALL, recentlyAdded = true))
            }
        }
        binding.btnSearch.setOnClickListener {
            startActivity(Intent(requireContext(), SearchActivity::class.java))
        }
        binding.btnPremium.setOnClickListener {
            startActivity(Intent(requireContext(), com.theoccess.alldocreader.ui.settings.PremiumActivity::class.java))
        }
    }

    private fun setupCategories() {
        categoryAdapter = CategoryAdapter { onCategoryClick(it) }
        binding.rvCategories.layoutManager = GridLayoutManager(requireContext(), 4)
        binding.rvCategories.adapter = categoryAdapter
        binding.rvCategories.itemAnimator = null
    }

    private fun setupTabs() {
        val tabs = binding.tabLayout
        tabs.addTab(tabs.newTab().setText(R.string.recent), selectedTab == TAB_RECENT)
        tabs.addTab(tabs.newTab().setText(R.string.bookmarks), selectedTab == TAB_BOOKMARKS)
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                selectedTab = tab.position
                renderList()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) = Unit
            override fun onTabReselected(tab: TabLayout.Tab) = Unit
        })
    }

    private fun setupList() {
        fileAdapter = FileAdapter(
            onOpen = { FileActions.open(requireContext(), it) },
            onMore = { FileActions.showMenu(requireContext(), it, inRecent = selectedTab == TAB_RECENT) },
            timeOf = { file -> entryTime(file.path) ?: file.modified }
        )
        binding.rvFiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvFiles.adapter = fileAdapter
    }

    private fun setupFab() {
        binding.fabCreate.setOnClickListener { showCreateSheet() }
        binding.tooltipBody.setOnClickListener {
            hideTooltip(permanently = true)
            startActivity(InsertBlankPagesActivity.createIntent(requireContext()))
        }
        binding.btnTooltipClose.setOnClickListener { hideTooltip(permanently = true) }
    }

    // ---------------------------------------------------------------- permission + scan

    private fun onAccessMaybeChanged() {
        val ctx = context ?: return
        if (StorageAccess.has(ctx)) {
            permissionDialog?.dismiss()
            binding.permissionView.visibility = View.GONE
            FileRepository.refresh(ctx)
            pendingCategory?.let { pendingCategory = null; openCategory(it) }
        } else {
            renderScan(FileRepository.current)
            if (!Prefs.permissionPrompted) {
                Prefs.permissionPrompted = true
                showPermissionDialog()
            }
        }
    }

    private fun showPermissionDialog() {
        if (permissionDialog?.isShowing == true) return
        val ctx = requireContext()
        val b = DialogPermissionBinding.inflate(layoutInflater)
        val dialog = Dialog(ctx)
        dialog.setContentView(b.root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(resources.displayMetrics.widthPixels - ctx.dp(56), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        b.btnAllow.setOnClickListener {
            dialog.dismiss()
            requestAccess()
        }
        b.btnNotNow.setOnClickListener {
            pendingCategory = null
            dialog.dismiss()
        }
        permissionDialog = dialog
        dialog.show()
    }

    private fun requestAccess() {
        if (StorageAccess.usesSettingsPage) StorageAccess.openSettingsPage(requireContext())
        else legacyPermission.launch(StorageAccess.runtimePermissions)
    }

    private fun renderScan(state: FileRepository.ScanState) {
        val b = _binding ?: return
        val hasAccess = StorageAccess.has(requireContext())
        val counts = mutableMapOf<Category, String>()
        if (state.loaded) {
            Category.entries.forEach { c ->
                counts[c] = when (c) {
                    Category.DIRECTORIES -> getString(
                        R.string.storage_usage, formatStorage(state.storageUsed), formatStorage(state.storageTotal)
                    )
                    Category.ALL -> countText(state.totalCount)
                    else -> countText(state.count(c.type!!))
                }
            }
        } else if (!hasAccess) {
            val (used, total) = FileRepository.storageStats()
            counts[Category.DIRECTORIES] = getString(R.string.storage_usage, formatStorage(used), formatStorage(total))
        }
        categoryAdapter.update(counts, loading = state.loading || (!state.loaded && hasAccess))

        val showLoading = hasAccess && state.loading && !state.loaded
        b.loadingView.visibility = if (showLoading) View.VISIBLE else View.GONE
        if (showLoading) startPlane() else stopPlane()
        b.permissionView.visibility = if (!hasAccess) View.VISIBLE else View.GONE
        b.btnGrant.setOnClickListener { showPermissionDialog() }
        renderList()

        if (loadStatus == 1 && !state.loading) setLoadStatus(2)
        if (state.loaded && hasAccess) maybeShowFirstRunHints()
        updateRecentBadge(state)
    }

    /** "+N" next to Recently added: files added since the user last opened that list. */
    private fun updateRecentBadge(state: FileRepository.ScanState) {
        val b = _binding ?: return
        if (Prefs.recentSeenAt == 0L) Prefs.recentSeenAt = System.currentTimeMillis()
        val since = Prefs.recentSeenAt
        val n = if (state.loaded) state.all().count { it.modified > since } else 0
        b.tvRecentBadge.visibility = if (n > 0) View.VISIBLE else View.GONE
        if (n > 0) b.tvRecentBadge.text = if (n > 99) "+99" else "+$n"
    }

    private fun countText(n: Int) = resources.getQuantityString(R.plurals.files_count, n, n)

    /** Storage shown like "37.8 GB". */
    private fun formatStorage(bytes: Long): String = if (bytes <= 0) "0 GB" else formatSize(bytes)

    // ---------------------------------------------------------------- list

    private fun entryTime(path: String): Long? {
        val list = if (selectedTab == TAB_RECENT) LibraryStore.recents.value else LibraryStore.bookmarks.value
        return list?.firstOrNull { it.path == path }?.time
    }

    private fun renderList() {
        val b = _binding ?: return
        val entries = if (selectedTab == TAB_RECENT) LibraryStore.recents.value.orEmpty()
        else LibraryStore.bookmarks.value.orEmpty()
        val files = entries.mapNotNull { e ->
            val f = File(e.path)
            if (f.exists()) DocFile.from(f) else null
        }
        fileAdapter.submitList(files)
        val busy = b.loadingView.visibility == View.VISIBLE || b.permissionView.visibility == View.VISIBLE
        b.emptyView.visibility = if (files.isEmpty() && !busy) View.VISIBLE else View.GONE
        b.rvFiles.visibility = if (files.isEmpty()) View.INVISIBLE else View.VISIBLE
    }

    // ---------------------------------------------------------------- actions

    private fun onCategoryClick(category: Category) {
        if (!StorageAccess.has(requireContext())) {
            pendingCategory = category
            showPermissionDialog()
            return
        }
        openCategory(category)
    }

    private fun openCategory(category: Category) {
        val act = activity ?: return
        // like the original: full-screen ad → the list → "Get Premium" page on top
        com.theoccess.alldocreader.ads.Ads.beforeCategory(act) {
            if (category == Category.DIRECTORIES) act.startActivity(Intent(act, DirectoriesActivity::class.java))
            else act.startActivity(FileListActivity.intent(act, category))
        }
    }

    private fun showCreateSheet() = CreateFilesSheet.show(requireContext())

    // ---------------------------------------------------------------- first-run hints

    private var hintsScheduled = false

    private fun maybeShowFirstRunHints() {
        if (hintsScheduled) return
        hintsScheduled = true
        if (!Prefs.coachShown) {
            binding.rvCategories.postDelayed({
                if (_binding == null || !isVisible) { hintsScheduled = false; return@postDelayed }
                (activity as? MainActivity)?.showCoachMark(binding.rvCategories) {
                    Prefs.coachShown = true
                    showTooltip()
                }
            }, 400)
        } else {
            showTooltip()
        }
    }

    private fun showTooltip() {
        val b = _binding ?: return
        if (Prefs.tooltipDismissed || b.tooltip.visibility == View.VISIBLE) return
        b.tooltip.visibility = View.VISIBLE
        b.tooltip.pivotX = b.tooltip.width.toFloat()
        b.tooltip.pivotY = b.tooltip.height.toFloat()
        b.tooltip.scaleX = 0.6f
        b.tooltip.scaleY = 0.6f
        b.tooltip.alpha = 0f
        b.tooltip.animate().scaleX(1f).scaleY(1f).alpha(1f)
            .setInterpolator(OvershootInterpolator()).setDuration(350).setStartDelay(300).start()
    }

    private fun hideTooltip(permanently: Boolean) {
        if (permanently) Prefs.tooltipDismissed = true
        val b = _binding ?: return
        b.tooltip.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(180).withEndAction {
            _binding?.tooltip?.visibility = View.GONE
        }.start()
    }

    // ---------------------------------------------------------------- loading animation

    private fun startPlane() {
        if (planeAnim?.isRunning == true) return
        val plane = binding.ivPlane
        val d = requireContext().dp(10).toFloat()
        val x = ObjectAnimator.ofFloat(plane, View.TRANSLATION_X, -d, d).apply {
            repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        }
        val y = ObjectAnimator.ofFloat(plane, View.TRANSLATION_Y, d / 2, -d / 2).apply {
            repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        }
        val r = ObjectAnimator.ofFloat(plane, View.ROTATION, -8f, 8f).apply {
            repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        }
        planeAnim = AnimatorSet().apply {
            playTogether(x, y, r)
            duration = 900
            start()
        }
    }

    private fun stopPlane() {
        planeAnim?.cancel()
        planeAnim = null
    }

    override fun onDestroyView() {
        stopPlane()
        permissionDialog?.dismiss()
        permissionDialog = null
        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val TAB_RECENT = 0
        private const val TAB_BOOKMARKS = 1
        private const val STATE_TAB = "tab"
    }
}
