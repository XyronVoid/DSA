package com.spacebachao.app.billing

import android.app.Activity
import android.content.Context
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

/**
 * One-time "Pro" unlock through Google Play.
 * The payment screen is shown by the Play Store app itself; this app never sees card details.
 *
 * Play Console setup: Monetize > Products > In-app products > create product id "pro_lifetime".
 */
class ProBilling(
    context: Context,
    /** isPro = null means "unknown right now" (keep the cached value). */
    private val onUpdate: (isPro: Boolean?, price: String?) -> Unit,
) : PurchasesUpdatedListener {

    companion object {
        const val PRO_ID = "pro_lifetime"
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var details: ProductDetails? = null
    private var price: String? = null

    fun connect() {
        if (client.isReady) {
            refresh()
            return
        }
        try {
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) refresh()
                }

                override fun onBillingServiceDisconnected() {
                    // Library reconnects automatically.
                }
            })
        } catch (e: Exception) {
            // Play Store missing or too old: app keeps working in free mode.
        }
    }

    fun refresh() {
        if (!client.isReady) return
        queryProduct()
        queryOwned()
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRO_ID)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val d = queryResult.productDetailsList.firstOrNull()
                details = d
                price = d?.let { priceOf(it) }
                onUpdate(null, price)
            }
        }
    }

    private fun queryOwned() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                onUpdate(handle(purchases), price)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null && handle(purchases)) onUpdate(true, price)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned()
            else -> Unit
        }
    }

    /** Returns true if Pro is owned. Acknowledges new purchases (otherwise Google refunds them). */
    private fun handle(purchases: List<Purchase>): Boolean {
        var owned = false
        for (p in purchases) {
            if (!p.products.contains(PRO_ID)) continue
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                owned = true
                if (!p.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                    client.acknowledgePurchase(ack) { }
                }
            }
        }
        return owned
    }

    /** false = Pro can't be bought right now (e.g. app not installed from Google Play). */
    fun buy(activity: Activity): Boolean {
        val d = details ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(d)
        d.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let { productParams.setOfferToken(it) }
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams.build()))
            .build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun priceOf(d: ProductDetails): String? =
        d.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
            ?: d.oneTimePurchaseOfferDetails?.formattedPrice
}
