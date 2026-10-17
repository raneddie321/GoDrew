package com.godraw.app.billing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.godraw.core.entitlement.Tier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class DebugSimulatorTest {
    @Test
    fun debugBuildsCanSimulateEveryTier() {
        val sim = TierSimulatorFactory.create(ApplicationProvider.getApplicationContext())
        assertTrue(sim.isAvailable)
        Tier.entries.forEach { t ->
            sim.set(t)
            assertEquals(t, sim.override.value)
        }
        sim.set(null)
        assertNull(sim.override.value)
    }
}
