package com.godraw.core.animation

import com.godraw.core.model.Keyframe
import com.godraw.core.model.LayerAnimation
import com.godraw.core.model.MotionPathMode
import com.godraw.core.model.TransformProperty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyframeSamplerTest {

    private val eps = 1e-3f

    @Test
    fun easingEndpointsAreExact() {
        EasingType.entries.filter { it != EasingType.STEP }.forEach { type ->
            val bez = if (type == EasingType.CUSTOM_BEZIER) listOf(0.25f, 0.1f, 0.25f, 1f) else null
            assertEquals("$type at 0", 0f, Easing.apply(type, 0f, bez), eps)
            assertEquals("$type at 1", 1f, Easing.apply(type, 1f, bez), eps)
        }
    }

    @Test
    fun easeCurvesHaveExpectedShape() {
        assertEquals(0.25f, Easing.apply(EasingType.EASE_IN, 0.5f), eps)
        assertEquals(0.75f, Easing.apply(EasingType.EASE_OUT, 0.5f), eps)
        assertEquals(0.5f, Easing.apply(EasingType.EASE_IN_OUT, 0.5f), eps)
        assertTrue(Easing.apply(EasingType.EASE_IN_BACK, 0.2f) < 0f) // anticipation
        assertTrue(Easing.apply(EasingType.EASE_OUT_BACK, 0.8f) > 1f) // overshoot
        // Linear-equivalent bezier is identity.
        assertEquals(0.3f, Easing.cubicBezier(0.3f, 0.3f, 0.7f, 0.7f, 0.3f), 1e-3f)
    }

    @Test
    fun linearInterpolationBetweenKeyframes() {
        val track = listOf(Keyframe(0, 0f), Keyframe(10, 100f))
        assertEquals(0f, KeyframeSampler.sample(track, 0f, 0f), eps)
        assertEquals(50f, KeyframeSampler.sample(track, 5f, 0f), eps)
        assertEquals(100f, KeyframeSampler.sample(track, 10f, 0f), eps)
        // Holds before the first / after the last keyframe.
        assertEquals(0f, KeyframeSampler.sample(track, -5f, 0f), eps)
        assertEquals(100f, KeyframeSampler.sample(track, 50f, 0f), eps)
    }

    @Test
    fun easingAppliedPerSegment() {
        val track = listOf(Keyframe(0, 0f, EasingType.EASE_IN), Keyframe(10, 100f))
        assertEquals(25f, KeyframeSampler.sample(track, 5f, 0f), eps)
    }

    @Test
    fun emptyTrackReturnsDefault() {
        assertEquals(1f, KeyframeSampler.sample(emptyList(), 3f, 1f), eps)
        val t = KeyframeSampler.sample(LayerAnimation(), 3f)
        assertTrue(t.isIdentity)
    }

    @Test
    fun smoothMotionPathPassesThroughKeyframes() {
        val anim = LayerAnimation(pathMode = MotionPathMode.SMOOTH)
            .withTrack(TransformProperty.POSITION_X, listOf(Keyframe(0, 0f), Keyframe(10, 100f), Keyframe(20, 0f)))
        assertEquals(100f, KeyframeSampler.sample(anim, 10f).x, eps)
        assertEquals(0f, KeyframeSampler.sample(anim, 0f).x, eps)
        // Smooth path overshoots less sharply than linear near the apex but stays continuous.
        val before = KeyframeSampler.sample(anim, 9.9f).x
        val after = KeyframeSampler.sample(anim, 10.1f).x
        assertTrue(kotlin.math.abs(before - after) < 2f)
        assertTrue(KeyframeSampler.motionPath(anim).isNotEmpty())
    }

    @Test
    fun upsertReplacesKeyframeAtSameFrame() {
        val t = KeyframeSampler.upsert(listOf(Keyframe(0, 1f), Keyframe(5, 2f)), Keyframe(5, 9f))
        assertEquals(listOf(Keyframe(0, 1f), Keyframe(5, 9f)), t)
    }

    @Test
    fun transitionPresetsCreateKeyframes() {
        val anim = Transitions.apply(LayerAnimation(), TransitionPreset.FADE_IN, 0, 12, 100, 100)
        assertEquals(0f, KeyframeSampler.sample(anim, 0f).opacity, eps)
        assertEquals(1f, KeyframeSampler.sample(anim, 12f).opacity, eps)
        val slide = Transitions.apply(LayerAnimation(), TransitionPreset.SLIDE_IN_LEFT, 4, 8, 200, 100)
        assertEquals(-200f, KeyframeSampler.sample(slide, 4f).x, eps)
        assertEquals(0f, KeyframeSampler.sample(slide, 12f).x, eps)
    }
}
