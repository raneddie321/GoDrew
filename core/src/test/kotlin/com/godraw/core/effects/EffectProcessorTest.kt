package com.godraw.core.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectProcessorTest {

    private val w = 24
    private val h = 16

    /** A red square with a highlight on transparency plus a gradient row, enough to exercise every effect. */
    private fun sample(): IntArray = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        when {
            x in 9..11 && y in 6..8 -> 0xFFFFFFF0.toInt() // highlight
            x in 6..15 && y in 4..11 -> 0xFFE04040.toInt()
            y == 14 -> (0xFF shl 24) or ((x * 10) shl 16) or ((x * 5) shl 8) or (255 - x * 10)
            else -> 0
        }
    }

    @Test
    fun everyEffectProducesSameSizeOutputWithoutMutatingInput() {
        val src = sample()
        val copy = src.copyOf()
        EffectType.entries.forEach { type ->
            val out = EffectProcessor.apply(type, src, w, h)
            assertEquals("$type size", src.size, out.size)
            assertTrue("$type input untouched", src.contentEquals(copy))
        }
    }

    @Test
    fun everyEffectChangesTheImage() {
        val src = sample()
        EffectType.entries.forEach { type ->
            val out = EffectProcessor.apply(type, src, w, h)
            assertTrue("$type should change pixels", !out.contentEquals(src))
        }
    }

    @Test
    fun invertTwiceIsIdentity() {
        val src = sample()
        val twice = EffectProcessor.apply(EffectType.INVERT, EffectProcessor.apply(EffectType.INVERT, src, w, h), w, h)
        assertTrue(src.contentEquals(twice))
    }

    @Test
    fun grayscaleProducesNeutralColors() {
        val out = EffectProcessor.apply(EffectType.GRAYSCALE, sample(), w, h)
        out.filter { (it ushr 24) > 0 }.forEach { c ->
            val r = (c shr 16) and 0xFF
            assertEquals(r, (c shr 8) and 0xFF)
            assertEquals(r, c and 0xFF)
        }
    }

    @Test
    fun blurSpreadsAlphaOutward() {
        val src = sample()
        val out = EffectProcessor.apply(EffectType.BLUR, src, w, h, mapOf("radius" to 4f))
        val outside = 4 * w + 4 // two pixels left of the square
        assertEquals(0, src[outside] ushr 24)
        assertNotEquals(0, out[outside] ushr 24)
    }

    @Test
    fun dropShadowAddsPixelsBelowRight() {
        val out = EffectProcessor.apply(EffectType.DROP_SHADOW, sample(), w, h, mapOf("distance" to 4f, "blur" to 0f))
        assertTrue((out[12 * w + 17] ushr 24) > 0)
    }

    @Test
    fun transparentPixelsStayTransparentForColorAdjustments() {
        val out = EffectProcessor.apply(EffectType.BRIGHTNESS_CONTRAST, sample(), w, h)
        assertEquals(0, out[0] ushr 24)
    }
}
