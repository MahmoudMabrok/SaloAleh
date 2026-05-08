package tools.mo3ta.salo.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.russhwolf.settings.SharedPreferencesSettings
import tools.mo3ta.salo.AndroidAppContext
import tools.mo3ta.salo.data.notification.NotificationSettingsStore
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

actual object NotificationScheduler {

    private const val TAG = "NotifScheduler"
    private const val TAG_DAILY = "daily_notification"
    private const val TAG_FRIDAY = "friday_notification"
    private const val TAG_RETENTION = "retention_check"
    internal const val ACTION_DAILY_EXACT = "tools.mo3ta.salo.notification.ACTION_DAILY_EXACT"
    internal const val ACTION_FRIDAY_EXACT = "tools.mo3ta.salo.notification.ACTION_FRIDAY_EXACT"
    private const val REQUEST_DAILY_EXACT = 2001
    private const val REQUEST_FRIDAY_EXACT = 2002
    private val cairoZone: ZoneId = ZoneId.of("Africa/Cairo")

    actual fun apply(dailyEnabled: Boolean, fridayEnabled: Boolean) {
        applyInternal(AndroidAppContext.get(), dailyEnabled, fridayEnabled)
    }

    actual fun scheduleTest(afterSeconds: Double) {
        Log.d(TAG, "scheduleTest() afterSeconds=$afterSeconds")
        val request = OneTimeWorkRequestBuilder<DailyNotificationWorker>()
            .setInitialDelay(afterSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        val wm = WorkManager.getInstance(AndroidAppContext.get())
        wm.enqueue(request)
        Log.d(TAG, "test work enqueued id=${request.id}")
    }

    internal fun reapplyStoredSettings(context: Context) {
        val store = NotificationSettingsStore(
            SharedPreferencesSettings(
                context.getSharedPreferences("ml_session", Context.MODE_PRIVATE),
            ),
        )
        applyInternal(context, store.dailyEnabled, store.fridayEnabled)
    }

    internal fun onExactAlarmTriggered(context: Context, action: String?) {
        when (action) {
            ACTION_DAILY_EXACT -> {
                AndroidReminderNotifier.postDaily(context)
                rescheduleDailyAfterTrigger(context)
            }

            ACTION_FRIDAY_EXACT -> {
                AndroidReminderNotifier.postFriday(context)
                rescheduleFridayAfterTrigger(context)
            }
        }
    }

    private fun applyInternal(context: Context, dailyEnabled: Boolean, fridayEnabled: Boolean) {
        Log.d(TAG, "apply() daily=$dailyEnabled friday=$fridayEnabled")
        val workManager = WorkManager.getInstance(context)

        cancelApproximateReminderWork(workManager)
        cancelExactReminderAlarms(context)

        if (dailyEnabled) {
            scheduleDailyReminder(context, workManager)
        }

        if (fridayEnabled) {
            scheduleFridayReminder(context, workManager)
        }

        workManager.enqueueUniquePeriodicWork(
            TAG_RETENTION,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RetentionCheckWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build(),
        )
    }

    private fun scheduleDailyReminder(context: Context, workManager: WorkManager) {
        if (canScheduleExactAlarms(context)) {
            scheduleExactAlarm(
                context = context,
                action = ACTION_DAILY_EXACT,
                requestCode = REQUEST_DAILY_EXACT,
                triggerAtMillis = nextDailyTriggerAtMillis(),
            )
            Log.d(TAG, "daily exact alarm scheduled")
            return
        }

        workManager.enqueueUniquePeriodicWork(
            TAG_DAILY,
            ExistingPeriodicWorkPolicy.REPLACE,
            PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(delayUntilNextDailyMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
        Log.d(TAG, "daily fallback work scheduled")
    }

    private fun scheduleFridayReminder(context: Context, workManager: WorkManager) {
        if (canScheduleExactAlarms(context)) {
            scheduleExactAlarm(
                context = context,
                action = ACTION_FRIDAY_EXACT,
                requestCode = REQUEST_FRIDAY_EXACT,
                triggerAtMillis = nextFridayTriggerAtMillis(),
            )
            Log.d(TAG, "friday exact alarm scheduled")
            return
        }

        workManager.enqueueUniquePeriodicWork(
            TAG_FRIDAY,
            ExistingPeriodicWorkPolicy.REPLACE,
            PeriodicWorkRequestBuilder<FridayNotificationWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(delayUntilNextFridayMillis(), TimeUnit.MILLISECONDS)
                .build(),
        )
        Log.d(TAG, "friday fallback work scheduled")
    }

    private fun rescheduleDailyAfterTrigger(context: Context) {
        val store = currentSettings(context)
        if (!store.dailyEnabled) {
            cancelExactAlarm(context, ACTION_DAILY_EXACT, REQUEST_DAILY_EXACT)
            return
        }

        if (canScheduleExactAlarms(context)) {
            scheduleExactAlarm(
                context = context,
                action = ACTION_DAILY_EXACT,
                requestCode = REQUEST_DAILY_EXACT,
                triggerAtMillis = nextDailyTriggerAtMillis(),
            )
        } else {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG_DAILY,
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequestBuilder<DailyNotificationWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayUntilNextDailyMillis(), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }
    }

    private fun rescheduleFridayAfterTrigger(context: Context) {
        val store = currentSettings(context)
        if (!store.fridayEnabled) {
            cancelExactAlarm(context, ACTION_FRIDAY_EXACT, REQUEST_FRIDAY_EXACT)
            return
        }

        if (canScheduleExactAlarms(context)) {
            scheduleExactAlarm(
                context = context,
                action = ACTION_FRIDAY_EXACT,
                requestCode = REQUEST_FRIDAY_EXACT,
                triggerAtMillis = nextFridayTriggerAtMillis(),
            )
        } else {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                TAG_FRIDAY,
                ExistingPeriodicWorkPolicy.REPLACE,
                PeriodicWorkRequestBuilder<FridayNotificationWorker>(1, TimeUnit.HOURS)
                    .setInitialDelay(delayUntilNextFridayMillis(), TimeUnit.MILLISECONDS)
                    .build(),
            )
        }
    }

    private fun cancelApproximateReminderWork(workManager: WorkManager) {
        workManager.cancelUniqueWork(TAG_DAILY)
        workManager.cancelUniqueWork(TAG_FRIDAY)
    }

    private fun cancelExactReminderAlarms(context: Context) {
        cancelExactAlarm(context, ACTION_DAILY_EXACT, REQUEST_DAILY_EXACT)
        cancelExactAlarm(context, ACTION_FRIDAY_EXACT, REQUEST_FRIDAY_EXACT)
    }

    private fun cancelExactAlarm(context: Context, action: String, requestCode: Int) {
        val pendingIntent = reminderPendingIntent(context, action, requestCode)
        context.getSystemService<AlarmManager>()
            ?.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun scheduleExactAlarm(
        context: Context,
        action: String,
        requestCode: Int,
        triggerAtMillis: Long,
    ) {
        val alarmManager = context.getSystemService<AlarmManager>() ?: return
        val pendingIntent = reminderPendingIntent(context, action, requestCode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarm access missing for action=$action")
            return
        }
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent,
        )
    }

    private fun reminderPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, NotificationAlarmReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun currentSettings(context: Context): NotificationSettingsStore =
        NotificationSettingsStore(
            SharedPreferencesSettings(
                context.getSharedPreferences("ml_session", Context.MODE_PRIVATE),
            ),
        )

    private fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService<AlarmManager>()?.canScheduleExactAlarms() == true
    }

    private fun delayUntilNextDailyMillis(): Long {
        val now = ZonedDateTime.now(cairoZone)
        return Duration.between(now, nextDailyTrigger(now)).toMillis().coerceAtLeast(1L)
    }

    private fun delayUntilNextFridayMillis(): Long {
        val now = ZonedDateTime.now(cairoZone)
        return Duration.between(now, nextFridayTrigger(now)).toMillis().coerceAtLeast(1L)
    }

    private fun nextDailyTriggerAtMillis(): Long =
        nextDailyTrigger(ZonedDateTime.now(cairoZone)).toInstant().toEpochMilli()

    private fun nextFridayTriggerAtMillis(): Long =
        nextFridayTrigger(ZonedDateTime.now(cairoZone)).toInstant().toEpochMilli()

    private fun nextDailyTrigger(now: ZonedDateTime): ZonedDateTime {
        val candidate = now
            .withHour(9)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
        return if (candidate.isAfter(now)) candidate else candidate.plusDays(1)
    }

    private fun nextFridayTrigger(now: ZonedDateTime): ZonedDateTime {
        if (now.dayOfWeek == DayOfWeek.FRIDAY) {
            for (hour in 9..17) {
                val candidate = now
                    .withHour(hour)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0)
                if (candidate.isAfter(now)) return candidate
            }
        }

        val daysUntilFriday = ((DayOfWeek.FRIDAY.value - now.dayOfWeek.value + 7) % 7).let {
            if (it == 0) 7 else it
        }
        return now
            .plusDays(daysUntilFriday.toLong())
            .withHour(9)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
    }
}
