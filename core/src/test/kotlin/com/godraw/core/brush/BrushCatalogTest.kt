package com.godraw.core.brush

import com.godraw.core.entitlement.Entitlements
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrushCatalogTest {

    @Test
    fun idsAreUnique() {
        val ids = BrushCatalog.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun freeTierGetsEveryFamily() {
        val free = BrushCatalog.all.take(Entitlements.FREE.brushCount!!)
        assertEquals(BrushFamily.entries.toSet(), free.map { it.family }.toSet())
    }

    @Test
    fun brushParametersAreSane() {
        BrushCatalog.all.forEach { b ->
            assertTrue(b.id, b.size > 0f)
            assertTrue(b.id, b.opacity in 0f..1f)
            assertTrue(b.id, b.hardness in 0f..1f)
            assertTrue(b.id, b.spacing > 0f)
            assertTrue(b.id, b.diameterAt(0f) > 0f)
            assertTrue(b.id, b.diameterAt(1f) >= b.diameterAt(0f))
        }
    }

    @Test
    fun catalogIsLargerThanProTier() {
        assertTrue(BrushCatalog.totalCount > Entitlements.PRO.brushCount!!)
    }
}
