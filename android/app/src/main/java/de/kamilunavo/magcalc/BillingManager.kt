package de.kamilunavo.magcalc

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

class BillingManager(context: Context) : PurchasesUpdatedListener {
    companion object {
        const val PRO_PRODUCT_ID = "de.kamilunavo.magcalc.pro"
    }

    var isPro by mutableStateOf(false)
        private set
    var productPrice by mutableStateOf<String?>(null)
        private set
    var billingReady by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set

    private var productDetails: ProductDetails? = null

    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .enableAutoServiceReconnection()
        .build()

    init { connect() }

    fun connect() {
        if (billingClient.isReady) {
            billingReady = true
            queryProduct()
            refreshPurchases()
            return
        }
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                billingReady = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                if (billingReady) {
                    queryProduct()
                    refreshPurchases()
                } else {
                    statusMessage = "Google Play Billing unavailable (${billingResult.responseCode})."
                }
            }

            override fun onBillingServiceDisconnected() {
                billingReady = false
            }
        })
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails
        if (details == null) {
            statusMessage = "MAGCalc Pro is not available yet."
            queryProduct()
            return
        }
        val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        if (offerToken.isNullOrBlank()) {
            statusMessage = "No eligible Google Play offer is available."
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            statusMessage = result.debugMessage.ifBlank { "Purchase could not be started." }
        }
    }

    fun restorePurchases() {
        statusMessage = null
        refreshPurchases()
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> statusMessage = null
            else -> statusMessage = billingResult.debugMessage.ifBlank { "Purchase failed." }
        }
    }

    private fun queryProduct() {
        if (!billingClient.isReady) return
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, result ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                statusMessage = billingResult.debugMessage.ifBlank { "Could not load MAGCalc Pro." }
                return@queryProductDetailsAsync
            }
            productDetails = result.productDetailsList.firstOrNull { it.productId == PRO_PRODUCT_ID }
            productPrice = productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
        }
    }

    private fun refreshPurchases() {
        if (!billingClient.isReady) {
            connect()
            return
        }
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases)
            } else {
                statusMessage = billingResult.debugMessage.ifBlank { "Could not restore purchases." }
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        val owned = purchases.firstOrNull {
            it.purchaseState == Purchase.PurchaseState.PURCHASED && PRO_PRODUCT_ID in it.products
        }
        isPro = owned != null
        owned?.let { purchase ->
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient.acknowledgePurchase(params) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        statusMessage = result.debugMessage.ifBlank { "Purchase acknowledgement failed." }
                    }
                }
            }
        }
    }
}
