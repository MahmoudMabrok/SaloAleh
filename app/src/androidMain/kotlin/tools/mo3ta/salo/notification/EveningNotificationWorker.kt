package tools.mo3ta.salo.notification

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class EveningNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.d("EveningWorker", "doWork() started")
        val notifEnabled = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
        Log.d("EveningWorker", "areNotificationsEnabled=$notifEnabled")
        if (!notifEnabled) {
            Log.d("EveningWorker", "notifications disabled — returning early")
            return Result.success()
        }
        Log.d("EveningWorker", "posting notification id=${NotificationChannels.NOTIF_ID_EVENING}")
        AndroidReminderNotifier.postEvening(applicationContext)
        Log.d("EveningWorker", "notification posted OK")
        return Result.success()
    }
}
