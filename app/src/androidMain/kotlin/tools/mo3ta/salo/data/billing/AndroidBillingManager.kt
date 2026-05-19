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
import kotlinx.coroutines.launch
import tools.mo3ta.salo.billing.SaloBillingClient

class AndroidBillingManager(
    private val billingClient: SaloBillingClient,
    private val premiumStore: PremiumStore,
) : BillingManager {

    private val log = Logger.withTag("AndroidBillingManager")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val productPrices = mutableMapOf<String, String>()

    override val isEnabled: Boolean = true

    private var currentActivity: Activity? = null

    override fun initialize() {
        billingClient.onPurchaseCompleted = { productIds ->
            for (productId in productIds) {
                if (productId in ProductRegistry.allProductIds) {
                    premiumStore.markPurchased(productId)
                    log.d { "Purchase confirmed: $productId" }
                }
            }
        }
        billingClient.connect()
        scope.launch {
            billingClient.isConnected.collect { connected ->
                if (connected) {
                    loadProductPrices()
                    restorePurchasesInternal()
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
        billingClient.launchBillingFlow(activity, productId)
    }

    override fun restorePurchases() {
        scope.launch { restorePurchasesInternal() }
    }

    override fun isPurchased(productId: String): Boolean =
        premiumStore.isPurchased(productId)

    override fun getProductPrice(productId: String): String? =
        productPrices[productId]

    private suspend fun loadProductPrices() {
        for (productId in ProductRegistry.allProductIds) {
            val result = billingClient.queryProductDetails(productId)
            val details = result.productDetailsList?.firstOrNull() ?: continue
            val price = details.oneTimePurchaseOfferDetails?.formattedPrice ?: continue
            productPrices[productId] = price
            log.d { "Price loaded: $productId = $price" }
        }
    }

    private suspend fun restorePurchasesInternal() {
        val result = billingClient.queryPurchases()
        for (purchase in result) {
            for (productId in purchase.products) {
                if (productId in ProductRegistry.allProductIds) {
                    premiumStore.markPurchased(productId)
                    log.d { "Restored purchase: $productId" }
                }
            }
        }
    }
}
