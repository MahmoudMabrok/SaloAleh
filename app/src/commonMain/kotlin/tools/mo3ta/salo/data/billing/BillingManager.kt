package tools.mo3ta.salo.data.billing

import kotlinx.coroutines.flow.SharedFlow

interface BillingManager {
    val isEnabled: Boolean
    val purchaseEvents: SharedFlow<String>
    val subscriptionDeactivated: SharedFlow<Unit>
    fun initialize()
    fun purchaseProduct(productId: String)
    fun restorePurchases()
    fun isPurchased(productId: String): Boolean
    fun isSubscriptionActive(productId: String): Boolean
    fun getProductPrice(productId: String): String?
}
