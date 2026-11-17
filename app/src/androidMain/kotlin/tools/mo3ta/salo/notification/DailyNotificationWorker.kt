package tools.mo3ta.salo.notification

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class DailyNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("DailyWorker", "doWork() started")
        return try {
            val notifEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
            Log.d("DailyWorker", "areNotificationsEnabled=$notifEnabled")
            val result = if (!notifEnabled) {
                Log.d("DailyWorker", "notifications disabled — returning early")
                Result.success()
            } else {
                Log.d("DailyWorker", "posting notification id=${NotificationChannels.NOTIF_ID_DAILY}")
                AndroidReminderNotifier.postDaily(applicationContext)
                Log.d("DailyWorker", "notification posted OK")
                Result.success()
            }
            result
        } finally {
            NotificationScheduler.rescheduleDailyAfterTrigger(applicationContext)
        }
    }
}
