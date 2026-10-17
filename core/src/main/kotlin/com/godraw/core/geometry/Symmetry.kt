package com.godraw.core.geometry

import com.godraw.core.model.SymmetryMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Produces mirrored copies of points for symmetry drawing around a canvas center. */
class Symmetry(
    val mode: SymmetryMode,
    val centerX: Float,
    val centerY: Float,
    segments: Int = 6,
) {
    val segments: Int = segments.coerceIn(2, 32)

    /** Number of strokes produced for one input stroke (including the original). */
    val copies: Int
        get() = when (mode) {
            SymmetryMode.NONE -> 1
            SymmetryMode.VERTICAL, SymmetryMode.HORIZONTAL -> 2
            SymmetryMode.QUADRANT -> 4
            SymmetryMode.RADIAL -> segments
        }

    /** Returns the point for symmetry copy [index] (0 is always the original). */
    fun map(index: Int, x: Float, y: Float): Pair<Float, Float> {
        if (index == 0) return x to y
        return when (mode) {
            SymmetryMode.NONE -> x to y
            SymmetryMode.VERTICAL -> (2 * centerX - x) to y
            SymmetryMode.HORIZONTAL -> x to (2 * centerY - y)
            SymmetryMode.QUADRANT -> when (index) {
                1 -> (2 * centerX - x) to y
                2 -> x to (2 * centerY - y)
                else -> (2 * centerX - x) to (2 * centerY - y)
            }
            SymmetryMode.RADIAL -> {
                val angle = 2.0 * PI * index / segments
                val c = cos(angle).toFloat()
                val s = sin(angle).toFloat()
                val dx = x - centerX
                val dy = y - centerY
                (centerX + dx * c - dy * s) to (centerY + dx * s + dy * c)
            }
        }
    }

    /** Whether symmetry copy [index] is mirrored (affects asymmetric tips such as calligraphy). */
    fun isMirrored(index: Int): Boolean = when (mode) {
        SymmetryMode.VERTICAL, SymmetryMode.HORIZONTAL -> index == 1
        SymmetryMode.QUADRANT -> index == 1 || index == 2
        else -> false
    }
}
