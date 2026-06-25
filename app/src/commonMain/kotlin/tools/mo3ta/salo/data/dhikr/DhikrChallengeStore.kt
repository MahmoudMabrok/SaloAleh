package tools.mo3ta.salo.data.dhikr

import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate

class DhikrChallengeStore(private val settings: Settings) {

    fun todayCount(today: LocalDate): Int {
        if (settings.getStringOrNull(KEY_DATE) != today.toString()) return 0
        return settings.getInt(KEY_COUNT, 0)
    }

    fun incrementToday(today: LocalDate): Int {
        ensureToday(today)
        val updated = settings.getInt(KEY_COUNT, 0) + 1
        settings.putInt(KEY_COUNT, updated)
        return updated
    }

    fun setTodayCount(today: LocalDate, count: Int): Int {
        ensureToday(today)
        val safeCount = count.coerceAtLeast(0)
        settings.putInt(KEY_COUNT, safeCount)
        return safeCount
    }

    private fun ensureToday(today: LocalDate) {
        val date = today.toString()
        if (settings.getStringOrNull(KEY_DATE) == date) return
        settings.putString(KEY_DATE, date)
        settings.putInt(KEY_COUNT, 0)
    }

    private companion object {
        const val KEY_DATE = "dhikr_challenge_date"
        const val KEY_COUNT = "dhikr_challenge_count"
    }
}
