package tools.mo3ta.salo.data.language

import com.russhwolf.settings.Settings

class LanguageStore(private val settings: Settings) {
    var language: String
        get() = settings.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE)
        set(v) = settings.putString(KEY_LANGUAGE, v)

    private companion object {
        const val KEY_LANGUAGE = "app_language"
        const val DEFAULT_LANGUAGE = "ar"
    }
}
