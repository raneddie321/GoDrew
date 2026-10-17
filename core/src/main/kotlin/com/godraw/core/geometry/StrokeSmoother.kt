package com.godraw.core.geometry

import kotlin.math.hypot

/**
 * Stroke stabilization combining two classic techniques:
 *  - "pulled string" (lazy brush): the pen drags the brush on a string of length [stringLength];
 *    the brush only moves once the string is taut, removing hand jitter;
 *  - exponential moving average of positions and pressure for additional smoothing.
 *
 * [amount] 0 disables stabilization; 1 is maximum.
 */
class StrokeSmoother(amount: Float, brushSize: Float = 10f) {

    private val amount = amount.coerceIn(0f, 1f)
    private val stringLength = this.amount * 40f + this.amount * brushSize * 0.25f
    private val alpha = 1f - this.amount * 0.85f

    private var lazyX = 0f
    private var lazyY = 0f
    private var smoothX = 0f
    private var smoothY = 0f
    private var smoothP = 0f
    private var started = false
    private var lastInput: StrokePoint? = null

    fun reset() {
        started = false
        lastInput = null
    }

    /** Feeds a raw point and returns the stabilized point (or null if the brush did not move). */
    fun add(p: StrokePoint): StrokePoint? {
        lastInput = p
        if (!started) {
            started = true
            lazyX = p.x; lazyY = p.y
            smoothX = p.x; smoothY = p.y; smoothP = p.pressure
            return p
        }
        if (amount <= 0f) return p

        val dx = p.x - lazyX
        val dy = p.y - lazyY
        val dist = hypot(dx, dy)
        if (dist > stringLength) {
            val move = (dist - stringLength) / dist
            lazyX += dx * move
            lazyY += dy * move
        } else {
            return null
        }
        smoothX += (lazyX - smoothX) * alpha
        smoothY += (lazyY - smoothY) * alpha
        smoothP += (p.pressure - smoothP) * alpha
        return p.copy(x = smoothX, y = smoothY, pressure = smoothP)
    }

    /** Returns points that bring the brush to the final pen location when the stroke ends. */
    fun finish(): List<StrokePoint> {
        val last = lastInput ?: return emptyList()
        if (amount <= 0f || !started) return emptyList()
        val out = ArrayList<StrokePoint>()
        var x = smoothX
        var y = smoothY
        var pr = smoothP
        // Ease out toward the last raw point so stabilized strokes do not end short.
        for (i in 1..6) {
            x += (last.x - x) * 0.5f
            y += (last.y - y) * 0.5f
            pr += (last.pressure - pr) * 0.5f
            out += last.copy(x = x, y = y, pressure = pr)
        }
        out += last
        return out
    }
}
