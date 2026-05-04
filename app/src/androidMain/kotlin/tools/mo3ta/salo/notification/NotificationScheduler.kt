package tools.mo3ta.salo.notification

import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import tools.mo3ta.salo.AndroidAppContext
import java.util.concurrent.TimeUnit

actual object NotificationScheduler {

    private const val TAG_DAILY = "daily_notification"
    private const val TAG_FRIDAY = "friday_notification"
    private const val TAG_RETENTION = "retention_check"

    actual fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean) {
        Log.d("NotifScheduler", "apply() daily=$dailyEnabled friday=$fridayEnabled")
        val workManager = WorkManager.getInstance(AndroidAppContext.get())

        if (dailyEnabled) {
            workManager.enqueueUniquePeriodicWork(
                TAG_DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(1, TimeUnit.DAYS)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(TAG_DAILY)
        }

        if (fridayEnabled) {
            workManager.enqueueUniquePeriodicWork(
                TAG_FRIDAY,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<FridayNotificationWorker>(1, TimeUnit.HOURS)
                    .build(),
            )
        } else {
            workManager.cancelUniqueWork(TAG_FRIDAY)
        }

        // Retention worker always runs regardless of user settings
        workManager.enqueueUniquePeriodicWork(
            TAG_RETENTION,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build(),
        )
    }

    actual fun scheduleTest(afterSeconds: Double) {
        Log.d("NotifScheduler", "scheduleTest() afterSeconds=$afterSeconds")
        val request = OneTimeWorkRequestBuilder<DailyNotificationWorker>()
            .setInitialDelay(afterSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        val wm = WorkManager.getInstance(AndroidAppContext.get())
        wm.enqueue(request)
        Log.d("NotifScheduler", "test work enqueued id=${request.id}")
    }
}
