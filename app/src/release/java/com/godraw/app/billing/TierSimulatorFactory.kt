package com.godraw.app.billing

import android.content.Context
import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Release builds: the subscription simulator does not exist and can never grant a tier. */
object TierSimulatorFactory {
    @Suppress("UNUSED_PARAMETER")
    fun create(context: Context): TierSimulator = DisabledTierSimulator
}

private object DisabledTierSimulator : TierSimulator {
    override val isAvailable: Boolean = false
    override val override: StateFlow<Tier?> = MutableStateFlow<Tier?>(null).asStateFlow()
    override fun set(tier: Tier?) = Unit
}
