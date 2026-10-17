package com.godraw.app.billing

import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.flow.StateFlow

/**
 * Development-only switch between Free, Pro and Max. The debug source set provides a working
 * implementation; the release source set provides one that is never available and always
 * reports no override, so production builds cannot grant premium access through it.
 */
interface TierSimulator {
    val isAvailable: Boolean
    val override: StateFlow<Tier?>
    fun set(tier: Tier?)
}
