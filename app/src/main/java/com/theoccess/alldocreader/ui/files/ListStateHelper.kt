package com.theoccess.alldocreader.ui.files

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import com.theoccess.alldocreader.databinding.LayoutListStateBinding
import com.theoccess.alldocreader.util.dp

/** Shows the loading (paper plane) or empty state of a list screen. */
class ListStateHelper(private val b: LayoutListStateBinding) {

    private var anim: ObjectAnimator? = null

    fun showLoading() {
        b.emptyView.visibility = View.GONE
        b.loadingView.visibility = View.VISIBLE
        if (anim == null) {
            anim = ObjectAnimator.ofFloat(b.ivPlane, View.TRANSLATION_Y, 0f, -b.root.context.dp(10).toFloat()).apply {
                duration = 700
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                start()
            }
        }
    }

    fun showContent(isEmpty: Boolean) {
        anim?.cancel()
        anim = null
        b.loadingView.visibility = View.GONE
        b.emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    fun hideAll() = showContent(false)
}
