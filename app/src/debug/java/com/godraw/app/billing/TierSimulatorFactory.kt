package com.godraw.app.billing

import android.content.Context
import com.godraw.app.BuildConfig
import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** DEBUG BUILDS ONLY: subscription simulator for testing Free, Pro and Max. */
object TierSimulatorFactory {
    fun create(context: Context): TierSimulator = DebugTierSimulator(context)
}

private class DebugTierSimulator(context: Context) : TierSimulator {
    private val prefs = context.applicationContext.getSharedPreferences("debug_tier_simulator", Context.MODE_PRIVATE)

    override val isAvailable: Boolean = BuildConfig.DEBUG

    private val state = MutableStateFlow(
        if (isAvailable) prefs.getString(KEY, null)?.let { v -> Tier.entries.firstOrNull { it.name == v } } else null,
    )
    override val override: StateFlow<Tier?> = state.asStateFlow()

    override fun set(tier: Tier?) {
        if (!isAvailable) return
        prefs.edit().apply { if (tier == null) remove(KEY) else putString(KEY, tier.name) }.apply()
        state.value = tier
    }

    private companion object {
        const val KEY = "tier"
    }
}
