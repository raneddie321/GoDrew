package com.godraw.core.timeline

import com.godraw.core.model.Frame

/**
 * Maps between wall-clock time and frames. One tick lasts 1/fps seconds; a frame with hold = n
 * occupies n ticks. All math uses integer microseconds for drift-free long playback.
 */
class PlaybackClock(frames: List<Frame>, fps: Int) {

    val fps: Int = fps.coerceIn(1, 120)
    private val holds: IntArray = IntArray(frames.size) { frames[it].hold.coerceAtLeast(1) }
    private val startTicks: IntArray = IntArray(frames.size).also { arr ->
        var acc = 0
        for (i in holds.indices) {
            arr[i] = acc
            acc += holds[i]
        }
    }
    val totalTicks: Int = holds.sum()
    val tickDurationUs: Long get() = 1_000_000L / fps

    val totalDurationUs: Long get() = ticksToUs(totalTicks)
    val totalDurationMs: Long get() = totalDurationUs / 1000

    fun ticksToUs(ticks: Int): Long = ticks * 1_000_000L / fps

    fun frameStartUs(index: Int): Long = ticksToUs(startTicks[index])
    fun frameStartTick(index: Int): Int = startTicks[index]
    fun frameDurationUs(index: Int): Long = ticksToUs(startTicks[index] + holds[index]) - frameStartUs(index)

    /** Index of the frame visible at [timeUs]; wraps when [loop] is true, otherwise clamps. */
    fun frameAt(timeUs: Long, loop: Boolean): Int {
        if (holds.isEmpty()) return 0
        val total = totalDurationUs
        val t = when {
            total <= 0 -> 0L
            loop -> ((timeUs % total) + total) % total
            else -> timeUs.coerceIn(0, total - 1)
        }
        val tick = (t * fps / 1_000_000L).toInt()
        return frameAtTick(tick)
    }

    fun frameAtTick(tick: Int): Int {
        if (holds.isEmpty()) return 0
        var lo = 0
        var hi = startTicks.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (startTicks[mid] <= tick) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun isFinished(timeUs: Long): Boolean = timeUs >= totalDurationUs
}
