package com.godraw.core.animation

import com.godraw.core.model.Keyframe
import com.godraw.core.model.LayerAnimation
import com.godraw.core.model.MotionPathMode
import com.godraw.core.model.TransformProperty

/** A layer's resolved transform at a given time. */
data class LayerTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Float = 0f,
    val scale: Float = 1f,
    val opacity: Float = 1f,
) {
    val isIdentity: Boolean get() = x == 0f && y == 0f && rotation == 0f && scale == 1f && opacity == 1f

    companion object {
        val IDENTITY = LayerTransform()
    }
}

/**
 * Evaluates keyframe tracks with classical interpolation: the segment between two keyframes is
 * blended with the start keyframe's easing curve. Values before the first / after the last
 * keyframe are held. With [MotionPathMode.SMOOTH], position tracks use a Catmull-Rom spline so
 * motion paths curve through every keyframe instead of turning sharply.
 */
object KeyframeSampler {

    fun sample(track: List<Keyframe>, frame: Float, default: Float, smooth: Boolean = false): Float {
        if (track.isEmpty()) return default
        if (track.size == 1 || frame <= track.first().frame) return track.first().value
        if (frame >= track.last().frame) return track.last().value

        val nextIndex = track.indexOfFirst { it.frame > frame }
        val i = nextIndex - 1
        val a = track[i]
        val b = track[nextIndex]
        val span = (b.frame - a.frame).toFloat()
        val local = if (span <= 0f) 1f else (frame - a.frame) / span
        val eased = Easing.apply(a.easing, local, a.bezier)

        if (!smooth || track.size < 3) return lerp(a.value, b.value, eased)

        val p0 = if (i > 0) track[i - 1].value else a.value - (b.value - a.value)
        val p3 = if (nextIndex + 1 < track.size) track[nextIndex + 1].value else b.value + (b.value - a.value)
        return catmullRom(p0, a.value, b.value, p3, eased)
    }

    fun sample(animation: LayerAnimation, frame: Float): LayerTransform {
        if (!animation.isAnimated) return LayerTransform.IDENTITY
        val smooth = animation.pathMode == MotionPathMode.SMOOTH
        fun value(p: TransformProperty, smoothTrack: Boolean = false) =
            sample(animation.track(p), frame, p.defaultValue, smoothTrack)
        return LayerTransform(
            x = value(TransformProperty.POSITION_X, smooth),
            y = value(TransformProperty.POSITION_Y, smooth),
            rotation = value(TransformProperty.ROTATION),
            scale = value(TransformProperty.SCALE).coerceAtLeast(0.01f),
            opacity = value(TransformProperty.OPACITY).coerceIn(0f, 1f),
        )
    }

    /** Points along the position motion path, for drawing it on the canvas. */
    fun motionPath(animation: LayerAnimation, steps: Int = 4): List<Pair<Float, Float>> {
        val frames = (animation.track(TransformProperty.POSITION_X) + animation.track(TransformProperty.POSITION_Y))
            .map { it.frame }
        if (frames.isEmpty()) return emptyList()
        val first = frames.min()
        val last = frames.max()
        if (first == last) return emptyList()
        val result = ArrayList<Pair<Float, Float>>()
        val total = (last - first) * steps
        for (s in 0..total) {
            val t = sample(animation, first + s / steps.toFloat())
            result += t.x to t.y
        }
        return result
    }

    /** Inserts or replaces a keyframe at [frame]. */
    fun upsert(track: List<Keyframe>, keyframe: Keyframe): List<Keyframe> =
        (track.filterNot { it.frame == keyframe.frame } + keyframe).sortedBy { it.frame }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * ((2f * p1) + (-p0 + p2) * t + (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2 + (-p0 + 3f * p1 - 3f * p2 + p3) * t3)
    }
}
