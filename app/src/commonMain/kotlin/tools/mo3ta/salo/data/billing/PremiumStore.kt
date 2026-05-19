package tools.mo3ta.salo.data.billing

import com.russhwolf.settings.Settings

class PremiumStore(private val settings: Settings) {
    fun markPurchased(productId: String) {
        settings.putBoolean("purchased_$productId", true)
    }

    fun isPurchased(productId: String): Boolean =
        settings.getBoolean("purchased_$productId", false)

    fun hasFeature(feature: PremiumFeature): Boolean =
        ProductRegistry.allProductIds.any { productId ->
            isPurchased(productId) && feature in ProductRegistry.featuresFor(productId)
        }

    val highestTier: SupportTier?
        get() = ProductRegistry.tiers.lastOrNull { isPurchased(it.productId) }

    var isScoreMasked: Boolean
        get() = settings.getBoolean("score_masked", false)
        set(value) { settings.putBoolean("score_masked", value) }
}
