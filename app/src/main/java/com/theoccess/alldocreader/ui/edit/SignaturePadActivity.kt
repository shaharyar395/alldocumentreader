package com.theoccess.alldocreader.ui.edit

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.theoccess.alldocreader.databinding.ActivitySignaturePadBinding
import com.theoccess.alldocreader.util.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Landscape signature pad: "Sign here", 6 ink colours, pen width, clear, undo / redo, Done.
 * The signature is trimmed, saved with [SignatureStore] and its path returned.
 */
class SignaturePadActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignaturePadBinding
    private val colors = intArrayOf(
        0xFF111111.toInt(), 0xFF5F6673.toInt(), 0xFF1F2A5C.toInt(),
        0xFF1E5BE6.toInt(), 0xFF7A1F2B.toInt(), 0xFFE53935.toInt()
    )
    private val colorViews = ArrayList<FrameLayout>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignaturePadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnClose.setOnClickListener { finish() }
        binding.btnUndo.setOnClickListener { binding.signView.undo(); refresh() }
        binding.btnRedo.setOnClickListener { binding.signView.redo(); refresh() }
        binding.btnClear.setOnClickListener { binding.signView.clear(); refresh() }
        binding.signView.onChanged = { refresh() }
        binding.btnDone.setOnClickListener { done() }
        colors.forEachIndexed { i, c ->
            val f = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(10) }
                setOnClickListener { select(i) }
            }
            colorViews += f
            binding.colors.addView(f)
        }
        select(0)
        binding.sbWidth.max = 99
        binding.sbWidth.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) = applyWidth(p)
            override fun onStartTrackingTouch(sb: SeekBar) = Unit
            override fun onStopTrackingTouch(sb: SeekBar) = Unit
        })
        if (savedInstanceState == null) binding.sbWidth.progress = 19
        applyWidth(binding.sbWidth.progress)
        refresh()
    }

    /** Pen width 1–100 like the original ("20" by default). */
    private fun applyWidth(p: Int) {
        val v = p + 1
        binding.signView.strokeWidthPx = dp(1) + v / 100f * dp(12)
        binding.tvWidth.text = v.toString()
    }

    private fun select(i: Int) {
        binding.signView.color = colors[i]
        colorViews.forEachIndexed { j, v ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(this@SignaturePadActivity.colors[j])
            }
            v.removeAllViews()
            if (i == j) v.addView(android.widget.ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(dp(14), dp(14), android.view.Gravity.CENTER)
                setImageResource(com.theoccess.alldocreader.R.drawable.ic_check)
                androidx.core.widget.ImageViewCompat.setImageTintList(this, android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt()))
            })
        }
    }

    private fun refresh() {
        val has = binding.signView.hasInk
        binding.btnDone.isEnabled = has
        binding.btnUndo.isEnabled = binding.signView.canUndo
        binding.btnRedo.isEnabled = binding.signView.canRedo
        binding.tvHint.visibility = if (has) View.INVISIBLE else View.VISIBLE
    }

    private fun done() {
        val bmp = binding.signView.export() ?: return
        val f = SignatureStore.save(this, bmp)
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_PATH, f.absolutePath))
        finish()
    }

    companion object {
        const val EXTRA_PATH = "path"
        fun intent(context: Context) = Intent(context, SignaturePadActivity::class.java)
    }
}

/** Finger-drawn signature with undo / redo; [export] crops to the ink. */
class SignatureView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private class Stroke(val path: Path, var color: Int, val width: Float, val bounds: RectF)

    /** Ink colour of the whole signature: picking another colour recolours what is already drawn. */
    var color = 0xFF1E5BE6.toInt()
        set(v) {
            field = v
            strokes.forEach { it.color = v }
            undone.forEach { it.color = v }
            invalidate()
        }
    var strokeWidthPx = 6f
    var onChanged: (() -> Unit)? = null

    private val strokes = ArrayList<Stroke>()
    private val undone = ArrayList<Stroke>()
    private var current: Stroke? = null
    private var lx = 0f
    private var ly = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    val hasInk get() = strokes.isNotEmpty()
    val canUndo get() = strokes.isNotEmpty()
    val canRedo get() = undone.isNotEmpty()

    fun undo() { if (strokes.isNotEmpty()) { undone += strokes.removeAt(strokes.lastIndex); invalidate() } }
    fun redo() { if (undone.isNotEmpty()) { strokes += undone.removeAt(undone.lastIndex); invalidate() } }
    fun clear() { strokes.clear(); undone.clear(); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        (strokes + listOfNotNull(current)).forEach { s ->
            paint.color = s.color
            paint.strokeWidth = s.width
            canvas.drawPath(s.path, paint)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val p = Path().apply { moveTo(e.x, e.y); lineTo(e.x + 0.1f, e.y) }
                current = Stroke(p, color, strokeWidthPx, RectF(e.x, e.y, e.x, e.y))
                lx = e.x; ly = e.y
            }
            MotionEvent.ACTION_MOVE -> current?.let { s ->
                val mx = (lx + e.x) / 2; val my = (ly + e.y) / 2
                s.path.quadTo(lx, ly, mx, my)
                s.bounds.union(e.x, e.y)
                lx = e.x; ly = e.y
            }
            MotionEvent.ACTION_UP -> current?.let { s ->
                s.path.lineTo(e.x, e.y)
                s.bounds.union(e.x, e.y)
                strokes += s
                undone.clear()
                current = null
                onChanged?.invoke()
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> current = null
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    fun export(): Bitmap? {
        if (strokes.isEmpty()) return null
        val b = RectF(strokes[0].bounds)
        strokes.forEach { b.union(it.bounds) }
        val pad = strokes.maxOf { it.width } + 6f
        val l = max(0f, b.left - pad); val t = max(0f, b.top - pad)
        val r = min(width.toFloat(), b.right + pad); val bt = min(height.toFloat(), b.bottom + pad)
        val w = (r - l).toInt().coerceAtLeast(1); val h = (bt - t).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(-l, -t)
        strokes.forEach { s -> paint.color = s.color; paint.strokeWidth = s.width; c.drawPath(s.path, paint) }
        return bmp
    }
}
