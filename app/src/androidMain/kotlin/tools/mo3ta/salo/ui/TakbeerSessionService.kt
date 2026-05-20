package tools.mo3ta.salo.ui

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tools.mo3ta.salo.R
import tools.mo3ta.salo.notification.NotificationChannels

/**
 * Foreground service that loops the takbeer audio continuously until the user
 * stops it — either via the in-app session button or the notification action.
 */
class TakbeerSessionService : Service() {

    companion object {
        const val ACTION_STOP = "tools.mo3ta.salo.action.STOP_TAKBEER_SESSION"
        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning
    }

    private var mediaPlayer: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NotificationChannels.NOTIF_ID_BUBBLE + 2, buildNotification())
        _isRunning.value = true

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer.create(this, R.raw.takbeer)?.apply {
                isLooping = true
                start()
            }
            if (mediaPlayer == null) {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, TakbeerSessionService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannels.CHANNEL_BUBBLE)
            .setContentTitle("جلسة التكبير")
            .setContentText("الله أكبر، الله أكبر، لا إله إلا الله")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "إيقاف", stopIntent)
            .build()
    }

    override fun onDestroy() {
        _isRunning.value = false
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
