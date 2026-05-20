package tools.mo3ta.salo.billing

import android.app.Activity
import android.content.Context
import co.touchlab.kermit.Logger
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.ProductDetailsResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

class SaloBillingClient(context: Context) {

    private val log = Logger.withTag("Billing")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    var onPurchaseCompleted: ((List<String>) -> Unit)? = null

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        if (result.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            scope.launch {
                for (purchase in purchases) {
                    handlePurchase(purchase)
                }
            }
        } else {
            log.w { "Purchase update: code=${result.responseCode} msg=${result.debugMessage}" }
        }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    log.d { "Billing connected" }
                    scope.launch { processPendingPurchases() }
                } else {
                    log.w { "Billing setup failed: ${result.debugMessage}" }
                }
            }

            override fun onBillingServiceDisconnected() {
                _isConnected.value = false
                log.d { "Billing disconnected" }
            }
        })
    }

    fun disconnect() {
        client.endConnection()
        _isConnected.value = false
    }

    suspend fun queryProductDetails(productId: String): ProductDetailsResult {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        return client.queryProductDetails(params)
    }

    fun launchBillingFlow(activity: Activity, productId: String) {
        scope.launch {
            val result = queryProductDetails(productId)
            val details = result.productDetailsList?.firstOrNull() ?: run {
                log.w { "Product $productId not found" }
                return@launch
            }
            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(
                        BillingFlowParams.ProductDetailsParams.newBuilder()
                            .setProductDetails(details)
                            .build()
                    )
                )
                .build()
            client.launchBillingFlow(activity, flowParams)
        }
    }

    suspend fun queryPurchases(): List<Purchase> {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        return result.purchasesList
    }

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            log.d { "Purchase completed: ${purchase.products}" }
            onPurchaseCompleted?.invoke(purchase.products)
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val ackResult = client.acknowledgePurchase(ackParams)
                log.d { "Acknowledge result: ${ackResult.responseCode}" }
            }
        }
    }

    private suspend fun processPendingPurchases() {
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )
        for (purchase in result.purchasesList) {
            handlePurchase(purchase)
        }
    }
}
