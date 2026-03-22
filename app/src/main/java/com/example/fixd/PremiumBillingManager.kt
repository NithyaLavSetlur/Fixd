package com.example.fixd

import android.app.Activity
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

class PremiumBillingManager(
    private val activity: Activity,
    private val onProductLoaded: (ProductDetails?, String?) -> Unit,
    private val onBillingMessage: (String?) -> Unit,
    private val onPremiumStatusResolved: (Boolean) -> Unit
) : PurchasesUpdatedListener {

    private var billingClient: BillingClient? = null
    private var productDetails: ProductDetails? = null
    private var selectedOfferToken: String? = null

    fun start() {
        if (billingClient != null) return
        billingClient = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .build()
        billingClient?.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryProduct()
                    restorePurchases()
                } else {
                    onBillingMessage(billingResult.debugMessage)
                }
            }

            override fun onBillingServiceDisconnected() {
                onBillingMessage(activity.getString(R.string.premium_billing_disconnected))
            }
        })
    }

    fun launchPurchase() {
        val details = productDetails
        val offerToken = selectedOfferToken
        if (details == null || offerToken.isNullOrBlank()) {
            onBillingMessage(activity.getString(R.string.premium_billing_unavailable))
            return
        }

        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .setOfferToken(offerToken)
            .build()

        billingClient?.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
    }

    fun restorePurchases() {
        val client = billingClient ?: return
        if (!client.isReady) {
            onBillingMessage(activity.getString(R.string.premium_billing_unavailable))
            return
        }
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        ) { _, purchases ->
            handlePurchases(purchases)
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> handlePurchases(purchases.orEmpty())
            BillingClient.BillingResponseCode.USER_CANCELED -> onBillingMessage(activity.getString(R.string.premium_purchase_cancelled))
            else -> onBillingMessage(billingResult.debugMessage)
        }
    }

    private fun queryProduct() {
        val client = billingClient ?: return
        if (!client.isReady) {
            onBillingMessage(activity.getString(R.string.premium_billing_unavailable))
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { billingResult, detailsList ->
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                onBillingMessage(
                    billingResult.debugMessage.ifBlank {
                        activity.getString(R.string.premium_billing_unavailable)
                    }
                )
                onProductLoaded(null, null)
                return@queryProductDetailsAsync
            }
            productDetails = detailsList.firstOrNull()
            val selectedOffer = productDetails
                ?.subscriptionOfferDetails
                ?.firstOrNull { offer -> offer.basePlanId == PREMIUM_BASE_PLAN_ID }
                ?: productDetails?.subscriptionOfferDetails?.firstOrNull()
            selectedOfferToken = selectedOffer?.offerToken
            val priceLabel = selectedOffer?.pricingPhases?.pricingPhaseList?.firstOrNull()?.formattedPrice
            onProductLoaded(productDetails, priceLabel)
        }
    }

    private fun handlePurchases(purchases: List<Purchase>) {
        val premiumPurchase = purchases.firstOrNull { purchase ->
            purchase.products.contains(PREMIUM_PRODUCT_ID) &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }

        if (premiumPurchase == null) {
            onPremiumStatusResolved(false)
            return
        }

        if (premiumPurchase.isAcknowledged) {
            onPremiumStatusResolved(true)
            return
        }

        billingClient?.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(premiumPurchase.purchaseToken)
                .build()
        ) { result ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                onPremiumStatusResolved(true)
            } else {
                onBillingMessage(result.debugMessage)
            }
        }
    }

    fun stop() {
        runCatching { billingClient?.endConnection() }
        billingClient = null
        productDetails = null
        selectedOfferToken = null
    }

    companion object {
        const val PREMIUM_PRODUCT_ID = "fixd_premium"
        const val PREMIUM_BASE_PLAN_ID = "monthly"
    }
}
