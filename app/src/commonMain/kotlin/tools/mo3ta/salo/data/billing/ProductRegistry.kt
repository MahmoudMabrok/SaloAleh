package tools.mo3ta.salo.data.billing

enum class ProductType {
    ONE_TIME,
    SUBSCRIPTION,
}

data class SupportTier(
    val productId: String,
    val label: String,
    val defaultPrice: String,
    val emoji: String,
    val features: Set<PremiumFeature>,
    val type: ProductType = ProductType.ONE_TIME,
    val period: SubscriptionPeriod? = null,
)

enum class SubscriptionPeriod {
    MONTHLY,
    YEARLY,
}

object ProductRegistry {
    const val SUPPORT_BASIC = "support_basic"
    const val SUPPORT_PREMIUM = "support_premium"
    const val SUPPORT_GENEROUS = "support_generous"

    const val SUB_MONTHLY_BASIC = "1_month_basic"
    const val SUB_MONTHLY_PREMIUM = "generous_month"
    const val SUB_YEARLY_PREMIUM = "mohamed_lovers"

    @Deprecated("Use tier-specific IDs", replaceWith = ReplaceWith("SUPPORT_PREMIUM"))
    const val SUPPORT_APP_PREMIUM = SUPPORT_PREMIUM

    private val basicFeatures: Set<PremiumFeature> = setOf(
        PremiumFeature.SUPPORTER_BADGE,
        PremiumFeature.FRIDAY_SCORES,
        PremiumFeature.OTHERS_ACHIEVEMENTS,
    )

    private val premiumFeatures: Set<PremiumFeature> = basicFeatures + setOf(
        PremiumFeature.LIVE_LEADERBOARD,
    )

    val oneTimeTiers: List<SupportTier> = listOf(
        SupportTier(SUPPORT_BASIC, "صدقة جارية", "$0.99", "☕", basicFeatures),
        SupportTier(SUPPORT_PREMIUM, "نصرة الحبيب ﷺ", "$1.99", "🌟", premiumFeatures),
        SupportTier(SUPPORT_GENEROUS, "سبّاق إلى الخيرات", "$4.99", "💎", premiumFeatures),
    )

    val subscriptionTiers: List<SupportTier> = listOf(
        SupportTier(
            SUB_MONTHLY_BASIC, "داعم شهري", "$0.99", "🌙",
            basicFeatures,
            ProductType.SUBSCRIPTION, SubscriptionPeriod.MONTHLY,
        ),
        SupportTier(
            SUB_MONTHLY_PREMIUM, "داعم مميز شهري", "$2.99", "⭐",
            premiumFeatures,
            ProductType.SUBSCRIPTION, SubscriptionPeriod.MONTHLY,
        ),
        SupportTier(
            SUB_YEARLY_PREMIUM, "داعم مميز سنوي", "$24.99", "💎",
            premiumFeatures,
            ProductType.SUBSCRIPTION, SubscriptionPeriod.YEARLY,
        ),
    )

    val tiers: List<SupportTier> = oneTimeTiers + subscriptionTiers

    private val productFeatures: Map<String, Set<PremiumFeature>> =
        tiers.associate { it.productId to it.features }

    private val productTypes: Map<String, ProductType> =
        tiers.associate { it.productId to it.type }

    fun featuresFor(productId: String): Set<PremiumFeature> =
        productFeatures[productId] ?: emptySet()

    fun typeFor(productId: String): ProductType =
        productTypes[productId] ?: ProductType.ONE_TIME

    val allProductIds: List<String> get() = productFeatures.keys.toList()

    val oneTimeProductIds: List<String> get() = oneTimeTiers.map { it.productId }

    val subscriptionProductIds: List<String> get() = subscriptionTiers.map { it.productId }
}
