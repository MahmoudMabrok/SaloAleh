package tools.mo3ta.salo.data.security

import com.russhwolf.settings.Settings

/**
 * Remembers whether the user has already been told their taps are being injected, so the
 * auto-click warning appears once ever rather than on every detection. After that first
 * warning the guard drops injected taps silently.
 *
 * Local-only persistence via `multiplatform-settings` key `auto_click_warning_shown`.
 */
class AutoClickGuardStore(private val settings: Settings) {

    fun hasWarned(): Boolean = settings.getBoolean(KEY_WARNED, false)

    fun markWarned() {
        settings.putBoolean(KEY_WARNED, true)
    }

    private companion object {
        const val KEY_WARNED = "auto_click_warning_shown"
    }
}
