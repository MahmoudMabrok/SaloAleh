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

    private companion object {
        const val KEY_DATE = "daily_goal_date"
        const val KEY_PROGRESS = "daily_goal_progress"
    }
}
