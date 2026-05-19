package tools.mo3ta.salo.ui

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.IBinder
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import org.koin.android.ext.android.inject
import tools.mo3ta.salo.R
import tools.mo3ta.salo.data.tendays.TenDaysStore
import tools.mo3ta.salo.notification.NotificationChannels

class TakbeerOverlayService : Service() {

    companion object {
        const val EXTRA_AUTO_REMIND = "auto_remind"
        const val EXTRA_INTERVAL_MINUTES = "interval_minutes"
        const val EXTRA_REPEAT_COUNT = "repeat_count"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
        private const val DRAG_THRESHOLD_PX = 10
        private const val DEFAULT_INTERVAL_MINUTES = 10
        private const val DEFAULT_REPEAT_COUNT = 1
    }

    private val store: TenDaysStore by inject()

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var overlayView: LinearLayout? = null
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hideJob: Job? = null
    private var autoRemind = false
    private var intervalMs = DEFAULT_INTERVAL_MINUTES * 60 * 1000L
    private var repeatCount = DEFAULT_REPEAT_COUNT

    private fun Int.dp(): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NotificationChannels.NOTIF_ID_BUBBLE + 1, buildNotification())
        autoRemind = intent?.getBooleanExtra(EXTRA_AUTO_REMIND, false) ?: false
        val minutes = intent?.getIntExtra(EXTRA_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES) ?: DEFAULT_INTERVAL_MINUTES
        intervalMs = minutes * 60 * 1000L
        repeatCount = intent?.getIntExtra(EXTRA_REPEAT_COUNT, DEFAULT_REPEAT_COUNT) ?: DEFAULT_REPEAT_COUNT
        if (overlayView == null) {
            createOverlay()
        }
        showOverlay()
        if (autoRemind) startReminderCycle()
        return START_NOT_STICKY
    }

    private fun createOverlay() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val card = TextView(this).apply {
            text = "الله أكبر، الله أكبر، الله أكبر،\nلا إله إلا الله،\nوالله أكبر، الله أكبر، الله أكبر،\nولله الحمد"
            textDirection = TextView.TEXT_DIRECTION_RTL
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(20.dp(), 14.dp(), 20.dp(), 14.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16.dp().toFloat()
                setColor(Color.argb(230, 10, 10, 31))
                setStroke(2, Color.argb(100, 251, 191, 36))
            }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }
        container.addView(card)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                topMargin = 6.dp()
            }
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#E53935"))
            }
            val size = 24.dp()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = 10.dp()
            }
        }
        btnRow.addView(closeBtn)

        val openBtn = TextView(this).apply {
            text = "فتح التطبيق"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            setPadding(12.dp(), 4.dp(), 12.dp(), 4.dp())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8.dp().toFloat()
                setColor(Color.argb(255, 251, 191, 36))
            }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT)
        }
        btnRow.addView(openBtn)

        container.addView(btnRow)

        params = WindowManager.LayoutParams(
            WRAP_CONTENT,
            WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200
        }

        setupDrag(container, closeBtn, openBtn)
        overlayView = container
    }

    private fun setupDrag(view: LinearLayout, closeBtn: TextView, openBtn: TextView) {
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f

        closeBtn.setOnClickListener { stopSelf() }
        openBtn.setOnClickListener {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(intent)
        }

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val distX = kotlin.math.abs(event.rawX - initialTouchX)
                    val distY = kotlin.math.abs(event.rawY - initialTouchY)
                    if (distX > DRAG_THRESHOLD_PX || distY > DRAG_THRESHOLD_PX) {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> true
                else -> false
            }
        }
    }

    private fun showOverlay() {
        val view = overlayView ?: return
        if (view.parent == null) {
            windowManager.addView(view, params)
        }
        view.visibility = LinearLayout.VISIBLE

        hideJob?.cancel()
        hideJob = scope.launch {
            playTakbeerRepeated(repeatCount)
            hideOverlay()
            if (!autoRemind) stopSelf()
        }
    }

    private fun hideOverlay() {
        overlayView?.visibility = LinearLayout.GONE
    }

    private fun startReminderCycle() {
        scope.launch {
            while (true) {
                delay(intervalMs)
                showOverlay()
            }
        }
    }

    private fun currentDay(): Int {
        val cairoTz = TimeZone.of("Africa/Cairo")
        val today = Clock.System.todayIn(cairoTz)
        val startDate = LocalDate.parse("2026-05-18")
        return (startDate.daysUntil(today) + 1).coerceIn(1, 9)
    }

    private suspend fun playTakbeerRepeated(times: Int) {
        repeat(times) {
            suspendCancellableCoroutine { cont ->
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer.create(this@TakbeerOverlayService, R.raw.takbeer)?.apply {
                    setOnCompletionListener {
                        it.release()
                        store.incrementTakbeerSound(currentDay())
                        if (cont.isActive) cont.resumeWith(Result.success(Unit))
                    }
                    start()
                }
                if (mediaPlayer == null && cont.isActive) {
                    cont.resumeWith(Result.success(Unit))
                }
            }
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CHANNEL_BUBBLE)
            .setContentTitle("التكبير")
            .setContentText("الله أكبر، الله أكبر، الله أكبر، لا إله إلا الله")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()

    override fun onDestroy() {
        _isRunning.value = false
        mediaPlayer?.release()
        mediaPlayer = null
        scope.cancel()
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
