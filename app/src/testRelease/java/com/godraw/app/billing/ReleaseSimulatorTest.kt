package com.godraw.app.billing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.godraw.core.entitlement.Tier
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class ReleaseSimulatorTest {
    @Test
    fun releaseBuildsCanNeverSimulatePremium() {
        val sim = TierSimulatorFactory.create(ApplicationProvider.getApplicationContext())
        assertFalse(sim.isAvailable)
        Tier.entries.forEach { t ->
            sim.set(t)
            assertNull(sim.override.value)
        }
    }
}
