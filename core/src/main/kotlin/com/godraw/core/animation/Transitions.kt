package com.godraw.core.animation

import com.godraw.core.entitlement.Feature
import com.godraw.core.model.Keyframe
import com.godraw.core.model.LayerAnimation
import com.godraw.core.model.TransformProperty

/**
 * Transition presets generate ordinary keyframes on a layer, so the result stays fully editable.
 */
enum class TransitionPreset(val requiredFeature: Feature?) {
    FADE_IN(null),
    FADE_OUT(null),
    SLIDE_IN_LEFT(Feature.ADVANCED_TRANSITIONS),
    SLIDE_IN_RIGHT(Feature.ADVANCED_TRANSITIONS),
    SLIDE_IN_TOP(Feature.ADVANCED_TRANSITIONS),
    SLIDE_IN_BOTTOM(Feature.ADVANCED_TRANSITIONS),
    ZOOM_IN(Feature.ADVANCED_TRANSITIONS),
    ZOOM_OUT(Feature.ADVANCED_TRANSITIONS),
    SPIN_IN(Feature.ADVANCED_TRANSITIONS),
}

object Transitions {

    fun apply(
        animation: LayerAnimation,
        preset: TransitionPreset,
        startFrame: Int,
        length: Int,
        canvasWidth: Int,
        canvasHeight: Int,
    ): LayerAnimation {
        val end = startFrame + length.coerceAtLeast(1)
        fun key(p: TransformProperty, from: Float, to: Float, easing: EasingType, a: LayerAnimation): LayerAnimation {
            var track = a.track(p)
            track = KeyframeSampler.upsert(track, Keyframe(startFrame, from, easing))
            track = KeyframeSampler.upsert(track, Keyframe(end, to, easing))
            return a.withTrack(p, track)
        }
        return when (preset) {
            TransitionPreset.FADE_IN -> key(TransformProperty.OPACITY, 0f, 1f, EasingType.EASE_IN_OUT, animation)
            TransitionPreset.FADE_OUT -> key(TransformProperty.OPACITY, 1f, 0f, EasingType.EASE_IN_OUT, animation)
            TransitionPreset.SLIDE_IN_LEFT -> key(TransformProperty.POSITION_X, -canvasWidth.toFloat(), 0f, EasingType.EASE_OUT, animation)
            TransitionPreset.SLIDE_IN_RIGHT -> key(TransformProperty.POSITION_X, canvasWidth.toFloat(), 0f, EasingType.EASE_OUT, animation)
            TransitionPreset.SLIDE_IN_TOP -> key(TransformProperty.POSITION_Y, -canvasHeight.toFloat(), 0f, EasingType.EASE_OUT, animation)
            TransitionPreset.SLIDE_IN_BOTTOM -> key(TransformProperty.POSITION_Y, canvasHeight.toFloat(), 0f, EasingType.EASE_OUT, animation)
            TransitionPreset.ZOOM_IN -> key(TransformProperty.SCALE, 0.1f, 1f, EasingType.EASE_OUT_BACK, animation)
            TransitionPreset.ZOOM_OUT -> key(TransformProperty.SCALE, 1f, 0.1f, EasingType.EASE_IN, animation)
            TransitionPreset.SPIN_IN -> {
                val rotated = key(TransformProperty.ROTATION, -360f, 0f, EasingType.EASE_OUT, animation)
                key(TransformProperty.SCALE, 0.1f, 1f, EasingType.EASE_OUT, rotated)
            }
        }
    }
}
