package tools.mo3ta.salo.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class FridayNotificationWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val cairoTz = TimeZone.of("Africa/Cairo")
            val now = Clock.System.now().toLocalDateTime(cairoTz)
            val result = when {
                !NotificationManagerCompat.from(applicationContext).areNotificationsEnabled() -> Result.success()
                now.dayOfWeek != DayOfWeek.FRIDAY -> Result.success()
                now.hour !in 9..16 -> Result.success()
                else -> {
                    // Round closes Friday 17:00 Cairo — last reminder hour is 16:00.
                    AndroidReminderNotifier.postFriday(applicationContext)
                    Result.success()
                }
            }
            result
        } finally {
            NotificationScheduler.rescheduleFridayAfterTrigger(applicationContext)
        }
    }
}
