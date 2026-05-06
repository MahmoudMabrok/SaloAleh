package tools.mo3ta.salo.notification

import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import tools.mo3ta.salo.MainActivity
import tools.mo3ta.salo.R

class SaloFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Topic subscription handles delivery — no token storage needed
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: return
        val body = message.notification?.body ?: return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP },
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(this).notify(
            NotificationChannels.NOTIF_ID_DAILY,
            NotificationCompat.Builder(this, NotificationChannels.CHANNEL_DAILY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        )
    }
}
