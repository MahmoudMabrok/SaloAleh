package tools.mo3ta.salo.data.notification

import com.russhwolf.settings.Settings

class NotificationSettingsStore(private val settings: Settings) {
    var dailyEnabled: Boolean
        get() = settings.getBoolean("notif_daily_enabled", true)
        set(v) = settings.putBoolean("notif_daily_enabled", v)

    var fridayEnabled: Boolean
        get() = settings.getBoolean("notif_friday_enabled", true)
        set(v) = settings.putBoolean("notif_friday_enabled", v)
}
