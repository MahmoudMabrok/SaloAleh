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
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }

        val cairoTz = TimeZone.of("Africa/Cairo")
        val now = Clock.System.now().toLocalDateTime(cairoTz)

        if (now.dayOfWeek != DayOfWeek.FRIDAY) return Result.success()
        if (now.hour !in 9..17) return Result.success()

        AndroidReminderNotifier.postFriday(applicationContext)

        return Result.success()
    }
}
