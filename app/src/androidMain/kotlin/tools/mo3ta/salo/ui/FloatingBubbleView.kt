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

    private val countText: TextView
    private val tooltipGroup: LinearLayout
    var onTap: () -> Unit = {}
    var onClose: () -> Unit = {}

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL

        tooltipGroup = buildTooltip(context)
        tooltipGroup.alpha = 0f
        addView(tooltipGroup)

        val bubbleContainer = buildBubble(context)
        addView(bubbleContainer)

        countText = bubbleContainer.findViewWithTag("count")
    }

    fun updateCount(count: Int) {
        countText.text = count.toString()
    }

    fun showTooltip() {
        tooltipGroup.animate().alpha(1f).setDuration(300).start()
    }

    fun hideTooltip() {
        tooltipGroup.animate().alpha(0f).setDuration(300).start()
    }

    private fun buildTooltip(context: Context): LinearLayout {
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt() }

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
            setPadding(dp(16), dp(10), dp(16), dp(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(50, 255, 255, 255))
                setStroke(1, Color.argb(65, 255, 255, 255))
            }
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                bottomMargin = dp(4)
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
        val dp = { v: Int -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt() }
        val bubbleSize = dp(68)

        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(bubbleSize + dp(12), bubbleSize + dp(12))
        }

        val circle = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#1b5e20"))
            ).apply { shape = GradientDrawable.OVAL }
            elevation = dp(4).toFloat()
            setOnClickListener { onTap() }
        }

        val label = TextView(context).apply {
            text = "صلوات"
            setTextColor(Color.argb(180, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = dp(14)
            }
        }
        circle.addView(label)

        val count = TextView(context).apply {
            tag = "count"
            text = "0"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                topMargin = dp(6)
            }
        }
        circle.addView(count)

        container.addView(circle)

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
            }
            val size = dp(20)
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setOnClickListener { onClose() }
        }
        container.addView(closeBtn)

        return container
    }
}
