package tools.mo3ta.salo.data.billing

import com.russhwolf.settings.Settings

class PremiumStore(private val settings: Settings) {
    fun markPurchased(productId: String, purchased: Boolean = true) {
        settings.putBoolean("purchased_$productId", purchased)
    }

    fun isPurchased(productId: String): Boolean =
        settings.getBoolean("purchased_$productId", false)

    fun markSubscriptionActive(productId: String, active: Boolean) {
        settings.putBoolean("sub_active_$productId", active)
    }

    fun isSubscriptionActive(productId: String): Boolean =
        settings.getBoolean("sub_active_$productId", false)

    fun hasFeature(feature: PremiumFeature): Boolean {
        val fromOneTime = ProductRegistry.oneTimeProductIds.any { productId ->
            isPurchased(productId) && feature in ProductRegistry.featuresFor(productId)
        }
        val fromSub = ProductRegistry.subscriptionProductIds.any { productId ->
            isSubscriptionActive(productId) && feature in ProductRegistry.featuresFor(productId)
        }
        return fromOneTime || fromSub
    }

    val highestTier: SupportTier?
        get() {
            val fromOneTime = ProductRegistry.oneTimeTiers.lastOrNull { isPurchased(it.productId) }
            val fromSub = ProductRegistry.subscriptionTiers.lastOrNull { isSubscriptionActive(it.productId) }
            return fromSub ?: fromOneTime
        }

    val hasActiveSubscription: Boolean
        get() = ProductRegistry.subscriptionProductIds.any { isSubscriptionActive(it) }

    var isScoreMasked: Boolean
        get() = settings.getBoolean("score_masked", false)
        set(value) { settings.putBoolean("score_masked", value) }
}
