package tools.mo3ta.salo.data.reminder

import com.russhwolf.settings.Settings
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Gates the Friday "read Surah Al-Kahf" reminder dialog so it appears at most once
 * per day, and only on Fridays (Cairo timezone — the caller passes today's Cairo date).
 *
 * Reading Surah Al-Kahf on Friday is a well-known sunnah; this nudges the user each Friday.
 *
 * Local-only persistence via `multiplatform-settings` key `alkahf_reminder_last_shown`,
 * storing the ISO date (`YYYY-MM-DD`) the reminder was last shown.
 */
class AlKahfReminderStore(private val settings: Settings) {

    fun shouldShow(today: LocalDate): Boolean =
        today.dayOfWeek == DayOfWeek.FRIDAY &&
            settings.getStringOrNull(KEY_LAST_SHOWN) != today.toString()

    fun markShown(today: LocalDate) {
        settings.putString(KEY_LAST_SHOWN, today.toString())
    }

    private companion object {
        const val KEY_LAST_SHOWN = "alkahf_reminder_last_shown"
    }
}
