package tools.mo3ta.salo.data.notification

import com.russhwolf.settings.Settings

class NotificationSettingsStore(private val settings: Settings) {
    var dailyEnabled: Boolean
        get() = settings.getBoolean(KEY_DAILY_ENABLED, true)
        set(v) = settings.putBoolean(KEY_DAILY_ENABLED, v)

    var fridayEnabled: Boolean
        get() = settings.getBoolean(KEY_FRIDAY_ENABLED, true)
        set(v) = settings.putBoolean(KEY_FRIDAY_ENABLED, v)

    /**
     * Controls server-sent engagement/reminder push notifications (notify-users.js):
     * day-1 lapsed, mid-week inactive, round-end recap, streak-at-risk, rival alerts,
     * and the ten-days reminders. Synced to RTDB so the server script honours it.
     */
    var serverRemindersEnabled: Boolean
        get() = settings.getBoolean(KEY_SERVER_REMINDERS_ENABLED, true)
        set(v) = settings.putBoolean(KEY_SERVER_REMINDERS_ENABLED, v)

    /**
     * Controls server-sent leaderboard/competition push notifications
     * (populate-leaderboard.js): top-3 changes, drop-out alerts, idle nudges, and the
     * ten-days leaderboard notifications. Synced to RTDB so the server script honours it.
     */
    var leaderboardNotifsEnabled: Boolean
        get() = settings.getBoolean(KEY_LEADERBOARD_NOTIFS_ENABLED, true)
        set(v) = settings.putBoolean(KEY_LEADERBOARD_NOTIFS_ENABLED, v)

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

    var bubbleTooltipShown: Boolean
        get() = settings.getBoolean(KEY_BUBBLE_TOOLTIP_SHOWN, false)
        set(v) = settings.putBoolean(KEY_BUBBLE_TOOLTIP_SHOWN, v)

    var useDailyLeaderboard: Boolean
        get() = settings.getBoolean(KEY_USE_DAILY_LEADERBOARD, true)
        set(v) = settings.putBoolean(KEY_USE_DAILY_LEADERBOARD, v)

    var dailyLeaderboardPromoShown: Boolean
        get() = settings.getBoolean(KEY_DAILY_LB_PROMO_SHOWN, false)
        set(v) = settings.putBoolean(KEY_DAILY_LB_PROMO_SHOWN, v)

    var topBarTooltipsShown: Boolean
        get() = settings.getBoolean(KEY_TOP_BAR_TOOLTIPS_SHOWN, false)
        set(v) = settings.putBoolean(KEY_TOP_BAR_TOOLTIPS_SHOWN, v)

    private companion object {
        const val KEY_DAILY_ENABLED = "notif_daily_enabled"
        const val KEY_FRIDAY_ENABLED = "notif_friday_enabled"
        const val KEY_SERVER_REMINDERS_ENABLED = "notif_server_reminders_enabled"
        const val KEY_LEADERBOARD_NOTIFS_ENABLED = "notif_leaderboard_enabled"
        const val KEY_SHOW_RANK_CHIP = "show_rank_chip"
        const val KEY_RANK_CHIP_TOOLTIP_SHOWN = "rank_chip_tooltip_shown"
        const val KEY_BUBBLE_TOOLTIP_SHOWN = "bubble_tooltip_shown"
        const val KEY_USE_DAILY_LEADERBOARD = "use_daily_leaderboard"
        const val KEY_DAILY_LB_PROMO_SHOWN = "daily_lb_promo_shown"
        const val KEY_TOP_BAR_TOOLTIPS_SHOWN = "top_bar_tooltips_shown"
    }
}
