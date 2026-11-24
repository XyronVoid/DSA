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

/** One subscription option shown on the Pro screen (e.g. monthly / yearly). */
data class ProPlan(
    val basePlanId: String,
    val price: String,
    /** ISO-8601 period from Google Play, e.g. P1M, P1Y */
    val period: String,
    val offerToken: String,
    /** Free trial period if the offer has one, e.g. P1W */
    val trialPeriod: String?,
) {
    val periodLabel: String get() = periodText(period, short = true)
    val title: String get() = when (period) {
        "P1W" -> "Weekly"
        "P1M" -> "Monthly"
        "P3M" -> "3 months"
        "P6M" -> "6 months"
        "P1Y" -> "Yearly"
        else -> periodText(period, short = false)
    }
    val trialLabel: String? get() = trialPeriod?.let { "${periodText(it, short = false)} free" }

    companion object {
        fun periodText(iso: String, short: Boolean): String {
            val m = Regex("""P(\d+)([DWMY])""").find(iso) ?: return iso
            val n = m.groupValues[1].toIntOrNull() ?: 1
            val unit = when (m.groupValues[2]) {
                "D" -> "day"
                "W" -> "week"
                "M" -> "month"
                else -> "year"
            }
            return if (n == 1 && short) unit else "$n $unit${if (n > 1) "s" else ""}"
        }
    }
}

/**
 * "Space Bachao Pro" subscription through Google Play.
 * The payment screen is shown by the Play Store app itself; this app never sees card details.
 *
 * Play Console setup: Monetize with Play > Products > Subscriptions >
 * create subscription id "space_bachao_pro" with base plans "monthly" and "yearly".
 */
class ProBilling(
    context: Context,
    /** isPro = null means "unknown right now" (keep the cached value). plans = null means unchanged. */
    private val onUpdate: (isPro: Boolean?, plans: List<ProPlan>?) -> Unit,
) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "space_bachao_pro"
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private var details: ProductDetails? = null

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
            // Play Store missing or too old: app keeps working on the free plan.
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
                        .setProductId(PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        client.queryProductDetailsAsync(params) { billingResult, queryResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                val d = queryResult.productDetailsList.firstOrNull()
                details = d
                onUpdate(null, d?.let { plansOf(it) } ?: emptyList())
            }
        }
    }

    /** One plan per base plan; if a free-trial offer exists, that one is used. */
    private fun plansOf(d: ProductDetails): List<ProPlan> {
        val offers = d.subscriptionOfferDetails ?: return emptyList()
        val plans = offers.mapNotNull { offer ->
            val phases = offer.pricingPhases.pricingPhaseList
            val recurring = phases.lastOrNull() ?: return@mapNotNull null
            val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
            ProPlan(
                basePlanId = offer.basePlanId,
                price = recurring.formattedPrice,
                period = recurring.billingPeriod,
                offerToken = offer.offerToken,
                trialPeriod = trial?.billingPeriod,
            )
        }
        return plans
            .groupBy { it.basePlanId }
            .map { (_, list) -> list.firstOrNull { it.trialPeriod != null } ?: list.first() }
            .sortedBy { orderOf(it.period) }
    }

    private fun orderOf(period: String) = when (period) {
        "P1W" -> 0
        "P1M" -> 1
        "P3M" -> 2
        "P6M" -> 3
        "P1Y" -> 4
        else -> 5
    }

    private fun queryOwned() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        client.queryPurchasesAsync(params) { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                onUpdate(handle(purchases), null)
            }
        }
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: MutableList<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases != null && handle(purchases)) onUpdate(true, null)
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned()
            else -> Unit
        }
    }

    /** true if an active Pro subscription exists. Acknowledges new purchases (otherwise Google refunds them). */
    private fun handle(purchases: List<Purchase>): Boolean {
        var owned = false
        for (p in purchases) {
            if (!p.products.contains(PRODUCT_ID)) continue
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

    /** false = can't subscribe right now (e.g. app not installed from Google Play). */
    fun buy(activity: Activity, plan: ProPlan): Boolean {
        val d = details ?: return false
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(d)
            .setOfferToken(plan.offerToken)
            .build()
        val flow = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productParams))
            .build()
        return client.launchBillingFlow(activity, flow).responseCode == BillingClient.BillingResponseCode.OK
    }
}
