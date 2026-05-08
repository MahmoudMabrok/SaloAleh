package tools.mo3ta.salo.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.russhwolf.settings.SharedPreferencesSettings
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import tools.mo3ta.salo.data.engagement.EngagementStore

class RetentionCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = SharedPreferencesSettings(
            applicationContext.getSharedPreferences("ml_session", Context.MODE_PRIVATE)
        )
        val store = EngagementStore(settings)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val missed = store.missedDays(today)
        if (missed < 1) return Result.success()

        AndroidReminderNotifier.postRetention(applicationContext, missed)
        return Result.success()
    }
}
