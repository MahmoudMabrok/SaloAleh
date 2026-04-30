package tools.mo3ta.salo.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tools.mo3ta.salo.R

class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_DAILY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("اللهم صلِّ على محمد ﷺ")
            .setContentText("تذكيرك اليومي — اضغط لتشارك الصلاة على النبي")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(NotificationChannels.NOTIF_ID_DAILY, notification)
        return Result.success()
    }
}
