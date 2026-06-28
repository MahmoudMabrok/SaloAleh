package tools.mo3ta.salo.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView

class FloatingBubbleView(context: Context) : FrameLayout(context) {

    private val countText: TextView

    private fun Int.dp(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    init {
        val bubbleSize = 72.dp()
        layoutParams = LayoutParams(bubbleSize, bubbleSize)

        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#1B5E20"), Color.parseColor("#0D1B4B"))
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(3.dp(), Color.parseColor("#FFD700"))
        }
        elevation = 8.dp().toFloat()
        contentDescription = "اضغط للصلاة على النبي"

        val label = TextView(context).apply {
            text = "صلوات"
            textDirection = TEXT_DIRECTION_RTL
            setTextColor(Color.argb(180, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                topMargin = 16.dp()
            }
        }
        addView(label)

        countText = TextView(context).apply {
            text = "0"
            setTextColor(Color.parseColor("#FFD700"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setPadding(0, 4.dp(), 0, 0)
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
                topMargin = 6.dp()
            }
        }
        addView(countText)
    }

    fun updateCount(count: Int) {
        countText.text = count.toString()
    }

    fun animateTap() {
        animate().cancel()
        animate().scaleX(1.25f).scaleY(1.25f).setDuration(100)
            .withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(150).start()
            }.start()
    }
}
