package tools.mo3ta.salo.analytics

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

class FirebaseAnalyticsManager(private val context: Context) : AnalyticsManager {
    private val analytics: FirebaseAnalytics by lazy { FirebaseAnalytics.getInstance(context) }

    override fun logAction(name: String, params: Map<String, String>) {
        analytics.logEvent(name) {
            params.forEach { (key, value) -> param(key, value) }
        }
    }
}
