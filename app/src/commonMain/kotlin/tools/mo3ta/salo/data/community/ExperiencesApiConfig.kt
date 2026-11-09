package tools.mo3ta.salo.data.community

/**
 * Android reads [tools.mo3ta.salo.BuildConfig.EXPERIENCES_API_BASE_URL].
 * iOS reads the constant in ExperiencesApiConfig.ios.kt.
 * Leave both empty until the Deno Deploy service in community-api/ is live.
 */
internal expect fun experiencesApiBaseUrl(): String

/** Origin plus `/experiences`, or null when the app has no API base URL yet. */
internal fun experiencesEndpoint(baseUrl: String): String? {
    val origin = baseUrl.trim().trimEnd('/')
    if (origin.isEmpty()) return null
    return "$origin/experiences"
}
