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

    /** Round key for which score masking is currently active, or null when masking is off. */
    var scoreMaskedRoundKey: String?
        get() = settings.getStringOrNull("score_masked_round")
        set(value) {
            if (value == null) settings.remove("score_masked_round")
            else settings.putString("score_masked_round", value)
        }

    /**
     * Score masking is scoped to a single round. When a new round starts (the active round key
     * differs from the one masking was enabled for), the mask is cleared so the player must opt in
     * again for the new round. Called on every bootstrap with the current round key.
     */
    fun clearScoreMaskOnNewRound(currentRoundKey: String) {
        if (currentRoundKey.isBlank()) return
        if (!isScoreMasked) {
            scoreMaskedRoundKey = null
            return
        }
        val maskedRound = scoreMaskedRoundKey
        if (maskedRound == null) {
            // Masking was enabled before round tracking existed (or in this round) — adopt the
            // active round so it is cleared once the next round begins.
            scoreMaskedRoundKey = currentRoundKey
        } else if (maskedRound != currentRoundKey) {
            isScoreMasked = false
            scoreMaskedRoundKey = null
        }
    }
}
