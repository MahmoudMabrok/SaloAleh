package tools.mo3ta.salo.data.notification

import com.russhwolf.settings.Settings

class NotificationSettingsStore(private val settings: Settings) {
    var dailyEnabled: Boolean
        get() = settings.getBoolean(KEY_DAILY_ENABLED, true)
        set(v) = settings.putBoolean(KEY_DAILY_ENABLED, v)

    var fridayEnabled: Boolean
        get() = settings.getBoolean(KEY_FRIDAY_ENABLED, true)
        set(v) = settings.putBoolean(KEY_FRIDAY_ENABLED, v)

    /** Called on first-ever app open to opt new users out of notifications by default. */
    fun initializeToOff() {
        settings.putBoolean(KEY_DAILY_ENABLED, false)
        settings.putBoolean(KEY_FRIDAY_ENABLED, false)
    }

    var showRankChip: Boolean
        get() = settings.getBoolean(KEY_SHOW_RANK_CHIP, true)
        set(v) = settings.putBoolean(KEY_SHOW_RANK_CHIP, v)

    var rankChipTooltipShown: Boolean
        get() = settings.getBoolean(KEY_RANK_CHIP_TOOLTIP_SHOWN, false)
        set(v) = settings.putBoolean(KEY_RANK_CHIP_TOOLTIP_SHOWN, v)

    private companion object {
        const val KEY_DAILY_ENABLED = "notif_daily_enabled"
        const val KEY_FRIDAY_ENABLED = "notif_friday_enabled"
        const val KEY_SHOW_RANK_CHIP = "show_rank_chip"
        const val KEY_RANK_CHIP_TOOLTIP_SHOWN = "rank_chip_tooltip_shown"
    }
}
