package tools.mo3ta.salo.analytics

class NoOpAnalyticsManager : AnalyticsManager {
    override fun logAction(name: String, params: Map<String, String>) = Unit
}
