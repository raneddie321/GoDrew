package com.godraw.app.billing

import com.godraw.app.BuildConfig
import com.godraw.core.entitlement.Tier

enum class BillingPeriod { MONTHLY, YEARLY }

data class SubscriptionProduct(val productId: String, val tier: Tier, val period: BillingPeriod)

/**
 * Google Play subscription configuration. The product ids below are *suggested* ids: create
 * subscriptions with exactly these ids in Google Play Console (or change them here). Prices,
 * offers and trials are configured in Play Console and displayed as Play returns them.
 */
object BillingConfig {

    val products: List<SubscriptionProduct> = listOf(
        SubscriptionProduct("godraw_pro_monthly", Tier.PRO, BillingPeriod.MONTHLY),
        SubscriptionProduct("godraw_pro_yearly", Tier.PRO, BillingPeriod.YEARLY),
        SubscriptionProduct("godraw_max_monthly", Tier.MAX, BillingPeriod.MONTHLY),
        SubscriptionProduct("godraw_max_yearly", Tier.MAX, BillingPeriod.YEARLY),
    )

    fun product(productId: String): SubscriptionProduct? = products.firstOrNull { it.productId == productId }

    /**
     * HTTPS endpoint of the developer's verification server, set at build time with
     * `-Pgodraw.verificationUrl=https://...` or the GODRAW_VERIFICATION_URL environment variable.
     */
    val verificationUrl: String = BuildConfig.PURCHASE_VERIFICATION_URL

    /** Premium purchases are only offered once server-side verification is configured. */
    val isVerificationConfigured: Boolean get() = verificationUrl.startsWith("https://")

    const val MANAGE_SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"
}
