package tools.mo3ta.salo.ui

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.android.ext.android.inject
import tools.mo3ta.salo.data.dhikr.DhikrChallengeStore
import tools.mo3ta.salo.data.engagement.ChallengeBadgeStore
import tools.mo3ta.salo.data.engagement.DailyGoalStore
import tools.mo3ta.salo.data.istighfar.IstighfarChallengeStore
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.notification.NotificationChannels

class FloatingBubbleService : Service() {

    /** Which activity the floating bubble counts. */
    enum class BubbleType(val id: String) {
        SALAWAT("salawat"),
        DHIKR("dhikr"),
        ISTIGHFAR("istighfar");

        companion object {
            fun from(id: String?): BubbleType = entries.firstOrNull { it.id == id } ?: SALAWAT
        }
    }

    companion object {
        const val EXTRA_ROUND_KEY = "round_key"
        const val EXTRA_BUBBLE_TYPE = "bubble_type"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        // Which bubble (by BubbleType.id) is currently on screen, or null when none.
        private val _activeType = MutableStateFlow<String?>(null)
        val activeType: StateFlow<String?> = _activeType
        private const val DRAG_THRESHOLD_PX = 10
        private const val TAP_THRESHOLD = 10
        private const val TAP_DURATION_MS = 300L
        private const val LONG_PRESS_MS = 400L
    }

    private val sessionStore: MohamedLoversSessionStore by inject()
    private val dailyGoalStore: DailyGoalStore by inject()
    private val dhikrStore: DhikrChallengeStore by inject()
    private val istighfarStore: IstighfarChallengeStore by inject()
    private val challengeBadgeStore: ChallengeBadgeStore by inject()
    private val analyticsManager: tools.mo3ta.salo.analytics.AnalyticsManager by inject()

    private var bubbleType: BubbleType = BubbleType.SALAWAT

    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: FloatingBubbleView
    private lateinit var bubbleParams: WindowManager.LayoutParams

    // Separate overlay for action targets — fixed at top of screen, not a child of bubble
    private var actionOverlayView: LinearLayout? = null
    private var closeTargetView: TextView? = null
    private var openTargetView: TextView? = null

    // Separate overlay for tooltip — FLAG_NOT_TOUCHABLE, positioned above bubble
    private var tooltipView: LinearLayout? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var roundKey: String = ""
    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())

    private var targetsVisible = false
    private val longPressRunnable = Runnable {
        targetsVisible = true
        bubbleView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        showActionOverlay()
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == watchedCountKey() && ::bubbleView.isInitialized) {
            mainHandler.post { bubbleView.updateCount(currentCount()) }
        }
    }

    private fun cairoToday(): LocalDate = Clock.System.todayIn(TimeZone.of("Africa/Cairo"))

    // The SharedPreferences key whose changes should refresh the bubble's number.
    private fun watchedCountKey(): String = when (bubbleType) {
        BubbleType.SALAWAT -> "pending_count_$roundKey"
        BubbleType.DHIKR -> "dhikr_challenge_pending"
        BubbleType.ISTIGHFAR -> "istighfar_challenge_pending"
    }

    // The number shown inside the bubble for the current type.
    private fun currentCount(): Int = when (bubbleType) {
        BubbleType.SALAWAT -> prefs.getInt("pending_count_$roundKey", 0)
        BubbleType.DHIKR -> dhikrStore.todayCount(cairoToday())
        BubbleType.ISTIGHFAR -> istighfarStore.todayCount(cairoToday())
    }

    private fun Int.dp(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("ml_session", Context.MODE_PRIVATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newType = BubbleType.from(intent?.getStringExtra(EXTRA_BUBBLE_TYPE))
        intent?.getStringExtra(EXTRA_ROUND_KEY)?.let { roundKey = it }
        // Only the salawat bubble needs an active round; challenges run off the Cairo day.
        if (newType == BubbleType.SALAWAT && roundKey.isBlank()) { stopSelf(); return START_NOT_STICKY }

        val firstStart = !::bubbleView.isInitialized
        bubbleType = newType
        val theme = themeFor(newType)

        if (firstStart) {
            startForeground(NotificationChannels.NOTIF_ID_BUBBLE, buildNotification(theme))
            setupBubble(theme)
            bubbleView.updateCount(currentCount())
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            startReminderCycle()
        } else {
            // Switching type on an already-visible bubble: re-skin in place.
            bubbleView.applyTheme(theme)
            bubbleView.updateCount(currentCount())
            startForeground(NotificationChannels.NOTIF_ID_BUBBLE, buildNotification(theme))
        }
        _activeType.value = newType.id
        return START_NOT_STICKY
    }

    private fun themeFor(type: BubbleType): BubbleTheme = when (type) {
        BubbleType.SALAWAT -> BubbleTheme(
            gradientStart = Color.parseColor("#1B5E20"),
            gradientEnd = Color.parseColor("#0D1B4B"),
            ringColor = Color.parseColor("#FFD700"),
            countColor = Color.parseColor("#FFD700"),
            label = "صلوات",
            contentDescription = "اضغط للصلاة على النبي",
            tooltip = "اللهم صل علي محمد وال محمد",
            notifTitle = "صلوات",
            notifText = "اللهم صل علي محمد وال محمد",
            goal = 0,
            subtitle = BubbleSubtitle.NONE,
        )
        BubbleType.DHIKR -> BubbleTheme(
            gradientStart = Color.parseColor("#0B6135"),
            gradientEnd = Color.parseColor("#06301F"),
            ringColor = Color.parseColor("#6FCF9E"),
            countColor = Color.parseColor("#E9C97F"),
            label = "تهليل",
            contentDescription = "اضغط للتهليل",
            tooltip = "لا إله إلا الله وحده لا شريك له",
            notifTitle = "أهل لا إله إلا الله",
            notifText = "لا إله إلا الله وحده لا شريك له",
            goal = ChallengeType.DHIKR.dailyGoal,
            subtitle = BubbleSubtitle.FREED_NECKS,
        )
        BubbleType.ISTIGHFAR -> BubbleTheme(
            gradientStart = Color.parseColor("#5C3A1F"),
            gradientEnd = Color.parseColor("#33200F"),
            ringColor = Color.parseColor("#C08A3E"),
            countColor = Color.parseColor("#E0B978"),
            label = "استغفار",
            contentDescription = "اضغط للاستغفار",
            tooltip = "أستغفر الله العظيم وأتوب إليه",
            notifTitle = "واستغفروه",
            notifText = "أستغفر الله العظيم وأتوب إليه",
            goal = ChallengeType.ISTIGHFAR.dailyGoal,
            subtitle = BubbleSubtitle.NONE,
        )
    }

    // ── WindowManager helpers ─────────────────────────────────────────────────

    private fun overlayParams(w: Int, h: Int, flags: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        flags,
        PixelFormat.TRANSLUCENT
    )

    private fun screenSize(): Pair<Int, Int> {
        val m = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(m)
        return Pair(m.widthPixels, m.heightPixels)
    }

    private fun clampBubble() {
        val (screenW, screenH) = screenSize()
        val margin = 32.dp()
        // bubbleParams.x = distance from RIGHT edge (Gravity.END); bubbleParams.y from TOP
        val bw = bubbleView.width.let { if (it > 0) it else 72.dp() }
        val bh = bubbleView.height.let { if (it > 0) it else 72.dp() }
        bubbleParams.x = bubbleParams.x.coerceIn(margin, screenW - margin - bw)
        bubbleParams.y = bubbleParams.y.coerceIn(margin, screenH - margin - bh)
    }

    // ── Bubble setup ──────────────────────────────────────────────────────────

    private fun setupBubble(theme: BubbleTheme) {
        bubbleView = FloatingBubbleView(this, theme)

        val bubbleSize = 72.dp()
        bubbleParams = overlayParams(
            bubbleSize, bubbleSize,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 200
        }

        setupDrag()
        windowManager.addView(bubbleView, bubbleParams)
    }

    private fun setupDrag() {
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var touchDownTime = 0L
        var isDragging = false

        bubbleView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x; initialY = bubbleParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    touchDownTime = SystemClock.elapsedRealtime()
                    isDragging = false
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val distX = kotlin.math.abs(event.rawX - initialTouchX)
                    val distY = kotlin.math.abs(event.rawY - initialTouchY)
                    if (distX > DRAG_THRESHOLD_PX || distY > DRAG_THRESHOLD_PX) {
                        if (!isDragging) {
                            isDragging = true
                            mainHandler.removeCallbacks(longPressRunnable)
                        }
                        bubbleParams.x = initialX - (event.rawX - initialTouchX).toInt()
                        bubbleParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, bubbleParams)

                        if (targetsVisible) {
                            checkTargetHighlight(event.rawX, event.rawY)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    val distX = kotlin.math.abs(event.rawX - initialTouchX)
                    val distY = kotlin.math.abs(event.rawY - initialTouchY)
                    val duration = SystemClock.elapsedRealtime() - touchDownTime

                    if (targetsVisible) {
                        handleTargetRelease(event.rawX, event.rawY)
                    } else if (distX < TAP_THRESHOLD && distY < TAP_THRESHOLD && duration < TAP_DURATION_MS) {
                        handleTap()
                    } else {
                        clampBubble()
                        windowManager.updateViewLayout(bubbleView, bubbleParams)
                    }
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    mainHandler.removeCallbacks(longPressRunnable)
                    isDragging = false
                    if (targetsVisible) dismissActionOverlay()
                    true
                }
                else -> false
            }
        }
    }

    // ── Bottom dock bar (separate WM view, slides up from bottom on hold) ───────

    private fun showActionOverlay() {
        if (actionOverlayView != null) return
        val (screenW, _) = screenSize()
        val barW = screenW - 64.dp()
        val barH = 72.dp()
        val iconSize = 40.dp()

        // Bar root — pill-shaped frosted container
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 8, 18, 35))
                cornerRadius = (barH / 2).toFloat()
                setStroke(1.dp(), Color.argb(80, 255, 215, 0))
            }
            elevation = 14.dp().toFloat()
        }

        // Close half
        closeTargetView = buildDockIcon("✕", Color.parseColor("#B71C1C"), iconSize)
        val closeHalf = buildDockHalf(closeTargetView!!, "إغلاق", barW / 2, barH)

        // Divider
        val divider = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1.dp(), (barH * 0.55).toInt())
            setBackgroundColor(Color.argb(60, 255, 215, 0))
        }

        // Open half
        openTargetView = buildDockIcon("↗", Color.parseColor("#1565C0"), iconSize)
        val openHalf = buildDockHalf(openTargetView!!, "فتح التطبيق", barW / 2, barH)

        bar.addView(closeHalf)
        bar.addView(divider)
        bar.addView(openHalf)

        bar.alpha = 0f
        bar.translationY = 40.dp().toFloat()

        val params = overlayParams(
            barW, barH,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 40.dp()
        }

        windowManager.addView(bar, params)
        actionOverlayView = bar

        bar.animate().alpha(1f).translationY(0f).setDuration(280).start()
    }

    private fun dismissActionOverlay() {
        targetsVisible = false
        val overlay = actionOverlayView ?: return
        overlay.animate().alpha(0f).translationY(40.dp().toFloat()).setDuration(220)
            .withEndAction {
                runCatching { windowManager.removeView(overlay) }
                actionOverlayView = null
                closeTargetView = null
                openTargetView = null
            }.start()
    }

    private fun buildDockIcon(icon: String, bgColor: Int, size: Int): TextView =
        TextView(this).apply {
            text = icon
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(bgColor)
            }
            elevation = 4.dp().toFloat()
            layoutParams = LinearLayout.LayoutParams(size, size)
        }

    private fun buildDockHalf(icon: TextView, label: String, w: Int, h: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(w, h)
            val gap = 8.dp()
            setPadding(gap, 0, gap, 0)
            addView(icon)
            addView(TextView(this@FloatingBubbleService).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    leftMargin = 8.dp()
                }
            })
        }

    // Bar hit: bubble is in the dock zone when its Y is in the bottom 120dp of screen.
    // Left half = close, right half = open app.
    private fun dockHitResult(rawX: Float, rawY: Float): DockHit {
        val (screenW, screenH) = screenSize()
        val inBar = rawY > screenH - 120.dp()
        if (!inBar) return DockHit.NONE
        return if (rawX < screenW / 2f) DockHit.CLOSE else DockHit.OPEN
    }

    private enum class DockHit { NONE, CLOSE, OPEN }

    private fun checkTargetHighlight(rawX: Float, rawY: Float) {
        val hit = dockHitResult(rawX, rawY)
        val closeScale = if (hit == DockHit.CLOSE) 1.2f else 1f
        val openScale = if (hit == DockHit.OPEN) 1.2f else 1f
        closeTargetView?.animate()?.scaleX(closeScale)?.scaleY(closeScale)?.setDuration(80)?.start()
        openTargetView?.animate()?.scaleX(openScale)?.scaleY(openScale)?.setDuration(80)?.start()
    }

    private fun handleTargetRelease(rawX: Float, rawY: Float) {
        when (dockHitResult(rawX, rawY)) {
            DockHit.CLOSE -> { dismissActionOverlay(); stopSelf() }
            DockHit.OPEN  -> { dismissActionOverlay(); launchApp() }
            DockHit.NONE  -> { dismissActionOverlay(); clampBubble(); windowManager.updateViewLayout(bubbleView, bubbleParams) }
        }
    }

    // ── Tooltip (separate WM view, FLAG_NOT_TOUCHABLE) ────────────────────────

    private fun showTooltip() {
        if (tooltipView != null) return
        val card = TextView(this).apply {
            text = themeFor(bubbleType).tooltip
            textDirection = android.view.View.TEXT_DIRECTION_RTL
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
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        row.addView(card)
        row.addView(TextView(this).apply {
            text = "▼"
            setTextColor(Color.argb(65, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
        })
        row.alpha = 0f

        val p = overlayParams(
            WRAP_CONTENT, WRAP_CONTENT,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = bubbleParams.x
            y = (bubbleParams.y - 80.dp()).coerceAtLeast(8.dp())
        }

        windowManager.addView(row, p)
        tooltipView = row
        row.animate().alpha(1f).setDuration(300).start()
    }

    private fun hideTooltip() {
        val tv = tooltipView ?: return
        tv.animate().alpha(0f).setDuration(300)
            .withEndAction {
                runCatching { windowManager.removeView(tv) }
                tooltipView = null
            }.start()
    }

    // ── Misc ──────────────────────────────────────────────────────────────────

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2; val dy = y1 - y2
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun handleTap() {
        val today = cairoToday()
        when (bubbleType) {
            BubbleType.SALAWAT -> {
                if (roundKey.isBlank()) return
                val pending = sessionStore.incrementPendingClick(roundKey, 1)
                dailyGoalStore.recordTap(today, 1)
                bubbleView.updateCount(pending.clickCount)
                bubbleView.animateTap()
                analyticsManager.logAction(
                    "bubble_tap",
                    mapOf("count" to pending.clickCount.toString(), "type" to bubbleType.id),
                )
            }
            BubbleType.DHIKR ->
                recordChallengeTap(dhikrStore.incrementToday(today), ChallengeType.DHIKR, today)
            BubbleType.ISTIGHFAR ->
                recordChallengeTap(istighfarStore.incrementToday(today), ChallengeType.ISTIGHFAR, today)
        }
    }

    private fun recordChallengeTap(updated: Int, challenge: ChallengeType, today: LocalDate) {
        // Crossing the daily goal earns (idempotently) that challenge's achievement badge.
        if (updated >= challenge.dailyGoal) challengeBadgeStore.recordWin(challenge, today)
        bubbleView.updateCount(updated)
        // Flash on each freed neck (dhikr, every 10) and when the daily goal is reached.
        val isMilestone = updated == challenge.dailyGoal ||
            (challenge == ChallengeType.DHIKR && updated > 0 && updated % 10 == 0)
        if (isMilestone) bubbleView.animateMilestone() else bubbleView.animateTap()
        analyticsManager.logAction(
            "bubble_tap",
            mapOf("count" to updated.toString(), "type" to challenge.id),
        )
    }

    private fun launchApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        intent?.let { startActivity(it) }
        analyticsManager.logAction("bubble_open_app", emptyMap())
    }

    private fun startReminderCycle() {
        scope.launch {
            showTooltip()
            delay(5_000L)
            hideTooltip()
            while (true) {
                delay(10 * 60 * 1000L)
                showTooltip()
                delay(5_000L)
                hideTooltip()
            }
        }
    }

    private fun buildNotification(theme: BubbleTheme): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CHANNEL_BUBBLE)
            .setContentTitle(theme.notifTitle)
            .setContentText(theme.notifText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onDestroy() {
        _isRunning.value = false
        _activeType.value = null
        mainHandler.removeCallbacks(longPressRunnable)
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scope.cancel()
        runCatching { if (::bubbleView.isInitialized) windowManager.removeView(bubbleView) }
        actionOverlayView?.let { runCatching { windowManager.removeView(it) } }
        tooltipView?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
