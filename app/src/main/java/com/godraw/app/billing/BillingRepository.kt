package com.godraw.app.billing

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
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class UnavailableReason { BILLING_UNSUPPORTED, PLAY_UNAVAILABLE, PRODUCTS_NOT_CONFIGURED, VERIFICATION_NOT_CONFIGURED }

sealed interface BillingAvailability {
    data object Connecting : BillingAvailability
    data object Ready : BillingAvailability
    data class Unavailable(val reason: UnavailableReason) : BillingAvailability
}

enum class BillingMessage {
    PURCHASE_COMPLETE,
    PURCHASE_PENDING,
    PURCHASE_CANCELED,
    PURCHASE_FAILED,
    VERIFICATION_FAILED,
    RESTORED,
    NOTHING_TO_RESTORE,
}

/** A purchasable plan as returned by Google Play (prices are Play's, never hard-coded). */
data class PlanOffer(
    val product: SubscriptionProduct,
    val details: ProductDetails,
    val offerToken: String,
    val formattedPrice: String,
    /** ISO-8601 billing period, e.g. P1M or P1Y. */
    val billingPeriod: String,
    val hasFreeTrial: Boolean,
)

data class BillingState(
    val availability: BillingAvailability = BillingAvailability.Connecting,
    val offers: List<PlanOffer> = emptyList(),
    val activeProductId: String? = null,
    val pendingPurchase: Boolean = false,
    val message: BillingMessage? = null,
)

/**
 * Google Play Billing integration: product loading, purchase flow, pending purchases,
 * server verification, acknowledgement, restore, upgrades/downgrades and expiry handling.
 */
class BillingRepository(
    context: Context,
    private val verifier: PurchaseVerifier,
    private val store: VerifiedEntitlementStore,
    private val scope: CoroutineScope,
) : PurchasesUpdatedListener {

    private val appContext = context.applicationContext
    private val packageName = appContext.packageName

    private val _state = MutableStateFlow(BillingState())
    val state: StateFlow<BillingState> = _state.asStateFlow()

    private val _verifiedTier = MutableStateFlow(Tier.FREE)
    /** Tier granted by verified, currently owned Google Play subscriptions. */
    val verifiedTier: StateFlow<Tier> = _verifiedTier.asStateFlow()

    private var ownedPurchases: List<Purchase> = emptyList()
    private var retryDelayMs = 1_000L

    private val client: BillingClient = BillingClient.newBuilder(appContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    fun start() {
        if (client.isReady) return
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    retryDelayMs = 1_000L
                    scope.launch {
                        loadProducts()
                        refreshPurchases(userInitiated = false)
                    }
                } else {
                    val reason = if (result.responseCode == BillingClient.BillingResponseCode.BILLING_UNAVAILABLE) {
                        UnavailableReason.BILLING_UNSUPPORTED
                    } else {
                        UnavailableReason.PLAY_UNAVAILABLE
                    }
                    _state.update { it.copy(availability = BillingAvailability.Unavailable(reason)) }
                }
            }

            override fun onBillingServiceDisconnected() {
                scope.launch {
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs * 2).coerceAtMost(60_000L)
                    start()
                }
            }
        })
    }

    private suspend fun loadProducts() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                BillingConfig.products.map {
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(it.productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                },
            )
            .build()
        val result = client.queryProductDetails(params)
        val offers = result.productDetailsList.orEmpty().mapNotNull { details -> toOffer(details) }
            .sortedWith(compareBy({ it.product.tier }, { it.product.period }))
        val availability = when {
            result.billingResult.responseCode != BillingClient.BillingResponseCode.OK ->
                BillingAvailability.Unavailable(UnavailableReason.PLAY_UNAVAILABLE)
            offers.isEmpty() -> BillingAvailability.Unavailable(UnavailableReason.PRODUCTS_NOT_CONFIGURED)
            !verifier.isConfigured -> BillingAvailability.Unavailable(UnavailableReason.VERIFICATION_NOT_CONFIGURED)
            else -> BillingAvailability.Ready
        }
        _state.update { it.copy(availability = availability, offers = offers) }
    }

    private fun toOffer(details: ProductDetails): PlanOffer? {
        val product = BillingConfig.product(details.productId) ?: return null
        val offers = details.subscriptionOfferDetails.orEmpty()
        // Prefer the base plan (no offer id); fall back to the first available offer.
        val offer = offers.firstOrNull { it.offerId == null } ?: offers.firstOrNull() ?: return null
        val phases = offer.pricingPhases.pricingPhaseList
        val recurring = phases.lastOrNull() ?: return null
        return PlanOffer(
            product = product,
            details = details,
            offerToken = offer.offerToken,
            formattedPrice = recurring.formattedPrice,
            billingPeriod = recurring.billingPeriod,
            hasFreeTrial = phases.any { it.priceAmountMicros == 0L },
        )
    }

    /** Starts the Google Play purchase flow, handling upgrades and downgrades. */
    fun launchPurchase(activity: Activity, offer: PlanOffer) {
        val availability = _state.value.availability
        if (availability != BillingAvailability.Ready) return
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(offer.details)
            .setOfferToken(offer.offerToken)
            .build()
        val builder = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams))

        val current = ownedPurchases.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        val currentProduct = current?.products?.firstNotNullOfOrNull { BillingConfig.product(it) }
        if (current != null && currentProduct != null && currentProduct.productId != offer.product.productId) {
            val isUpgrade = offer.product.tier.ordinal > currentProduct.tier.ordinal ||
                (offer.product.tier == currentProduct.tier && offer.product.period == BillingPeriod.YEARLY)
            val mode = if (isUpgrade) {
                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.WITH_TIME_PRORATION
            } else {
                BillingFlowParams.SubscriptionUpdateParams.ReplacementMode.DEFERRED
            }
            builder.setSubscriptionUpdateParams(
                BillingFlowParams.SubscriptionUpdateParams.newBuilder()
                    .setOldPurchaseToken(current.purchaseToken)
                    .setSubscriptionReplacementMode(mode)
                    .build(),
            )
        }
        val result = client.launchBillingFlow(activity, builder.build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.update { it.copy(message = BillingMessage.PURCHASE_FAILED) }
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> scope.launch {
                purchases.orEmpty().forEach { handlePurchase(it, announce = true) }
                refreshPurchases(userInitiated = false)
            }
            BillingClient.BillingResponseCode.USER_CANCELED ->
                _state.update { it.copy(message = BillingMessage.PURCHASE_CANCELED) }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
                scope.launch { refreshPurchases(userInitiated = true) }
            else -> _state.update { it.copy(message = BillingMessage.PURCHASE_FAILED) }
        }
    }

    private suspend fun handlePurchase(purchase: Purchase, announce: Boolean) {
        when (purchase.purchaseState) {
            Purchase.PurchaseState.PENDING -> {
                // Pending purchases (e.g. cash payments) grant nothing until they complete.
                _state.update { it.copy(pendingPurchase = true, message = if (announce) BillingMessage.PURCHASE_PENDING else it.message) }
            }
            Purchase.PurchaseState.PURCHASED -> {
                val productId = purchase.products.firstOrNull { BillingConfig.product(it) != null } ?: return
                when (val verdict = verifier.verify(PurchaseToVerify(packageName, productId, purchase.purchaseToken))) {
                    is VerificationResult.Verified -> {
                        store.put(purchase.purchaseToken, verdict.tier, verdict.expiryTimeMillis)
                        if (!purchase.isAcknowledged) {
                            client.acknowledgePurchase(
                                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
                            )
                        }
                        if (announce) _state.update { it.copy(message = BillingMessage.PURCHASE_COMPLETE, pendingPurchase = false) }
                    }
                    VerificationResult.Invalid -> {
                        store.remove(purchase.purchaseToken)
                        if (announce) _state.update { it.copy(message = BillingMessage.VERIFICATION_FAILED) }
                    }
                    is VerificationResult.Unavailable -> {
                        // Keep a previously verified, unexpired record (offline use); grant nothing new.
                        if (announce) _state.update { it.copy(message = BillingMessage.VERIFICATION_FAILED) }
                    }
                }
            }
            else -> Unit
        }
    }

    /** Re-reads owned subscriptions from Google Play ("Restore purchases"). */
    suspend fun refreshPurchases(userInitiated: Boolean) {
        if (!client.isReady) {
            start()
            return
        }
        val result = client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
        )
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) return
        ownedPurchases = result.purchasesList
        store.retainOnly(ownedPurchases.map { it.purchaseToken })

        val now = System.currentTimeMillis()
        for (purchase in ownedPurchases) {
            val cached = store.find(purchase.purchaseToken)
            val needsVerification = purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
                (cached == null || cached.expiryTimeMillis <= now || !purchase.isAcknowledged)
            if (needsVerification || purchase.purchaseState == Purchase.PurchaseState.PENDING) {
                handlePurchase(purchase, announce = false)
            }
        }
        recomputeTier()
        if (userInitiated) {
            val restored = _verifiedTier.value != Tier.FREE
            _state.update { it.copy(message = if (restored) BillingMessage.RESTORED else BillingMessage.NOTHING_TO_RESTORE) }
        }
    }

    private fun recomputeTier() {
        val now = System.currentTimeMillis()
        val ownedTokens = ownedPurchases
            .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .associateBy { VerifiedEntitlementStore.hash(it.purchaseToken) }
        val valid = store.all().filter { it.tokenHash in ownedTokens && it.expiryTimeMillis > now }
        val best = valid.maxByOrNull { it.tier.ordinal }
        _verifiedTier.value = best?.tier ?: Tier.FREE
        val activeProduct = best?.let { e -> ownedTokens[e.tokenHash]?.products?.firstOrNull() }
        _state.update {
            it.copy(
                activeProductId = activeProduct,
                pendingPurchase = ownedPurchases.any { p -> p.purchaseState == Purchase.PurchaseState.PENDING },
            )
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
