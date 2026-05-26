package tools.mo3ta.salo.data.billing

import android.app.Activity
import co.touchlab.kermit.Logger
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import tools.mo3ta.salo.billing.SaloBillingClient

class AndroidBillingManager(
    private val billingClient: SaloBillingClient,
    private val premiumStore: PremiumStore,
) : BillingManager {

    private val log = Logger.withTag("AndroidBillingManager")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _productPrices = MutableStateFlow<Map<String, String>>(emptyMap())
    override val productPrices: StateFlow<Map<String, String>> = _productPrices.asStateFlow()

    override val isEnabled: Boolean = true

    private val _purchaseEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val purchaseEvents: SharedFlow<String> = _purchaseEvents.asSharedFlow()

    private val _subscriptionDeactivated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val subscriptionDeactivated: SharedFlow<Unit> = _subscriptionDeactivated.asSharedFlow()

    private val _supporterRestored = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    override val supporterRestored: SharedFlow<Boolean> = _supporterRestored.asSharedFlow()

    private var currentActivity: Activity? = null

    override fun initialize() {
        billingClient.onPurchaseCompleted = { productIds ->
            for (productId in productIds) {
                if (productId in ProductRegistry.allProductIds) {
                    val isSub = ProductRegistry.typeFor(productId) == ProductType.SUBSCRIPTION
                    if (isSub) {
                        val wasActive = premiumStore.isSubscriptionActive(productId)
                        premiumStore.markSubscriptionActive(productId, true)
                        log.d { "Subscription activated: $productId (new=${!wasActive})" }
                        if (!wasActive) {
                            _purchaseEvents.tryEmit(productId)
                        }
                    } else {
                        val wasAlreadyPurchased = premiumStore.isPurchased(productId)
                        premiumStore.markPurchased(productId)
                        log.d { "Purchase confirmed: $productId (new=${!wasAlreadyPurchased})" }
                        if (!wasAlreadyPurchased) {
                            _purchaseEvents.tryEmit(productId)
                        }
                    }
                }
            }
        }
        billingClient.connect()
        scope.launch {
            billingClient.isConnected.collect { connected ->
                if (connected) {
                    loadProductPrices()
                    restorePurchasesInternal()
                    refreshSubscriptionState()
                    _supporterRestored.tryEmit(premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE))
                }
            }
        }
    }

    fun setActivity(activity: Activity?) {
        currentActivity = activity
    }

    override fun purchaseProduct(productId: String) {
        val activity = currentActivity ?: run {
            log.w { "No activity set — cannot launch billing flow" }
            return
        }
        val isSub = ProductRegistry.typeFor(productId) == ProductType.SUBSCRIPTION
        val productType = if (isSub) BillingClient.ProductType.SUBS else BillingClient.ProductType.INAPP
        billingClient.launchBillingFlow(activity, productId, productType)
    }

    override fun restorePurchases() {
        scope.launch {
            restorePurchasesInternal()
            refreshSubscriptionState()
            _supporterRestored.tryEmit(premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE))
        }
    }

    override fun isPurchased(productId: String): Boolean =
        premiumStore.isPurchased(productId)

    override fun isSubscriptionActive(productId: String): Boolean =
        premiumStore.isSubscriptionActive(productId)

    override fun getProductPrice(productId: String): String? =
        _productPrices.value[productId]

    private suspend fun loadProductPrices() {
        val prices = _productPrices.value.toMutableMap()
        for (productId in ProductRegistry.oneTimeProductIds) {
            val result = billingClient.queryProductDetails(productId, BillingClient.ProductType.INAPP)
            val details = result.productDetailsList?.firstOrNull() ?: continue
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: continue
            prices[productId] = price
            log.d { "Price loaded: $productId = $price" }
        }
        for (productId in ProductRegistry.subscriptionProductIds) {
            val result = billingClient.queryProductDetails(productId, BillingClient.ProductType.SUBS)
            val details = result.productDetailsList?.firstOrNull() ?: continue
            val price = details.subscriptionOfferDetails
                ?.firstOrNull()
                ?.pricingPhases
                ?.pricingPhaseList
                ?.firstOrNull()
                ?.formattedPrice ?: continue
            prices[productId] = price
            log.d { "Sub price loaded: $productId = $price" }
        }
        _productPrices.value = prices
    }

    private suspend fun restorePurchasesInternal() {
        val hadSupporterBadge = premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE)
        val activeOneTimeIds = mutableSetOf<String>()
        val result = billingClient.queryPurchases()
        for (purchase in result) {
            for (productId in purchase.products) {
                if (productId in ProductRegistry.oneTimeProductIds) {
                    activeOneTimeIds.add(productId)
                }
            }
        }
        for (productId in ProductRegistry.oneTimeProductIds) {
            val isActive = productId in activeOneTimeIds
            premiumStore.markPurchased(productId, isActive)
            log.d { "One-time state refreshed: $productId active=$isActive" }
        }
        val hasSupporterBadgeNow = premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE)
        if (hadSupporterBadge && !hasSupporterBadgeNow) {
            _subscriptionDeactivated.tryEmit(Unit)
            log.d { "Supporter badge lost — one-time purchases revoked" }
        }
    }

    private suspend fun refreshSubscriptionState() {
        val hadSupporterBadge = premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE)
        val activeSubIds = mutableSetOf<String>()
        val subs = billingClient.querySubscriptions()
        for (purchase in subs) {
            for (productId in purchase.products) {
                if (productId in ProductRegistry.subscriptionProductIds) {
                    activeSubIds.add(productId)
                }
            }
        }
        for (productId in ProductRegistry.subscriptionProductIds) {
            val isActive = productId in activeSubIds
            premiumStore.markSubscriptionActive(productId, isActive)
            log.d { "Sub state refreshed: $productId active=$isActive" }
        }
        val hasSupporterBadgeNow = premiumStore.hasFeature(PremiumFeature.SUPPORTER_BADGE)
        if (hadSupporterBadge && !hasSupporterBadgeNow) {
            _subscriptionDeactivated.tryEmit(Unit)
            log.d { "Supporter badge lost — subscription expired" }
        }
    }
}
