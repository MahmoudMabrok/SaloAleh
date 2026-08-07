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
import kotlinx.coroutines.Job
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
import tools.mo3ta.salo.data.zabad.ZabadChallengeStore
import tools.mo3ta.salo.data.ghars.GharsChallengeStore
import tools.mo3ta.salo.data.alfhasana.AlfHasanaChallengeStore
import tools.mo3ta.salo.data.kalimat.KalimatChallengeStore
import tools.mo3ta.salo.data.hawqala.HawqalaChallengeStore
import tools.mo3ta.salo.input.isSyntheticTap
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore
import tools.mo3ta.salo.domain.ChallengeType
import tools.mo3ta.salo.notification.NotificationChannels

class FloatingBubbleService : Service() {

    /** Which activity the floating bubble counts. */
    enum class BubbleType(val id: String) {
        SALAWAT("salawat"),
        DHIKR("dhikr"),
        ISTIGHFAR("istighfar"),
        ZABAD("zabad"),
        GHARS("ghars"),
        ALF_HASANA("alf_hasana"),
        KALIMAT("kalimat"),
        HAWQALA("hawqala");

        /**
         * NotificationAction id for this type's challenge screen, or null for salawat
         * (which just opens the main screen). Matches NotificationAction.from() strings.
         */
        val openChallengeAction: String?
            get() = when (this) {
                SALAWAT -> null
                DHIKR -> "open_dhikr_challenge"
                ISTIGHFAR -> "open_istighfar_challenge"
                ZABAD -> "open_zabad_challenge"
                GHARS -> "open_ghars_challenge"
                ALF_HASANA -> "open_alf_hasana_challenge"
                KALIMAT -> "open_kalimat_challenge"
                HAWQALA -> "open_hawqala_challenge"
            }

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
        private const val SWITCH_TOOLTIP_MS = 4_000L
        // Intent extra naming the challenge screen to open when the bubble is
        // dragged onto "open app". Value matches NotificationAction.from() strings.
        const val EXTRA_OPEN_CHALLENGE = "bubble_open_challenge"
    }

    private val sessionStore: MohamedLoversSessionStore by inject()
    private val dailyGoalStore: DailyGoalStore by inject()
    private val dhikrStore: DhikrChallengeStore by inject()
    private val istighfarStore: IstighfarChallengeStore by inject()
    private val zabadStore: ZabadChallengeStore by inject()
    private val gharsStore: GharsChallengeStore by inject()
    private val alfHasanaStore: AlfHasanaChallengeStore by inject()
    private val kalimatStore: KalimatChallengeStore by inject()
    private val hawqalaStore: HawqalaChallengeStore by inject()
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

    // Type-switch picker — a row of chips at the top; drag the bubble onto one to switch.
    private var typePickerView: LinearLayout? = null
    private val chipViews = mutableListOf<Pair<BubbleType, TextView>>()
    private var pickerRowLeft = 0
    private var pickerRowRight = 0
    private var pickerBandBottom = 0
    private var pickerSegWidth = 0

    // Separate overlay for tooltip — FLAG_NOT_TOUCHABLE, positioned above bubble
    private var tooltipView: LinearLayout? = null
    // Auto-hide timer for the zikr tooltip flashed when the type is switched.
    private var switchTooltipJob: Job? = null

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
        BubbleType.ZABAD -> "zabad_challenge_pending"
        BubbleType.GHARS -> "ghars_challenge_pending"
        BubbleType.ALF_HASANA -> "alf_hasana_challenge_pending"
        BubbleType.KALIMAT -> "kalimat_challenge_pending"
        BubbleType.HAWQALA -> "hawqala_challenge_pending"
    }

    // The number shown inside the bubble for the current type.
    private fun currentCount(): Int = when (bubbleType) {
        BubbleType.SALAWAT -> prefs.getInt("pending_count_$roundKey", 0)
        BubbleType.DHIKR -> dhikrStore.todayCount(cairoToday())
        BubbleType.ISTIGHFAR -> istighfarStore.todayCount(cairoToday())
        BubbleType.ZABAD -> zabadStore.todayCount(cairoToday())
        BubbleType.GHARS -> gharsStore.todayCount(cairoToday())
        BubbleType.ALF_HASANA -> alfHasanaStore.todayCount(cairoToday())
        BubbleType.KALIMAT -> kalimatStore.todayCount(cairoToday())
        BubbleType.HAWQALA -> hawqalaStore.todayCount(cairoToday())
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

        if (firstStart) {
            bubbleType = newType
            val theme = themeFor(newType)
            startForeground(NotificationChannels.NOTIF_ID_BUBBLE, buildNotification(theme))
            setupBubble(theme)
            bubbleView.updateCount(currentCount())
            prefs.registerOnSharedPreferenceChangeListener(prefListener)
            startReminderCycle()
            _activeType.value = newType.id
        } else {
            // Switching type on an already-visible bubble: re-skin in place.
            switchType(newType)
        }
        return START_NOT_STICKY
    }

    /** Re-skin the live bubble to [newType] in place: theme, count, foreground notification. */
    private fun switchType(newType: BubbleType) {
        if (!::bubbleView.isInitialized || newType == bubbleType) return
        // Salawat needs an active round; a challenge-started bubble has none, so ignore it.
        if (newType == BubbleType.SALAWAT && roundKey.isBlank()) return
        val previous = bubbleType
        bubbleType = newType
        val theme = themeFor(newType)
        bubbleView.applyTheme(theme)
        bubbleView.updateCount(currentCount())
        startForeground(NotificationChannels.NOTIF_ID_BUBBLE, buildNotification(theme))
        _activeType.value = newType.id
        // Surface the new type's zikr above the bubble so the user knows what to recite.
        flashSwitchTooltip()
        analyticsManager.logAction(
            "bubble_switch_type",
            mapOf("from" to previous.id, "to" to newType.id),
        )
    }

    /** Briefly show the current type's zikr tooltip above the bubble, then auto-hide it. */
    private fun flashSwitchTooltip() {
        switchTooltipJob?.cancel()
        // Remove any tooltip already on screen so the new zikr text is shown.
        tooltipView?.let { runCatching { windowManager.removeView(it) } }
        tooltipView = null
        showTooltip()
        switchTooltipJob = scope.launch {
            delay(SWITCH_TOOLTIP_MS)
            hideTooltip()
        }
    }

    // Bubble types the on-screen picker offers: salawat only when a round is active.
    private fun switchableTypes(): List<BubbleType> =
        BubbleType.entries.filter { it != BubbleType.SALAWAT || roundKey.isNotBlank() }

    private fun themeFor(type: BubbleType): BubbleTheme = when (type) {
        BubbleType.SALAWAT -> BubbleTheme(
            gradientStart = Color.parseColor("#1B5E20"),
            gradientEnd = Color.parseColor("#0D1B4B"),
            ringColor = Color.parseColor("#FFD700"),
            countColor = Color.parseColor("#FFD700"),
            label = "صلوات",
            name = "صلوات",
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
            name = "أهل لا إله إلا الله",
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
            name = "واستغفروه",
            contentDescription = "اضغط للاستغفار",
            tooltip = "أستغفر الله العظيم وأتوب إليه",
            notifTitle = "واستغفروه",
            notifText = "أستغفر الله العظيم وأتوب إليه",
            goal = ChallengeType.ISTIGHFAR.dailyGoal,
            subtitle = BubbleSubtitle.NONE,
        )
        BubbleType.ZABAD -> BubbleTheme(
            gradientStart = Color.parseColor("#12708A"),
            gradientEnd = Color.parseColor("#04121C"),
            ringColor = Color.parseColor("#2ED3C4"),
            countColor = Color.parseColor("#E9C46A"),
            label = "زبد",
            name = "تسبيح المئة",
            contentDescription = "اضغط للتسبيح",
            tooltip = "سبحان الله وبحمده",
            notifTitle = "زبد البحر",
            notifText = "سبحان الله وبحمده",
            goal = ChallengeType.ZABAD.dailyGoal,
            subtitle = BubbleSubtitle.NONE,
        )
        BubbleType.GHARS -> BubbleTheme(
            gradientStart = Color.parseColor("#2E7D4F"),
            gradientEnd = Color.parseColor("#0B2A22"),
            ringColor = Color.parseColor("#C4762A"),
            countColor = Color.parseColor("#F5D97A"),
            label = "غرس",
            name = "اغرس نخلة",
            contentDescription = "اضغط للغرس",
            tooltip = "سبحان الله العظيم وبحمده",
            notifTitle = "الغَرْس",
            notifText = "سبحان الله العظيم وبحمده",
            goal = ChallengeType.GHARS.dailyGoal,
            subtitle = BubbleSubtitle.NONE,
        )
        BubbleType.ALF_HASANA -> BubbleTheme(
            gradientStart = Color.parseColor("#3A2C52"),
            gradientEnd = Color.parseColor("#0E1B33"),
            ringColor = Color.parseColor("#E9C462"),
            countColor = Color.parseColor("#F2D98A"),
            label = "تسبيح",
            name = "ألف حسنة",
            contentDescription = "اضغط للتسبيح",
            tooltip = "سبحان الله",
            notifTitle = "ألف حسنة",
            notifText = "سبحان الله",
            goal = ChallengeType.ALF_HASANA.dailyGoal,
            subtitle = BubbleSubtitle.NONE,
        )
        BubbleType.KALIMAT -> BubbleTheme(
            gradientStart = Color.parseColor("#52294A"),
            gradientEnd = Color.parseColor("#141026"),
            ringColor = Color.parseColor("#E07A9E"),
            countColor = Color.parseColor("#F2C0D2"),
            label = "الكلمات",
            name = "الكلمات الأربع",
            contentDescription = "اضغط للتسبيح",
            tooltip = "سبحان الله وبحمده",
            notifTitle = "الكلمات الأربع",
            notifText = "سبحان الله وبحمده",
            goal = ChallengeType.KALIMAT.dailyGoal,
            subtitle = BubbleSubtitle.NONE,
        )
        BubbleType.HAWQALA -> BubbleTheme(
            gradientStart = Color.parseColor("#2E1B4D"),
            gradientEnd = Color.parseColor("#0E1026"),
            ringColor = Color.parseColor("#A78BFA"),
            countColor = Color.parseColor("#C9B6FA"),
            label = "كنوز",
            name = "كنوز الجنة",
            contentDescription = "اضغط للذكر",
            tooltip = "لا حول ولا قوة إلا بالله",
            notifTitle = "كنوز الجنة",
            notifText = "لا حول ولا قوة إلا بالله",
            goal = ChallengeType.HAWQALA.dailyGoal,
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
            // The bubble is its own overlay window, so its taps bypass
            // MainActivity.dispatchTouchEvent entirely and need guarding separately —
            // otherwise it is a free route around the auto-click guard. Swallowed silently;
            // a service has no UI to warn through, and the activity warns on its own.
            TouchDiagnostics.log(event)
            if (event.isSyntheticTap()) return@setOnTouchListener true
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

        showTypePicker()
    }

    private fun dismissActionOverlay() {
        targetsVisible = false
        dismissTypePicker()
        val overlay = actionOverlayView ?: return
        overlay.animate().alpha(0f).translationY(40.dp().toFloat()).setDuration(220)
            .withEndAction {
                runCatching { windowManager.removeView(overlay) }
                actionOverlayView = null
                closeTargetView = null
                openTargetView = null
            }.start()
    }

    // ── Type-switch picker (chip row at top; drag the bubble onto a chip) ────────

    private fun showTypePicker() {
        if (typePickerView != null) return
        val types = switchableTypes()
        if (types.size < 2) return

        val (screenW, _) = screenSize()
        val rowW = screenW - 32.dp()
        // Taller row so full challenge names can wrap onto a second line.
        val rowH = 64.dp()
        val chipsTopY = 48.dp()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutDirection = android.view.View.LAYOUT_DIRECTION_LTR
            background = GradientDrawable().apply {
                setColor(Color.argb(220, 8, 18, 35))
                cornerRadius = (rowH / 2).toFloat()
                setStroke(1.dp(), Color.argb(80, 255, 215, 0))
            }
            elevation = 14.dp().toFloat()
            setPadding(8.dp(), 0, 8.dp(), 0)
        }

        chipViews.clear()
        types.forEach { type ->
            val theme = themeFor(type)
            val isCurrent = type == bubbleType
            val chip = TextView(this).apply {
                text = theme.name
                textDirection = android.view.View.TEXT_DIRECTION_RTL
                setTextColor(if (isCurrent) Color.WHITE else Color.argb(200, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(8.dp(), 4.dp(), 8.dp(), 4.dp())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 999f
                    setColor(withAlpha(theme.ringColor, if (isCurrent) 90 else 30))
                    setStroke(1.dp(), withAlpha(theme.ringColor, if (isCurrent) 220 else 90))
                }
                layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                    marginStart = 4.dp()
                    marginEnd = 4.dp()
                }
            }
            row.addView(chip)
            chipViews.add(type to chip)
        }

        row.alpha = 0f
        row.translationY = (-24).dp().toFloat()

        val params = overlayParams(
            rowW, rowH,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = chipsTopY
        }

        windowManager.addView(row, params)
        typePickerView = row

        pickerRowLeft = (screenW - rowW) / 2
        pickerRowRight = pickerRowLeft + rowW
        pickerBandBottom = chipsTopY + rowH + 24.dp()
        pickerSegWidth = if (types.isNotEmpty()) rowW / types.size else rowW

        row.animate().alpha(1f).translationY(0f).setDuration(280).start()
    }

    private fun dismissTypePicker() {
        val picker = typePickerView ?: return
        chipViews.clear()
        picker.animate().alpha(0f).translationY((-24).dp().toFloat()).setDuration(220)
            .withEndAction {
                runCatching { windowManager.removeView(picker) }
                typePickerView = null
            }.start()
    }

    // Which chip the bubble is over, or null. Uses the same geometry showTypePicker laid out.
    private fun chipHitType(rawX: Float, rawY: Float): BubbleType? {
        if (typePickerView == null || chipViews.isEmpty()) return null
        if (rawY > pickerBandBottom) return null
        if (rawX < pickerRowLeft || rawX > pickerRowRight) return null
        val index = ((rawX - pickerRowLeft) / pickerSegWidth).toInt()
            .coerceIn(0, chipViews.size - 1)
        return chipViews[index].first
    }

    private fun highlightChips(rawX: Float, rawY: Float) {
        val hit = chipHitType(rawX, rawY)
        chipViews.forEach { (type, chip) ->
            val scale = if (type == hit && type != bubbleType) 1.15f else 1f
            chip.animate().scaleX(scale).scaleY(scale).setDuration(80).start()
        }
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
        highlightChips(rawX, rawY)
    }

    private fun handleTargetRelease(rawX: Float, rawY: Float) {
        // A chip hit switches type; otherwise fall back to the close/open dock.
        val chipType = chipHitType(rawX, rawY)
        if (chipType != null) {
            dismissActionOverlay()
            clampBubble()
            windowManager.updateViewLayout(bubbleView, bubbleParams)
            switchType(chipType)
            return
        }
        when (dockHitResult(rawX, rawY)) {
            DockHit.CLOSE -> { dismissActionOverlay(); stopSelf() }
            DockHit.OPEN  -> { dismissActionOverlay(); launchApp() }
            DockHit.NONE  -> { dismissActionOverlay(); clampBubble(); windowManager.updateViewLayout(bubbleView, bubbleParams) }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

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
            BubbleType.ZABAD ->
                recordChallengeTap(zabadStore.incrementToday(today), ChallengeType.ZABAD, today)
            BubbleType.GHARS ->
                recordChallengeTap(gharsStore.incrementToday(today), ChallengeType.GHARS, today)
            BubbleType.ALF_HASANA ->
                recordChallengeTap(alfHasanaStore.incrementToday(today), ChallengeType.ALF_HASANA, today)
            BubbleType.KALIMAT ->
                recordChallengeTap(kalimatStore.incrementToday(today), ChallengeType.KALIMAT, today)
            BubbleType.HAWQALA ->
                recordChallengeTap(hawqalaStore.incrementToday(today), ChallengeType.HAWQALA, today)
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
            // A challenge bubble opens straight to its challenge screen.
            bubbleType.openChallengeAction?.let { putExtra(EXTRA_OPEN_CHALLENGE, it) }
        }
        intent?.let { startActivity(it) }
        analyticsManager.logAction(
            "bubble_open_app",
            mapOf("type" to bubbleType.id),
        )
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
        switchTooltipJob?.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        scope.cancel()
        runCatching { if (::bubbleView.isInitialized) windowManager.removeView(bubbleView) }
        actionOverlayView?.let { runCatching { windowManager.removeView(it) } }
        typePickerView?.let { runCatching { windowManager.removeView(it) } }
        tooltipView?.let { runCatching { windowManager.removeView(it) } }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
