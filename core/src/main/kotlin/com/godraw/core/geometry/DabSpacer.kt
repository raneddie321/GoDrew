package com.godraw.core.geometry

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/** A single brush stamp along a stroke. */
data class Dab(val x: Float, val y: Float, val pressure: Float, val angle: Float)

/**
 * Converts a stream of stroke points into evenly spaced dabs. Spacing is a fraction of the
 * current (pressure-scaled) brush diameter, so fast and slow strokes look identical.
 */
class DabSpacer(
    private val spacingFraction: Float,
    private val diameterAt: (pressure: Float) -> Float,
) {
    private var last: StrokePoint? = null
    private var distanceToNext = 0f

    fun reset() {
        last = null
        distanceToNext = 0f
    }

    fun add(p: StrokePoint): List<Dab> {
        val prev = last
        if (prev == null) {
            last = p
            distanceToNext = spacing(p.pressure)
            return listOf(Dab(p.x, p.y, p.pressure, 0f))
        }
        val dx = p.x - prev.x
        val dy = p.y - prev.y
        val segment = hypot(dx, dy)
        if (segment < 1e-4f) return emptyList()
        val angle = atan2(dy, dx)
        val out = ArrayList<Dab>()
        var travelled = 0f
        while (travelled + distanceToNext <= segment) {
            travelled += distanceToNext
            val t = travelled / segment
            val pressure = prev.pressure + (p.pressure - prev.pressure) * t
            out += Dab(prev.x + dx * t, prev.y + dy * t, pressure, angle)
            distanceToNext = spacing(pressure)
        }
        distanceToNext -= segment - travelled
        last = p
        return out
    }

    private fun spacing(pressure: Float): Float = max(0.5f, diameterAt(pressure) * spacingFraction)
}
