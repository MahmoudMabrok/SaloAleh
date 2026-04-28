package tools.mo3ta.salo.analytics

interface AnalyticsManager {
    fun logAction(name: String, params: Map<String, String> = emptyMap())
}
