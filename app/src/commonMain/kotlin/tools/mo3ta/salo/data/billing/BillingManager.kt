package tools.mo3ta.salo.data.billing

interface BillingManager {
    val isEnabled: Boolean
    fun initialize()
    fun purchaseProduct(productId: String)
    fun restorePurchases()
    fun isPurchased(productId: String): Boolean
    fun getProductPrice(productId: String): String?
}
