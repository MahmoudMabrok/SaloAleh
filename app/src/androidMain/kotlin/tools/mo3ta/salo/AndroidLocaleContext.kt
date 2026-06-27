package tools.mo3ta.salo

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

private const val PREFS_NAME = "ml_session"
private const val KEY_LANGUAGE = "app_language"
private const val DEFAULT_LANGUAGE = "ar"

fun Context.withStoredAppLocale(): Context {
    val languageTag = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_LANGUAGE, DEFAULT_LANGUAGE)
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_LANGUAGE
    val locale = Locale.forLanguageTag(languageTag)
    Locale.setDefault(locale)
    val config = Configuration(resources.configuration).apply {
        setLocale(locale)
        setLayoutDirection(locale)
    }
    return createConfigurationContext(config)
}
