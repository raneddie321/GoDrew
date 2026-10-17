package com.godraw.core.raster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FloodFillTest {

    private val black = 0xFF000000.toInt()
    private val red = 0xFFFF0000.toInt()

    /** 10x10 transparent canvas with a closed black square outline from (2,2) to (7,7). */
    private fun boxed(): IntArray {
        val px = IntArray(100)
        for (i in 2..7) {
            px[2 * 10 + i] = black; px[7 * 10 + i] = black
            px[i * 10 + 2] = black; px[i * 10 + 7] = black
        }
        return px
    }

    @Test
    fun fillStaysInsideClosedShape() {
        val ref = boxed()
        val target = IntArray(100)
        val rect = FloodFill.fill(target, ref, 10, 10, 5, 5, red, tolerance = 0, expand = 0)
        assertEquals(IntRect(3, 3, 7, 7), rect)
        assertEquals(red, target[5 * 10 + 5])
        assertEquals(0, target[0])
        assertEquals(0, target[2 * 10 + 2]) // outline untouched
        assertEquals(16, target.count { it == red })
    }

    @Test
    fun expandGrowsUnderLines() {
        val target = IntArray(100)
        FloodFill.fill(target, boxed(), 10, 10, 5, 5, red, tolerance = 0, expand = 1)
        assertEquals(red, target[2 * 10 + 4])
    }

    @Test
    fun outsideStartReturnsNull() {
        assertNull(FloodFill.fill(IntArray(100), IntArray(100), 10, 10, 20, 3, red))
    }

    @Test
    fun toleranceIncludesSimilarColors() {
        val ref = IntArray(4) { 0xFF101010.toInt() }
        ref[3] = 0xFF181818.toInt()
        val target = IntArray(4)
        FloodFill.fill(target, ref, 2, 2, 0, 0, red, tolerance = 4, expand = 0)
        assertEquals(3, target.count { it == red })
        FloodFill.fill(target, ref, 2, 2, 0, 0, red, tolerance = 10, expand = 0)
        assertEquals(4, target.count { it == red })
    }

    @Test
    fun blendOverHalfTransparent() {
        val c = FloodFill.blendOver(0xFF0000FF.toInt(), 0x80FF0000.toInt())
        assertEquals(255, c ushr 24)
        assertEquals(128, (c shr 16) and 0xFF)
        assertEquals(127, c and 0xFF)
    }
}
