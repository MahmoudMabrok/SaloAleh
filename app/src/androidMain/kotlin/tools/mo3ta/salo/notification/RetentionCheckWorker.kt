package tools.mo3ta.salo.notification

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.R
import tools.mo3ta.salo.data.engagement.EngagementStore

class RetentionCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) {
            return Result.success()
        }
        val settings = SharedPreferencesSettings(
            applicationContext.getSharedPreferences("ml_session", Context.MODE_PRIVATE)
        )
        val store = EngagementStore(settings)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val missed = store.missedDays(today)
        if (missed < 1) return Result.success()

        val dayWord = if (missed == 1) "يوم" else "أيام"
        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.CHANNEL_RETENTION)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("نفتقدك 🤍")
            .setContentText("مضى $missed $dayWord منذ آخر زيارة — لا تنسَ الصلاة على النبي ﷺ")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext)
            .notify(NotificationChannels.NOTIF_ID_RETENTION, notification)
        return Result.success()
    }
}
