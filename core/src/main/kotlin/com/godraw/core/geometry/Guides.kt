package com.godraw.core.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Geometry helpers for rulers and perspective guides. */
object Guides {

    /** Projects (x, y) onto the infinite line through (x1, y1)-(x2, y2). */
    fun projectOntoLine(x: Float, y: Float, x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float> {
        val dx = x2 - x1
        val dy = y2 - y1
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-6f) return x1 to y1
        val t = ((x - x1) * dx + (y - y1) * dy) / len2
        return (x1 + t * dx) to (y1 + t * dy)
    }

    /**
     * Perspective snapping: a stroke that starts at (sx, sy) is constrained to the line toward the
     * vanishing point that best matches the stroke's current direction. Horizontal and vertical
     * directions are also candidates so 2-point perspective verticals stay upright.
     */
    fun snapToPerspective(
        sx: Float,
        sy: Float,
        x: Float,
        y: Float,
        vanishingPoints: List<Pair<Float, Float>>,
        includeAxes: Boolean = true,
    ): Pair<Float, Float> {
        val dx = x - sx
        val dy = y - sy
        if (hypot(dx, dy) < 4f) return sx to sy
        val strokeAngle = atan2(dy, dx)
        val candidates = ArrayList<Float>()
        vanishingPoints.forEach { (vx, vy) -> candidates += atan2(vy - sy, vx - sx) }
        if (includeAxes) {
            candidates += 0f
            candidates += (Math.PI / 2).toFloat()
        }
        if (candidates.isEmpty()) return x to y
        val best = candidates.minBy { lineAngleDistance(it, strokeAngle) }
        val ex = sx + cos(best)
        val ey = sy + sin(best)
        return projectOntoLine(x, y, sx, sy, ex, ey)
    }

    /** Smallest angle between two undirected lines. */
    fun lineAngleDistance(a: Float, b: Float): Float {
        var d = abs(a - b) % Math.PI.toFloat()
        if (d > Math.PI.toFloat() / 2) d = Math.PI.toFloat() - d
        return d
    }

    /** Snaps an angle (radians) to the nearest multiple of [stepDegrees]. */
    fun snapAngle(angle: Float, stepDegrees: Float = 15f): Float {
        val step = Math.toRadians(stepDegrees.toDouble()).toFloat()
        return Math.round(angle / step) * step
    }
}
