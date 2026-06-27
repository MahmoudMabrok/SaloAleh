package tools.mo3ta.salo.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import tools.mo3ta.salo.withStoredAppLocale

object NotificationChannels {
    const val CHANNEL_DAILY = "channel_daily"
    const val CHANNEL_RETENTION = "channel_retention"
    const val CHANNEL_FRIDAY = "channel_friday"
    const val CHANNEL_PUSH = "channel_push"
    const val CHANNEL_PROTECTION = "channel_protection"

    const val NOTIF_ID_DAILY = 1001
    const val NOTIF_ID_RETENTION = 1002
    const val NOTIF_ID_FRIDAY = 1003
    const val NOTIF_ID_PUSH = 1004
    const val CHANNEL_BUBBLE = "channel_bubble"
    const val NOTIF_ID_BUBBLE = 1005
    const val NOTIF_ID_PROTECTION = 1006

    fun createAll(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val localizedContext = context.withStoredAppLocale()
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
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_PUSH, "إشعارات عامة", NotificationManager.IMPORTANCE_DEFAULT)
                    .apply { description = "إشعارات من الفريق" }
            )
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_BUBBLE, "الفقاعة العائمة", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        description = "إشعار نشط أثناء استخدام الفقاعة العائمة"
                        setSound(null, null)
                        enableVibration(false)
                    }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_PROTECTION,
                    localizedContext.getString(tools.mo3ta.salo.R.string.protection_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = localizedContext.getString(tools.mo3ta.salo.R.string.protection_channel_description)
                    setSound(null, null)
                    enableVibration(false)
                }
            )
        }
    }
}
