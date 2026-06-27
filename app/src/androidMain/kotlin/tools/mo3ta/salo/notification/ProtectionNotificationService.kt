package tools.mo3ta.salo.notification

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tools.mo3ta.salo.R
import tools.mo3ta.salo.withStoredAppLocale

class ProtectionNotificationService : Service() {

    companion object {
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private const val NOON_HOUR = 12
        private const val EVENING_CUTOFF_HOUR = 18
        private const val MORNING_CUTOFF_HOUR = 6
    }

    private val handler = Handler(Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSelf() }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withStoredAppLocale())
    }

    override fun onCreate() {
        super.onCreate()
        _isRunning.value = true
        NotificationChannels.createAll(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(stopRunnable)

        val protectionWindow = currentProtectionWindow()
        startForeground(
            NotificationChannels.NOTIF_ID_PROTECTION,
            buildNotification(protectionWindow.messageRes),
        )
        handler.postDelayed(stopRunnable, protectionWindow.msUntilCutoff)

        return START_NOT_STICKY
    }

    private fun currentProtectionWindow(): ProtectionWindow {
        val now = Calendar.getInstance()
        val beforeNoon = now.get(Calendar.HOUR_OF_DAY) < NOON_HOUR
        val cutoff = now.clone() as Calendar
        val messageRes: Int

        if (beforeNoon) {
            messageRes = R.string.protection_till_evening
            cutoff.set(Calendar.HOUR_OF_DAY, EVENING_CUTOFF_HOUR)
        } else {
            messageRes = R.string.protection_till_morning
            cutoff.add(Calendar.DAY_OF_YEAR, 1)
            cutoff.set(Calendar.HOUR_OF_DAY, MORNING_CUTOFF_HOUR)
        }

        cutoff.set(Calendar.MINUTE, 0)
        cutoff.set(Calendar.SECOND, 0)
        cutoff.set(Calendar.MILLISECOND, 0)

        return ProtectionWindow(
            messageRes = messageRes,
            msUntilCutoff = (cutoff.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L),
        )
    }

    private fun buildNotification(messageRes: Int): Notification =
        NotificationCompat.Builder(this, NotificationChannels.CHANNEL_PROTECTION)
            .setContentTitle(getString(R.string.protection_notification_title))
            .setContentText(getString(messageRes))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    override fun onDestroy() {
        handler.removeCallbacks(stopRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE)
        _isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private data class ProtectionWindow(
    val messageRes: Int,
    val msUntilCutoff: Long,
)
