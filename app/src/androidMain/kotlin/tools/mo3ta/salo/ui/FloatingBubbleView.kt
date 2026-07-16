package tools.mo3ta.salo.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** What the small line under the count shows for a given bubble. */
enum class BubbleSubtitle { NONE, FREED_NECKS }

/**
 * Visual + behavioural skin for the floating bubble. One bubble, many skins:
 * salawat keeps a solid gold ring and no goal; each challenge supplies its own
 * gradient/ring/count colours, a daily [goal] that drives the progress arc, and
 * an optional [subtitle] motif.
 */
data class BubbleTheme(
    val gradientStart: Int,
    val gradientEnd: Int,
    val ringColor: Int,
    val countColor: Int,
    val label: String,
    val contentDescription: String,
    val tooltip: String,
    val notifTitle: String,
    val notifText: String,
    /** Daily target that fills the progress arc. 0 = unbounded (no arc), e.g. salawat. */
    val goal: Int = 0,
    val subtitle: BubbleSubtitle = BubbleSubtitle.NONE,
)

class FloatingBubbleView(context: Context, initialTheme: BubbleTheme) : FrameLayout(context) {

    private val labelText: TextView
    private val countText: TextView
    private val subText: TextView

    private var theme: BubbleTheme = initialTheme
    private var count: Int = 0
    private var progressFraction: Float = 0f
    private var flashing: Boolean = false

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcRect = RectF()

    private fun Int.dp(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    init {
        val bubbleSize = 72.dp()
        layoutParams = LayoutParams(bubbleSize, bubbleSize)
        elevation = 8.dp().toFloat()
        // FrameLayout doesn't draw by default — we paint the goal arc in onDraw.
        setWillNotDraw(false)

        val stack = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        labelText = TextView(context).apply {
            textDirection = TEXT_DIRECTION_RTL
            setTextColor(Color.argb(160, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }

        countText = TextView(context).apply {
            text = "0"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }

        subText = TextView(context).apply {
            textDirection = TEXT_DIRECTION_RTL
            setTextColor(Color.argb(150, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            visibility = GONE
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }

        stack.addView(labelText)
        stack.addView(countText)
        stack.addView(subText)
        addView(stack)

        applyTheme(initialTheme)
    }

    /** Re-skin the bubble in place (used when the user switches challenge type). */
    fun applyTheme(newTheme: BubbleTheme) {
        theme = newTheme
        contentDescription = newTheme.contentDescription

        val strokePx = 3.dp()
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(newTheme.gradientStart, newTheme.gradientEnd),
        ).apply {
            shape = GradientDrawable.OVAL
            // With a goal, the fixed ring becomes a faint track and the bright arc shows progress.
            val ring = if (newTheme.goal > 0) withAlpha(newTheme.ringColor, 60) else newTheme.ringColor
            setStroke(strokePx, ring)
        }

        labelText.text = newTheme.label
        countText.setTextColor(newTheme.countColor)

        recomputeProgress()
        updateSubtitle()
        invalidate()
    }

    fun updateCount(count: Int) {
        this.count = count
        countText.text = count.toString()
        recomputeProgress()
        updateSubtitle()
        invalidate()
    }

    private fun recomputeProgress() {
        progressFraction = if (theme.goal > 0) {
            (count.toFloat() / theme.goal.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    private fun updateSubtitle() {
        val text = when (theme.subtitle) {
            BubbleSubtitle.FREED_NECKS -> if (count >= 10) "🔓 ${count / 10}" else ""
            BubbleSubtitle.NONE -> ""
        }
        subText.text = text
        subText.visibility = if (text.isEmpty()) GONE else VISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (theme.goal <= 0 || progressFraction <= 0f) return
        val sw = 3.dp().toFloat() + 1f
        val inset = sw / 2f + 1.dp()
        arcRect.set(inset, inset, width - inset, height - inset)
        arcPaint.strokeWidth = sw
        arcPaint.color = if (flashing) FLASH_COLOR else theme.ringColor
        canvas.drawArc(arcRect, -90f, progressFraction * 360f, false, arcPaint)
    }

    fun animateTap() {
        animate().cancel()
        animate().scaleX(1.25f).scaleY(1.25f).setDuration(100)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()
    }

    /** Stronger pulse + a brief gold ring flash when a decade / daily goal is reached. */
    fun animateMilestone() {
        flashing = true
        invalidate()
        postDelayed({
            flashing = false
            invalidate()
        }, 450L)
        animate().cancel()
        animate().scaleX(1.35f).scaleY(1.35f).setDuration(120)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(180).start()
            }.start()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private companion object {
        val FLASH_COLOR = Color.parseColor("#FFD700")
    }
}
