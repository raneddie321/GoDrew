package com.godraw.app.billing

import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The app-wide source of truth for the user's plan. Production builds use only verified Google
 * Play subscriptions; debug builds may additionally apply the [TierSimulator] override.
 * Downgrades never delete work — they only restrict adding beyond the new limits.
 */
class EntitlementManager(
    billing: BillingRepository,
    val simulator: TierSimulator,
    scope: CoroutineScope,
) {
    val tier: StateFlow<Tier> = combine(billing.verifiedTier, simulator.override) { verified, simulated ->
        if (simulator.isAvailable && simulated != null) simulated else verified
    }.stateIn(scope, SharingStarted.Eagerly, Tier.FREE)

    val gate: StateFlow<FeatureGate> = tier.map { FeatureGate(it) }
        .stateIn(scope, SharingStarted.Eagerly, FeatureGate(Tier.FREE))

    val currentGate: FeatureGate get() = gate.value
}
