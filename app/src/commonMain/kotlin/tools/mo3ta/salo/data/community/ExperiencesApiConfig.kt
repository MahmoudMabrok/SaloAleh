package tools.mo3ta.salo.data.community

import tools.mo3ta.salo.domain.ExperiencesException
import tools.mo3ta.salo.domain.ExperiencesFailure

/**
 * Firebase Remote Config parameter on project kamapp-3b3ac.
 * The value is the Deno Deploy origin, for example `https://saloaleh-community.deno.dev`.
 */
internal const val EXPERIENCES_API_BASE_URL_KEY = "experiences_api_base_url"

private val ORIGIN = Regex("""https?://[A-Za-z0-9.-]+(?::\d+)?""")

/**
 * Debug builds may override the origin locally. Release builds leave [debugOverride] empty
 * and fetch [fetchImmediately] false so production always uses Remote Config.
 */
internal data class ExperiencesApiLocalConfig(
    val debugOverride: String,
    val fetchImmediately: Boolean,
)

internal expect fun experiencesApiLocalConfig(): ExperiencesApiLocalConfig

/** Accept an http(s) origin with no path. Anything else, including a blank value, is unusable. */
internal fun sanitizeOrigin(raw: String): String {
    val value = raw.trim().trimEnd('/')
    if (value.isEmpty() || !ORIGIN.matches(value)) return ""
    return value
}

/**
 * A fetch that failed and left no cached origin is an outage. A successful fetch of a
 * blank or illegal value means the parameter is not set.
 */
internal fun originAfterFetch(fetchSucceeded: Boolean, rawValue: String): String {
    val sanitized = sanitizeOrigin(rawValue)
    if (!fetchSucceeded && sanitized.isEmpty()) {
        throw ExperiencesException(ExperiencesFailure.Unavailable)
    }
    return sanitized
}

/** Origin plus `/experiences`, or null when the app has no API base URL yet. */
internal fun experiencesEndpoint(baseUrl: String): String? {
    val origin = sanitizeOrigin(baseUrl)
    if (origin.isEmpty()) return null
    return "$origin/experiences"
}
