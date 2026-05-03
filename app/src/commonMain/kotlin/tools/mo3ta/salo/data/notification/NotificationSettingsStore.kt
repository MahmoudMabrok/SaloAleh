package tools.mo3ta.salo.data.notification

import com.russhwolf.settings.Settings

class NotificationSettingsStore(private val settings: Settings) {
    var dailyEnabled: Boolean
        get() = settings.getBoolean(KEY_DAILY_ENABLED, true)
        set(v) = settings.putBoolean(KEY_DAILY_ENABLED, v)

    var fridayEnabled: Boolean
        get() = settings.getBoolean(KEY_FRIDAY_ENABLED, true)
        set(v) = settings.putBoolean(KEY_FRIDAY_ENABLED, v)

    private companion object {
        const val KEY_DAILY_ENABLED = "notif_daily_enabled"
        const val KEY_FRIDAY_ENABLED = "notif_friday_enabled"
    }
}
