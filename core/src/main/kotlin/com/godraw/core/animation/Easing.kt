package com.godraw.core.animation

import com.godraw.core.entitlement.Feature
import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** Classic easing curves (Robert Penner style). All map t ∈ [0, 1] to progress. */
@Serializable
enum class EasingType(val requiredFeature: Feature?) {
    LINEAR(null),
    EASE_IN(null),
    EASE_OUT(null),
    EASE_IN_OUT(null),
    STEP(Feature.ADVANCED_EASING),
    EASE_IN_CUBIC(Feature.ADVANCED_EASING),
    EASE_OUT_CUBIC(Feature.ADVANCED_EASING),
    EASE_IN_BACK(Feature.ADVANCED_EASING),
    EASE_OUT_BACK(Feature.ADVANCED_EASING),
    ELASTIC(Feature.CUSTOM_BEZIER_EASING),
    BOUNCE(Feature.CUSTOM_BEZIER_EASING),
    CUSTOM_BEZIER(Feature.CUSTOM_BEZIER_EASING),
}

object Easing {

    private const val BACK = 1.70158f

    fun apply(type: EasingType, t: Float, bezier: List<Float>? = null): Float {
        val x = t.coerceIn(0f, 1f)
        return when (type) {
            EasingType.LINEAR -> x
            EasingType.EASE_IN -> x * x
            EasingType.EASE_OUT -> 1f - (1f - x) * (1f - x)
            EasingType.EASE_IN_OUT -> if (x < 0.5f) 2f * x * x else 1f - (-2f * x + 2f).pow(2) / 2f
            EasingType.STEP -> if (x < 1f) 0f else 1f
            EasingType.EASE_IN_CUBIC -> x * x * x
            EasingType.EASE_OUT_CUBIC -> 1f - (1f - x).pow(3)
            EasingType.EASE_IN_BACK -> (BACK + 1f) * x * x * x - BACK * x * x
            EasingType.EASE_OUT_BACK -> {
                val u = x - 1f
                1f + (BACK + 1f) * u * u * u + BACK * u * u
            }
            EasingType.ELASTIC -> elasticOut(x)
            EasingType.BOUNCE -> bounceOut(x)
            EasingType.CUSTOM_BEZIER -> {
                val p = bezier
                if (p == null || p.size < 4) x else cubicBezier(p[0], p[1], p[2], p[3], x)
            }
        }
    }

    private fun elasticOut(x: Float): Float {
        if (x == 0f || x == 1f) return x
        val c4 = (2.0 * PI / 3.0)
        return (2.0.pow(-10.0 * x) * sin((x * 10.0 - 0.75) * c4) + 1.0).toFloat()
    }

    fun bounceOut(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> { val u = x - 1.5f / d1; n1 * u * u + 0.75f }
            x < 2.5f / d1 -> { val u = x - 2.25f / d1; n1 * u * u + 0.9375f }
            else -> { val u = x - 2.625f / d1; n1 * u * u + 0.984375f }
        }
    }

    /**
     * CSS-style cubic Bézier timing function with endpoints (0,0) and (1,1).
     * Solves x(s) = t with Newton–Raphson, falling back to bisection.
     */
    fun cubicBezier(x1: Float, y1: Float, x2: Float, y2: Float, t: Float): Float {
        val cx1 = x1.coerceIn(0f, 1f)
        val cx2 = x2.coerceIn(0f, 1f)
        fun bx(s: Float) = 3f * (1 - s) * (1 - s) * s * cx1 + 3f * (1 - s) * s * s * cx2 + s * s * s
        fun by(s: Float) = 3f * (1 - s) * (1 - s) * s * y1 + 3f * (1 - s) * s * s * y2 + s * s * s
        fun dx(s: Float) = 3f * (1 - s) * (1 - s) * cx1 + 6f * (1 - s) * s * (cx2 - cx1) + 3f * s * s * (1 - cx2)

        var s = t
        repeat(8) {
            val err = bx(s) - t
            if (abs(err) < 1e-5f) return by(s)
            val d = dx(s)
            if (abs(d) < 1e-6f) return@repeat
            s = (s - err / d).coerceIn(0f, 1f)
        }
        var lo = 0f
        var hi = 1f
        s = t
        repeat(30) {
            val v = bx(s)
            if (abs(v - t) < 1e-5f) return by(s)
            if (v < t) lo = s else hi = s
            s = (lo + hi) / 2f
        }
        return by(s)
    }
}
