package com.theoccess.alldocreader.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import androidx.appcompat.app.AppCompatActivity
import com.theoccess.alldocreader.R
import com.theoccess.alldocreader.databinding.ActivityPolicyBinding
import com.theoccess.alldocreader.util.Links

/** Terms of use / Privacy policy (blue bar, readable text). */
class PolicyActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val b = ActivityPolicyBinding.inflate(layoutInflater)
        setContentView(b.root)
        val terms = intent.getIntExtra(EXTRA_WHICH, TERMS) == TERMS
        b.tvTitle.setText(if (terms) R.string.terms_of_use else R.string.privacy_policy)
        val html = getString(if (terms) R.string.terms_html else R.string.privacy_html, getString(R.string.app_name), Links.SUPPORT_EMAIL)
        b.tvBody.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        b.tvBody.movementMethod = LinkMovementMethod.getInstance()
        b.btnBack.setOnClickListener { finish() }
    }

    companion object {
        const val TERMS = 0
        const val PRIVACY = 1
        private const val EXTRA_WHICH = "which"

        fun intent(context: Context, which: Int) = Intent(context, PolicyActivity::class.java).putExtra(EXTRA_WHICH, which)
    }
}
