package com.godraw.core.entitlement

import com.godraw.core.brush.BrushCatalog
import com.godraw.core.effects.EffectType
import com.godraw.core.model.LayerBlendMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureGateTest {

    private val free = FeatureGate(Tier.FREE)
    private val pro = FeatureGate(Tier.PRO)
    private val max = FeatureGate(Tier.MAX)

    @Test
    fun freeTierLimitsMatchSpecification() {
        val l = Entitlements.FREE
        assertEquals(20, l.maxProjects)
        assertEquals(15, l.maxLayers)
        assertEquals(500, l.maxFrames)
        assertEquals(24, l.maxFps)
        assertEquals(1080, l.maxExportShortEdge)
        assertEquals(40, l.brushCount)
        assertEquals(5, l.effectCount)
    }

    @Test
    fun proTierLimitsMatchSpecification() {
        val l = Entitlements.PRO
        assertEquals(100, l.maxProjects)
        assertEquals(60, l.maxLayers)
        assertEquals(2000, l.maxFrames)
        assertEquals(30, l.maxFps)
        assertEquals(1440, l.maxExportShortEdge)
        assertEquals(150, l.brushCount)
        assertEquals(25, l.effectCount)
    }

    @Test
    fun maxTierHasNoArbitraryLimits() {
        val l = Entitlements.MAX
        assertEquals(null, l.maxProjects)
        assertEquals(null, l.maxLayers)
        assertEquals(null, l.maxFrames)
        assertEquals(60, l.maxFps)
        assertEquals(2160, l.maxExportShortEdge)
        assertEquals(null, l.brushCount)
        assertEquals(null, l.effectCount)
    }

    @Test
    fun projectLimitPerTier() {
        assertTrue(free.canCreateProject(19).isGranted)
        val locked = free.canCreateProject(20)
        assertEquals(Access.Locked(Tier.PRO, LockReason.PROJECT_LIMIT), locked)
        assertTrue(pro.canCreateProject(99).isGranted)
        assertEquals(Access.Locked(Tier.MAX, LockReason.PROJECT_LIMIT), pro.canCreateProject(100))
        assertTrue(max.canCreateProject(100_000).isGranted)
    }

    @Test
    fun layerAndFrameLimitsPerTier() {
        assertTrue(free.canAddLayers(14).isGranted)
        assertFalse(free.canAddLayers(15).isGranted)
        assertTrue(pro.canAddLayers(15).isGranted)
        assertFalse(pro.canAddLayers(60).isGranted)
        assertTrue(max.canAddLayers(500).isGranted)

        assertTrue(free.canAddFrames(499).isGranted)
        assertFalse(free.canAddFrames(499, adding = 2).isGranted)
        assertTrue(pro.canAddFrames(1999).isGranted)
        assertFalse(pro.canAddFrames(2000).isGranted)
        assertTrue(max.canAddFrames(10_000).isGranted)
    }

    @Test
    fun fpsLimits() {
        assertTrue(free.canUseFps(24).isGranted)
        assertEquals(Access.Locked(Tier.PRO, LockReason.FPS_LIMIT), free.canUseFps(30))
        assertEquals(Access.Locked(Tier.MAX, LockReason.FPS_LIMIT), free.canUseFps(60))
        assertTrue(pro.canUseFps(30).isGranted)
        assertTrue(max.canUseFps(60).isGranted)
        assertEquals(24, free.clampFps(60))
    }

    @Test
    fun brushUnlocksFollowCatalogOrder() {
        assertEquals(208, BrushCatalog.totalCount)
        assertTrue(free.canUseBrush(39).isGranted)
        assertEquals(Access.Locked(Tier.PRO, LockReason.BRUSH), free.canUseBrush(40))
        assertTrue(pro.canUseBrush(149).isGranted)
        assertEquals(Access.Locked(Tier.MAX, LockReason.BRUSH), pro.canUseBrush(150))
        assertTrue(max.canUseBrush(BrushCatalog.totalCount - 1).isGranted)
        assertFalse(free.canUseBrush(0, isCustom = true).isGranted)
        assertTrue(pro.canUseBrush(0, isCustom = true).isGranted)
    }

    @Test
    fun effectUnlocksFollowCatalogOrder() {
        assertTrue(EffectType.entries.size > 25)
        assertTrue(free.canUseEffect(4).isGranted)
        assertFalse(free.canUseEffect(5).isGranted)
        assertTrue(pro.canUseEffect(24).isGranted)
        assertFalse(pro.canUseEffect(25).isGranted)
        assertTrue(max.canUseEffect(EffectType.entries.lastIndex).isGranted)
    }

    @Test
    fun blendModesPerTier() {
        assertTrue(free.canUseBlendMode(LayerBlendMode.MULTIPLY).isGranted)
        assertEquals(Access.Locked(Tier.PRO, LockReason.BLEND_MODE), free.canUseBlendMode(LayerBlendMode.COLOR_DODGE))
        assertEquals(Access.Locked(Tier.MAX, LockReason.BLEND_MODE), pro.canUseBlendMode(LayerBlendMode.LUMINOSITY))
        LayerBlendMode.entries.forEach { assertTrue(max.canUseBlendMode(it).isGranted) }
    }

    @Test
    fun booleanFeaturesPerTier() {
        Feature.entries.forEach { f ->
            val required = Entitlements.minimumTier(f)
            assertEquals(required == Tier.FREE, free.check(f).isGranted)
            assertEquals(required != Tier.MAX, pro.check(f).isGranted)
            assertTrue(max.check(f).isGranted)
        }
        assertFalse(free.check(Feature.LAYER_MASKS).isGranted)
        assertTrue(pro.check(Feature.LAYER_MASKS).isGranted)
        assertFalse(pro.check(Feature.CUSTOM_BEZIER_EASING).isGranted)
    }

    @Test
    fun exportSizeIsFittedToTier() {
        assertTrue(free.canExportSize(1920, 1080).isGranted)
        assertTrue(free.canExportSize(1080, 1920).isGranted)
        assertEquals(Access.Locked(Tier.PRO, LockReason.EXPORT_SIZE), free.canExportSize(2560, 1440))
        assertEquals(Access.Locked(Tier.MAX, LockReason.EXPORT_SIZE), free.canExportSize(3840, 2160))

        assertEquals(1920 to 1080, free.fitExportSize(3840, 2160))
        assertEquals(2560 to 1440, pro.fitExportSize(3840, 2160))
        assertEquals(3840 to 2160, max.fitExportSize(3840, 2160))
        // Never upscales beyond the requested scale.
        assertEquals(640 to 360, max.fitExportSize(640, 360))
        // Square canvases are limited by the short edge.
        assertEquals(1080 to 1080, free.fitExportSize(2048, 2048))
        // Dimensions are always even for video encoders.
        val (w, h) = free.fitExportSize(1001, 777)
        assertEquals(0, w % 2)
        assertEquals(0, h % 2)
    }

    @Test
    fun audioClipLimits() {
        assertTrue(free.canAddAudioClip(0).isGranted)
        assertFalse(free.canAddAudioClip(1).isGranted)
        assertTrue(pro.canAddAudioClip(7).isGranted)
        assertTrue(max.canAddAudioClip(100).isGranted)
    }
}
