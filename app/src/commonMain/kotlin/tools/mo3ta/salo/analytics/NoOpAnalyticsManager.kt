package tools.mo3ta.salo.analytics

class NoOpAnalyticsManager : AnalyticsManager {
    override fun setUserId(uid: String) = Unit
    override fun logAction(name: String, params: Map<String, String>) = Unit
    override fun logView(name: String, params: Map<String, String>) = Unit
}
