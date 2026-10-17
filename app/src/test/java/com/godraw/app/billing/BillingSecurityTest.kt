package com.godraw.app.billing

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class BillingSecurityTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun suggestedProductIdsAreConfigured() {
        assertEquals(
            setOf("godraw_pro_monthly", "godraw_pro_yearly", "godraw_max_monthly", "godraw_max_yearly"),
            BillingConfig.products.map { it.productId }.toSet(),
        )
        assertEquals(Tier.MAX, BillingConfig.product("godraw_max_yearly")!!.tier)
    }

    @Test
    fun unconfiguredVerifierNeverGrantsAccess() = runBlocking {
        val verifier = ServerPurchaseVerifier("")
        assertFalse(verifier.isConfigured)
        val result = verifier.verify(PurchaseToVerify("com.godraw.app", "godraw_pro_monthly", "token"))
        assertTrue(result is VerificationResult.Unavailable)
        assertFalse(ServerPurchaseVerifier("http://insecure.example").isConfigured)
    }

    @Test
    fun verifiedEntitlementsAreBoundToOwnedPurchaseTokens() {
        val store = VerifiedEntitlementStore(context)
        store.put("token-a", Tier.PRO, Long.MAX_VALUE)
        store.put("token-b", Tier.MAX, Long.MAX_VALUE)
        assertEquals(Tier.PRO, store.find("token-a")!!.tier)
        // Only the hash is stored, never the token itself.
        assertTrue(store.all().none { it.tokenHash.contains("token") })
        store.retainOnly(listOf("token-a"))
        assertNull(store.find("token-b"))
        assertEquals(1, store.all().size)
    }
}
