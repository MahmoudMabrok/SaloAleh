package tools.mo3ta.salo.notification

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import tools.mo3ta.salo.AndroidAppContext
import java.util.concurrent.TimeUnit

actual object NotificationScheduler {

    private const val TAG_DAILY = "daily_notification"
    private const val TAG_FRIDAY = "friday_notification"
    private const val TAG_RETENTION = "retention_check"

    actual fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean) {
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
}
