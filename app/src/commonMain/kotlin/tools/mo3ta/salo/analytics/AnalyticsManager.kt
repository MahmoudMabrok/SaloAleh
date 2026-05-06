package tools.mo3ta.salo.analytics

interface AnalyticsManager {
    fun logAction(name: String, params: Map<String, String> = emptyMap())
    fun logView(name: String, params: Map<String, String> = emptyMap())

}
