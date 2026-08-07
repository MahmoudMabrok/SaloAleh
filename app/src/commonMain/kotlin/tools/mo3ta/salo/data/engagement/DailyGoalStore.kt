package tools.mo3ta.salo.data.engagement

import com.russhwolf.settings.Settings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class DailyGoalStore(private val settings: Settings) {

    private val targets = mapOf(
        DayOfWeek.MONDAY to 33,
        DayOfWeek.TUESDAY to 66,
        DayOfWeek.WEDNESDAY to 100,
        DayOfWeek.THURSDAY to 133,
        DayOfWeek.FRIDAY to 200,
        DayOfWeek.SATURDAY to 33,
        DayOfWeek.SUNDAY to 33,
    )

    fun todayTarget(today: LocalDate): Int = targets[today.dayOfWeek] ?: 33

    fun recordTap(today: LocalDate, delta: Int) {
        val storedDate = settings.getStringOrNull(KEY_DATE)
        if (storedDate != today.toString()) {
            settings.putString(KEY_DATE, today.toString())
            settings.putInt(KEY_PROGRESS, 0)
        }
        settings.putInt(KEY_PROGRESS, settings.getInt(KEY_PROGRESS, 0) + delta)
    }

    fun todayProgress(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_PROGRESS, 0)
    }

    fun isGoalComplete(today: LocalDate): Boolean = todayProgress(today) >= todayTarget(today)

    /**
     * Adopt the day's count published by a restored account. This value is written to the player
     * node as an absolute `todayCount`, so without it the first tap after a restore would publish
     * a count of 1 and wipe out the day's standing on the daily leaderboard.
     */
    fun restoreProgress(today: LocalDate, progress: Int) {
        settings.putString(KEY_DATE, today.toString())
        settings.putInt(KEY_PROGRESS, progress.coerceAtLeast(0))
    }

    private companion object {
        const val KEY_DATE = "daily_goal_date"
        const val KEY_PROGRESS = "daily_goal_progress"
    }
}
