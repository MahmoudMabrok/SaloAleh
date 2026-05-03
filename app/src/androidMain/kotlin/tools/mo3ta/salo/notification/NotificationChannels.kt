package tools.mo3ta.salo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationChannels {
    const val CHANNEL_DAILY = "channel_daily"
    const val CHANNEL_RETENTION = "channel_retention"
    const val CHANNEL_FRIDAY = "channel_friday"

    const val NOTIF_ID_DAILY = 1001
    const val NOTIF_ID_RETENTION = 1002
    const val NOTIF_ID_FRIDAY = 1003

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_DAILY, "تذكير يومي", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "تذكير يومي بالصلاة على النبي" }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RETENTION, "نفتقدك", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "تنبيه عند غيابك عن التطبيق" }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_FRIDAY, "إشعارات الجمعة", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "تذكير بالصلاة على النبي كل ساعة يوم الجمعة" }
            )
        }
    }
}
