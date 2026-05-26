package tools.mo3ta.salo.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tools.mo3ta.salo.MainActivity
import tools.mo3ta.salo.R
import tools.mo3ta.salo.data.firebase.MohamedLoversFirebaseClient
import tools.mo3ta.salo.data.session.MohamedLoversSessionStore

class SaloFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            val settings = SharedPreferencesSettings(
                getSharedPreferences("ml_session", Context.MODE_PRIVATE)
            )
            val store = MohamedLoversSessionStore(settings)
            store.saveLocalFcmToken(token)
            store.setFcmTokenSynced(false)
            val uid = store.getOrCreateUid()
            MohamedLoversFirebaseClient(store).writeFcmToken(uid, token).onSuccess {
                store.setFcmTokenSynced(true)
            }
        }
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.data["title"] ?: message.notification?.title ?: return
        val body = message.data["body"] ?: message.notification?.body ?: return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            message.data[MainActivity.EXTRA_NOTIFICATION_TYPE]?.let {
                putExtra(MainActivity.EXTRA_NOTIFICATION_TYPE, it)
            }
            message.data[MainActivity.EXTRA_NEW_VERSION]?.let {
                putExtra(MainActivity.EXTRA_NEW_VERSION, it)
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationManagerCompat.from(this).notify(
            NotificationChannels.NOTIF_ID_PUSH,
            NotificationCompat.Builder(this, NotificationChannels.CHANNEL_PUSH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        )
    }
}
