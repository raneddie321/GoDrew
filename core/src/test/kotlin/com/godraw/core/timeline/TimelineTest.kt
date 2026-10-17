package com.godraw.core.timeline

import com.godraw.core.model.Frame
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelineTest {

    private var counter = 0
    private val ids: () -> String = { "n${counter++}" }
    private fun frames(n: Int) = (0 until n).map { Frame("f$it") }

    @Test
    fun insertBlankAfterIndex() {
        val r = FrameOps.insertBlank(frames(3), 0, ids)
        assertEquals(listOf("f0", "n0", "f1", "f2"), r.frames.map { it.id })
        assertEquals(1, r.activeIndex)
    }

    @Test
    fun duplicateCopiesCellsAndHold() {
        val src = listOf(Frame("a", 3), Frame("b"))
        val r = FrameOps.duplicate(src, listOf(0), ids)
        assertEquals(listOf("a", "n0", "b"), r.frames.map { it.id })
        assertEquals(3, r.frames[1].hold)
        assertEquals(listOf("a" to "n0"), r.cellCopies)
    }

    @Test
    fun deleteKeepsAtLeastOneFrame() {
        val r = FrameOps.delete(frames(2), listOf(0, 1), ids)
        assertEquals(1, r.frames.size)
        assertEquals(listOf("f0", "f1"), r.removedFrameIds)
        val r2 = FrameOps.delete(frames(4), listOf(2), ids)
        assertEquals(listOf("f0", "f1", "f3"), r2.frames.map { it.id })
        assertEquals(1, r2.activeIndex)
    }

    @Test
    fun moveSelection() {
        val r = FrameOps.move(frames(5), listOf(0, 1), 3)
        assertEquals(listOf("f2", "f3", "f4", "f0", "f1"), r.frames.map { it.id })
        val r2 = FrameOps.move(frames(5), listOf(4), 0)
        assertEquals(listOf("f4", "f0", "f1", "f2", "f3"), r2.frames.map { it.id })
    }

    @Test
    fun pasteInsertsCopies() {
        val r = FrameOps.paste(frames(2), listOf(Frame("f0")), 1, ids)
        assertEquals(listOf("f0", "f1", "n0"), r.frames.map { it.id })
        assertEquals(listOf("f0" to "n0"), r.cellCopies)
    }

    @Test
    fun playbackClockRespectsHoldsAndLooping() {
        val clock = PlaybackClock(listOf(Frame("a", 1), Frame("b", 2), Frame("c", 1)), 4)
        assertEquals(4, clock.totalTicks)
        assertEquals(1_000_000L, clock.totalDurationUs)
        assertEquals(0, clock.frameAt(0, loop = true))
        assertEquals(1, clock.frameAt(250_000, loop = true))
        assertEquals(1, clock.frameAt(700_000, loop = true))
        assertEquals(2, clock.frameAt(750_000, loop = true))
        assertEquals(0, clock.frameAt(1_000_000, loop = true))
        assertEquals(2, clock.frameAt(5_000_000, loop = false))
        assertEquals(500_000L, clock.frameDurationUs(1))
    }
}
