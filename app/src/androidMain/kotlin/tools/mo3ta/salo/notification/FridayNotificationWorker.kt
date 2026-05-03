package tools.mo3ta.salo.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import tools.mo3ta.salo.R

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

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_FRIDAY)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("اللهم صلِّ على محمد ﷺ")
            .setContentText("يوم الجمعة المبارك — صلّ على النبي الكريم")
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(applicationContext)
            .notify(NotificationChannels.NOTIF_ID_FRIDAY, notification)

        return Result.success()
    }
}
