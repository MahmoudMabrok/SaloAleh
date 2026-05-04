package tools.mo3ta.salo.notification

import android.content.Context
import android.util.Log
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
        Log.d("DailyWorker", "doWork() started")
        val notifEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        Log.d("DailyWorker", "areNotificationsEnabled=$notifEnabled")
        if (!notifEnabled) {
            Log.d("DailyWorker", "notifications disabled — returning early")
            return Result.success()
        }
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_DAILY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("اللهم صلِّ على محمد ﷺ")
            .setContentText("تذكيرك اليومي — اضغط لتشارك الصلاة على النبي")
            .setAutoCancel(true)
            .build()
        Log.d("DailyWorker", "posting notification id=${NotificationChannels.NOTIF_ID_DAILY}")
        NotificationManagerCompat.from(applicationContext)
            .notify(NotificationChannels.NOTIF_ID_DAILY, notification)
        Log.d("DailyWorker", "notification posted OK")
        return Result.success()
    }
}
