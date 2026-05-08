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

        NotificationManagerCompat.from(context).notify(
            NotificationChannels.NOTIF_ID_DAILY,
            NotificationCompat.Builder(context, NotificationChannels.CHANNEL_DAILY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("اللهم صلِّ على محمد ﷺ")
                .setContentText("تذكيرك اليومي — اضغط لتشارك الصلاة على النبي")
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build(),
        )
    }

    fun postFriday(context: Context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        NotificationManagerCompat.from(context).notify(
            NotificationChannels.NOTIF_ID_FRIDAY,
            NotificationCompat.Builder(context, NotificationChannels.CHANNEL_FRIDAY)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("اللهم صلِّ على محمد ﷺ")
                .setContentText("يوم الجمعة المبارك — صلّ على النبي الكريم")
                .setAutoCancel(true)
                .setContentIntent(openAppIntent(context))
                .build(),
        )
    }

    private fun openAppIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
