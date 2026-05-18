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
    private lateinit var closeBtn: TextView
    var onTap: () -> Unit = {}
    var onClose: () -> Unit = {}

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

        countText = bubbleContainer.findViewWithTag("count")
            ?: throw IllegalStateException("Count view not found in FloatingBubbleView")
    }

    fun updateCount(count: Int) {
        countText.text = count.toString()
    }

    // Asymmetric padding: generous outward (screen edge), small inward (avoid stealing bubble taps)
    fun isCloseButtonHit(rawX: Float, rawY: Float): Boolean {
        val loc = IntArray(2)
        closeBtn.getLocationOnScreen(loc)
        val padOut = 14.dp()
        val padIn = 4.dp()
        return rawX >= loc[0] - padIn && rawX < loc[0] + closeBtn.width + padOut &&
               rawY >= loc[1] - padOut && rawY < loc[1] + closeBtn.height + padIn
    }

    fun showTooltip() {
        tooltipGroup.animate().cancel()
        tooltipGroup.animate().alpha(1f).setDuration(300).start()
    }

    fun hideTooltip() {
        tooltipGroup.animate().cancel()
        tooltipGroup.animate().alpha(0f).setDuration(300).start()
    }

    private fun buildTooltip(context: Context): LinearLayout {

        val container = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }

        val card = TextView(context).apply {
            text = "الله أكبر، الله أكبر، الله أكبر، لا إله إلا الله،\nوالله أكبر، الله أكبر، الله أكبر، ولله الحمد"
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
        val bubbleSize = 68.dp()

        val container = FrameLayout(context).apply {
            layoutParams = LayoutParams(bubbleSize + 12.dp(), bubbleSize + 12.dp())
        }

        val circle = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(bubbleSize, bubbleSize).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#4CAF50"), Color.parseColor("#1b5e20"))
            ).apply { shape = GradientDrawable.OVAL }
            elevation = 4.dp().toFloat()
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
                topMargin = 14.dp()
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
                topMargin = 6.dp()
            }
        }
        circle.addView(count)

        container.addView(circle)

        closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
            }
            val size = 20.dp()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            contentDescription = "إغلاق الفقاعة"
        }
        container.addView(closeBtn)

        return container
    }
}
