package tools.mo3ta.salo.data.ahladhikr

import com.russhwolf.settings.Settings

/**
 * Persists the last successfully-parsed snapshot JSON so the screen can still show
 * yesterday's أهل الذكر lists when the network or the published file is broken.
 * Only validated JSON reaches this cache — never a raw failed response body.
 */
class AhlAdhikrStore(private val settings: Settings) {

    var cachedJson: String?
        get() = settings.getStringOrNull(KEY_CACHE)
        set(value) {
            if (value == null) settings.remove(KEY_CACHE) else settings.putString(KEY_CACHE, value)
        }

    fun clear() {
        settings.remove(KEY_CACHE)
    }

    companion object {
        private const val KEY_CACHE = "ahl_adhikr_cache_json"
    }
}
