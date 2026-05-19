package tools.mo3ta.salo.data.billing

object ProductRegistry {
    const val SUPPORT_APP_PREMIUM = "support_app_premium"

    private val productFeatures: Map<String, Set<PremiumFeature>> = mapOf(
        SUPPORT_APP_PREMIUM to setOf(PremiumFeature.SCORE_MASK, PremiumFeature.SUPPORTER_BADGE),
    )

    fun featuresFor(productId: String): Set<PremiumFeature> =
        productFeatures[productId] ?: emptySet()

    val allProductIds: List<String> get() = productFeatures.keys.toList()
}
