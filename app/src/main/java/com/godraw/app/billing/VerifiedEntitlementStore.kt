package com.godraw.app.billing

import android.content.Context
import com.godraw.core.entitlement.Tier
import java.security.MessageDigest

/** A server-verified entitlement, bound to the purchase token that produced it. */
data class VerifiedEntitlement(val tokenHash: String, val tier: Tier, val expiryTimeMillis: Long)

/**
 * Remembers server-verified entitlements so premium features keep working offline. A cached
 * entry is only honoured while Google Play still reports the same purchase as owned (matched
 * by token hash) and before its expiry, so editing this file alone cannot unlock anything.
 */
class VerifiedEntitlementStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("verified_entitlements", Context.MODE_PRIVATE)

    fun all(): List<VerifiedEntitlement> = prefs.all.mapNotNull { (hash, value) ->
        val parts = (value as? String)?.split('|') ?: return@mapNotNull null
        val tier = Tier.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: return@mapNotNull null
        val expiry = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
        VerifiedEntitlement(hash, tier, expiry)
    }

    fun put(purchaseToken: String, tier: Tier, expiryTimeMillis: Long) {
        prefs.edit().putString(hash(purchaseToken), "${tier.name}|$expiryTimeMillis").apply()
    }

    fun remove(purchaseToken: String) {
        prefs.edit().remove(hash(purchaseToken)).apply()
    }

    /** Drops records whose purchase is no longer owned. */
    fun retainOnly(ownedTokens: Collection<String>) {
        val keep = ownedTokens.map { hash(it) }.toSet()
        val editor = prefs.edit()
        prefs.all.keys.filter { it !in keep }.forEach { editor.remove(it) }
        editor.apply()
    }

    fun find(purchaseToken: String): VerifiedEntitlement? = all().firstOrNull { it.tokenHash == hash(purchaseToken) }

    companion object {
        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
