package tools.mo3ta.salo.data.billing

class NoOpBillingManager : BillingManager {
    override val isEnabled: Boolean = false
    override fun initialize() = Unit
    override fun purchaseProduct(productId: String) = Unit
    override fun restorePurchases() = Unit
    override fun isPurchased(productId: String): Boolean = false
    override fun getProductPrice(productId: String): String? = null
}
