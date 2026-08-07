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
     * Overwrite today's progress with [value]. Used only to reconcile the local count with
     * server-side evidence (the published daily badge, the daily push cap, or the day count an
     * account restore adopts) — ordinary salawat always go through [recordTap].
     */
    fun setTodayProgress(today: LocalDate, value: Int) {
        settings.putString(KEY_DATE, today.toString())
        settings.putInt(KEY_PROGRESS, value.coerceAtLeast(0))
    }

    /** Lowers today's progress to at most [max]. Returns the resulting progress. */
    fun clampTodayProgress(today: LocalDate, max: Int): Int {
        val current = todayProgress(today)
        if (current <= max) return current
        setTodayProgress(today, max)
        return max
    }

    /** Raises today's progress to at least [min]. Returns the resulting progress. */
    fun raiseTodayProgress(today: LocalDate, min: Int): Int {
        val current = todayProgress(today)
        if (current >= min) return current
        setTodayProgress(today, min)
        return min
    }

    private companion object {
        const val KEY_DATE = "daily_goal_date"
        const val KEY_PROGRESS = "daily_goal_progress"
    }
}
