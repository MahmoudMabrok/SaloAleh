package tools.mo3ta.salo.data.billing

data class SupportTier(
    val productId: String,
    val label: String,
    val defaultPrice: String,
    val emoji: String,
    val features: Set<PremiumFeature>,
)

object ProductRegistry {
    const val SUPPORT_BASIC = "support_basic"
    const val SUPPORT_PREMIUM = "support_premium"
    const val SUPPORT_GENEROUS = "support_generous"

    @Deprecated("Use tier-specific IDs", replaceWith = ReplaceWith("SUPPORT_PREMIUM"))
    const val SUPPORT_APP_PREMIUM = SUPPORT_PREMIUM

    val tiers: List<SupportTier> = listOf(
        SupportTier(SUPPORT_BASIC, "صدقة جارية", "$0.99", "☕", setOf(PremiumFeature.SUPPORTER_BADGE)),
        SupportTier(SUPPORT_PREMIUM, "نصرة الحبيب ﷺ", "$1.99", "🌟", setOf(PremiumFeature.SCORE_MASK, PremiumFeature.SUPPORTER_BADGE)),
        SupportTier(SUPPORT_GENEROUS, "سبّاق إلى الخيرات", "$4.99", "💎", setOf(PremiumFeature.SCORE_MASK, PremiumFeature.SUPPORTER_BADGE)),
    )

    private val productFeatures: Map<String, Set<PremiumFeature>> =
        tiers.associate { it.productId to it.features }

    fun featuresFor(productId: String): Set<PremiumFeature> =
        productFeatures[productId] ?: emptySet()

    val allProductIds: List<String> get() = productFeatures.keys.toList()
}
