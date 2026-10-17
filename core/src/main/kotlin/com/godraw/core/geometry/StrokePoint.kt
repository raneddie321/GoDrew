package com.godraw.core.geometry

/** A sampled input point in canvas coordinates. Pressure is 0..1; tilt in radians when available. */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val timeMs: Long = 0L,
    val tilt: Float = 0f,
    val orientation: Float = 0f,
)
