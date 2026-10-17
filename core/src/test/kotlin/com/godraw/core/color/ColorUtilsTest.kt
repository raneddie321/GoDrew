package com.godraw.core.color

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorUtilsTest {

    @Test
    fun hexParsingAndFormatting() {
        assertEquals(0xFFFF8800.toInt(), ColorUtils.parseHex("#FF8800"))
        assertEquals(0xFFFF8800.toInt(), ColorUtils.parseHex("ff8800"))
        assertEquals(0x80FF8800.toInt(), ColorUtils.parseHex("#80FF8800"))
        assertEquals(0xFFFFAA00.toInt(), ColorUtils.parseHex("#FA0"))
        assertNull(ColorUtils.parseHex("#GG0000"))
        assertNull(ColorUtils.parseHex("12345"))
        assertEquals("#FF8800", ColorUtils.toHex(0xFFFF8800.toInt()))
        assertEquals("#80FF8800", ColorUtils.toHex(0x80FF8800.toInt()))
    }

    @Test
    fun hsvRoundTrip() {
        listOf(0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF123456, 0xFFABCDEF, 0xFF808080).forEach { c ->
            val hsv = ColorUtils.toHsv(c.toInt())
            assertEquals(c.toInt(), ColorUtils.fromHsv(hsv[0], hsv[1], hsv[2]))
        }
    }

    @Test
    fun hslRoundTrip() {
        val out = FloatArray(3)
        listOf(0xFF336699, 0xFFE04040, 0xFF20C060).forEach { c ->
            val v = c.toInt()
            ColorUtils.toHsl(ColorUtils.red(v), ColorUtils.green(v), ColorUtils.blue(v), out)
            val back = ColorUtils.fromHsl(out[0], out[1], out[2], 255)
            assertEquals(ColorUtils.red(v).toFloat(), ColorUtils.red(back).toFloat(), 1f)
            assertEquals(ColorUtils.green(v).toFloat(), ColorUtils.green(back).toFloat(), 1f)
            assertEquals(ColorUtils.blue(v).toFloat(), ColorUtils.blue(back).toFloat(), 1f)
        }
    }
}
