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
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .enablePrepaidPlans()
                .build()
        )
        .build()

    fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _isConnected.value = true
                    log.d { "Billing connected" }
                    scope.launch {
                        processPendingPurchases()
                        processPendingSubscriptions()
                    }
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

    suspend fun queryProductDetails(productId: String, productType: String = BillingClient.ProductType.INAPP): List<ProductDetails> {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(productType)
                        .build()
                )
            )
            .build()
        return awaitProductDetails(params)
    }

    fun launchBillingFlow(activity: Activity, productId: String, productType: String = BillingClient.ProductType.INAPP) {
        scope.launch {
            val details = queryProductDetails(productId, productType).firstOrNull() ?: run {
                log.w { "Product $productId not found" }
                return@launch
            }

            val paramsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)

            if (productType == BillingClient.ProductType.SUBS) {
                val offerToken = details.subscriptionOfferDetails?.firstOrNull()?.offerToken
                if (offerToken != null) {
                    paramsBuilder.setOfferToken(offerToken)
                } else {
                    log.w { "No offer token for subscription $productId" }
                    return@launch
                }
            }

            val flowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(paramsBuilder.build()))
                .build()
            client.launchBillingFlow(activity, flowParams)
        }
    }

    suspend fun queryPurchases(): List<Purchase> =
        awaitPurchases(BillingClient.ProductType.INAPP)

    suspend fun querySubscriptions(): List<Purchase> =
        awaitPurchases(BillingClient.ProductType.SUBS)

    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            log.d { "Purchase completed: ${purchase.products}" }
            onPurchaseCompleted?.invoke(purchase.products)
            if (!purchase.isAcknowledged) {
                val ackParams = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                val ackResult = awaitAcknowledge(ackParams)
                log.d { "Acknowledge result: ${ackResult.responseCode}" }
            }
        }
    }

    private suspend fun processPendingPurchases() {
        for (purchase in awaitPurchases(BillingClient.ProductType.INAPP)) {
            handlePurchase(purchase)
        }
    }

    private suspend fun processPendingSubscriptions() {
        for (purchase in awaitPurchases(BillingClient.ProductType.SUBS)) {
            handlePurchase(purchase)
        }
    }

    // Suspend wrappers over the callback-based Billing APIs. The pure-Java
    // `billing` artifact (not `billing-ktx`) is used so the library's own
    // Kotlin version stays decoupled from the app's compiler version.
    private suspend fun awaitProductDetails(params: QueryProductDetailsParams): List<ProductDetails> =
        suspendCancellableCoroutine { cont ->
            client.queryProductDetailsAsync(params) { _, result ->
                cont.resume(result.productDetailsList)
            }
        }

    private suspend fun awaitPurchases(productType: String): List<Purchase> =
        suspendCancellableCoroutine { cont ->
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(productType)
                .build()
            client.queryPurchasesAsync(params) { _, purchases ->
                cont.resume(purchases)
            }
        }

    private suspend fun awaitAcknowledge(params: AcknowledgePurchaseParams): BillingResult =
        suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                cont.resume(result)
            }
        }
}
