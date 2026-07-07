package tools.mo3ta.salo.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tools.mo3ta.salo.MainActivity
import tools.mo3ta.salo.R

object AndroidReminderNotifier {

    fun postDaily(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val title = "اللهم صلِّ على محمد ﷺ"
        val body = "تذكيرك اليومي — اضغط لتشارك الصلاة على النبي"
        NotificationManagerCompat.from(context).notify(
            NotificationChannels.NOTIF_ID_DAILY,
            NotificationCompat.Builder(context, NotificationChannels.CHANNEL_DAILY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context, NotificationChannels.NOTIF_ID_DAILY, title, body))
                .build(),
        )
    }

    fun postFriday(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val title = "اللهم صلِّ على محمد ﷺ"
        val body = "يوم الجمعة، هو يوم الصلاة علي النبي، عطر فمك بالصلاة ﷺ"
        NotificationManagerCompat.from(context).notify(
            NotificationChannels.NOTIF_ID_FRIDAY,
            NotificationCompat.Builder(context, NotificationChannels.CHANNEL_FRIDAY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context, NotificationChannels.NOTIF_ID_FRIDAY, title, body))
                .build(),
        )
    }

    fun postRetention(context: Context, missedDays: Int) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val dayWord = if (missedDays == 1) "يوم" else "أيام"
        val title = "نفتقدك 🤍"
        val body = "مضى $missedDays $dayWord منذ آخر زيارة — لا تنسَ الصلاة على النبي ﷺ"
        NotificationManagerCompat.from(context).notify(
            NotificationChannels.NOTIF_ID_RETENTION,
            NotificationCompat.Builder(context, NotificationChannels.CHANNEL_RETENTION)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context, NotificationChannels.NOTIF_ID_RETENTION, title, body))
                .build(),
        )
    }

    private fun openAppIntent(context: Context, requestCode: Int, title: String, body: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_NOTIF_TITLE, title)
                putExtra(MainActivity.EXTRA_NOTIF_BODY, body)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
