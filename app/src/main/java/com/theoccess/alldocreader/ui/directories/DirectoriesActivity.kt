package com.theoccess.alldocreader.ui.directories

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityDirectoriesBinding
import com.theoccess.alldocreader.ui.search.SearchActivity

/** "Directories": Storage / All documents / Recycle bin. */
class DirectoriesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDirectoriesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDirectoriesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSearch.setOnClickListener { startActivity(Intent(this, SearchActivity::class.java)) }

        val titles = listOf(R.string.storage, R.string.all_documents, R.string.recycle_bin)
        binding.pager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = 3
            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> StorageVolumesFragment()
                1 -> AllDocumentsFragment()
                else -> RecycleBinFragment()
            }
        }
        TabLayoutMediator(binding.tabs, binding.pager) { tab, pos -> tab.setText(titles[pos]) }.attach()
        intent.getIntExtra(EXTRA_TAB, 0).takeIf { it in 0..2 && savedInstanceState == null }?.let {
            binding.pager.setCurrentItem(it, false)
        }
    }

    companion object {
        const val EXTRA_TAB = "tab"
    }
}
