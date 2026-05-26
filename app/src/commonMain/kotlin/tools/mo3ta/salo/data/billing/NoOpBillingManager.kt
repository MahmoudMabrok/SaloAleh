package tools.mo3ta.salo.data.billing

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class NoOpBillingManager : BillingManager {
    override val isEnabled: Boolean = false
    override val purchaseEvents: SharedFlow<String> = MutableSharedFlow<String>().asSharedFlow()
    override val subscriptionDeactivated: SharedFlow<Unit> = MutableSharedFlow<Unit>().asSharedFlow()
    override val productPrices: StateFlow<Map<String, String>> = MutableStateFlow<Map<String, String>>(emptyMap()).asStateFlow()
    override fun initialize() = Unit
    override fun purchaseProduct(productId: String) = Unit
    override fun restorePurchases() = Unit
    override fun isPurchased(productId: String): Boolean = false
    override fun isSubscriptionActive(productId: String): Boolean = false
    override fun getProductPrice(productId: String): String? = null
}
