package tools.mo3ta.salo.data.billing

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BillingManager {
    val isEnabled: Boolean
    val purchaseEvents: SharedFlow<String>
    val subscriptionDeactivated: SharedFlow<Unit>
    val supporterRestored: SharedFlow<Boolean>
    val productPrices: StateFlow<Map<String, String>>
    fun initialize()
    fun purchaseProduct(productId: String)
    fun restorePurchases()
    fun isPurchased(productId: String): Boolean
    fun isSubscriptionActive(productId: String): Boolean
    fun getProductPrice(productId: String): String?
}
