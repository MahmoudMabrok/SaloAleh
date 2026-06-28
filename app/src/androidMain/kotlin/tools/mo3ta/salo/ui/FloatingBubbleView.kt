package tools.mo3ta.salo.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class FloatingBubbleView(context: Context) : LinearLayout(context) {

    var onTap: () -> Unit = {}
    var onClose: () -> Unit = {}
    var onOpenApp: () -> Unit = {}

    private lateinit var bubbleCircle: FrameLayout
    private lateinit var countText: TextView
    private lateinit var tooltipGroup: LinearLayout
    private lateinit var closeTarget: TextView
    private lateinit var openTarget: TextView
    private lateinit var actionRow: LinearLayout

    private fun Int.dp(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        tooltipGroup = buildTooltip(context)
        tooltipGroup.alpha = 0f
        addView(tooltipGroup)

        val bubbleContainer = buildBubble(context)
        addView(bubbleContainer)

        actionRow = buildActionRow(context)
        actionRow.alpha = 0f
        actionRow.translationY = (-8).dp().toFloat()
        addView(actionRow)
    }

    fun updateCount(count: Int) {
        countText.text = count.toString()
    }

    fun animateTap() {
        bubbleCircle.animate().cancel()
        bubbleCircle.animate()
            .scaleX(1.25f).scaleY(1.25f)
            .setDuration(100)
            .withEndAction {
                bubbleCircle.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(150)
                    .start()
            }.start()
    }

    fun showTooltip() {
        tooltipGroup.animate().cancel()
        tooltipGroup.animate().alpha(1f).setDuration(300).start()
    }

    fun hideTooltip() {
        tooltipGroup.animate().cancel()
        tooltipGroup.animate().alpha(0f).setDuration(300).start()
    }

    fun showActionTargets() {
        actionRow.visibility = VISIBLE
        actionRow.animate().cancel()
        actionRow.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(250)
            .start()
    }

    fun hideActionTargets() {
        actionRow.animate().cancel()
        actionRow.animate()
            .alpha(0f)
            .translationY((-8).dp().toFloat())
            .setDuration(200)
            .withEndAction { actionRow.visibility = INVISIBLE }
            .start()
    }

    fun getCloseTargetCenter(): Pair<Float, Float> {
        val loc = IntArray(2)
        closeTarget.getLocationOnScreen(loc)
        return Pair(loc[0] + closeTarget.width / 2f, loc[1] + closeTarget.height / 2f)
    }

    fun getOpenAppTargetCenter(): Pair<Float, Float> {
        val loc = IntArray(2)
        openTarget.getLocationOnScreen(loc)
        return Pair(loc[0] + openTarget.width / 2f, loc[1] + openTarget.height / 2f)
    }

    fun highlightCloseTarget(on: Boolean) {
        val scale = if (on) 1.2f else 1.0f
        closeTarget.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
    }

    fun highlightOpenTarget(on: Boolean) {
        val scale = if (on) 1.2f else 1.0f
        openTarget.animate().scaleX(scale).scaleY(scale).setDuration(120).start()
    }

    private fun buildTooltip(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }
        val card = TextView(context).apply {
            text = "اللهم صل علي محمد وال محمد"
            textDirection = TEXT_DIRECTION_RTL
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(16.dp(), 10.dp(), 16.dp(), 10.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14.dp().toFloat()
                setColor(Color.argb(50, 255, 255, 255))
                setStroke(1, Color.argb(65, 255, 255, 255))
            }
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                bottomMargin = 4.dp()
            }
        }
        container.addView(card)
        val arrow = TextView(context).apply {
            text = "▼"
            setTextColor(Color.argb(65, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }
        container.addView(arrow)
        return container
    }

    private fun buildBubble(context: Context): FrameLayout {
        val bubbleSize = 72.dp()

        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(bubbleSize + 8.dp(), bubbleSize + 8.dp())
        }

        bubbleCircle = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.CENTER
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1B5E20"), Color.parseColor("#0D1B4B"))
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(3.dp(), Color.parseColor("#FFD700"))
            }
            elevation = 8.dp().toFloat()
            contentDescription = "اضغط للصلاة على النبي"
        }

        val label = TextView(context).apply {
            text = "صلوات"
            textDirection = TEXT_DIRECTION_RTL
            setTextColor(Color.argb(180, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = 16.dp()
            }
        }
        bubbleCircle.addView(label)

        countText = TextView(context).apply {
            text = "0"
            setTextColor(Color.parseColor("#FFD700"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, 4.dp(), 0, 0)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                topMargin = 6.dp()
            }
        }
        bubbleCircle.addView(countText)

        container.addView(bubbleCircle)
        return container
    }

    private fun buildActionRow(context: Context): LinearLayout {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = 8.dp()
            }
        }

        closeTarget = buildTarget(context, "✕", Color.parseColor("#B71C1C"))
        openTarget = buildTarget(context, "↗", Color.parseColor("#FFD700"))

        row.addView(closeTarget)
        val spacer = FrameLayout(context).apply {
            layoutParams = LayoutParams(16.dp(), 1)
        }
        row.addView(spacer)
        row.addView(openTarget)

        return row
    }

    private fun buildTarget(context: Context, icon: String, bgColor: Int): TextView {
        val size = 48.dp()
        return TextView(context).apply {
            text = icon
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            elevation = 6.dp().toFloat()
            layoutParams = LayoutParams(size, size)
        }
    }
}
