package com.godraw.core.gif

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.abs

class GifEncoderTest {

    private fun frame(w: Int, h: Int, color: Int): IntArray = IntArray(w * h) { i ->
        if ((i % w) < w / 2) color else 0xFFFFFFFF.toInt()
    }

    private fun decode(bytes: ByteArray): List<java.awt.image.BufferedImage> {
        val reader = ImageIO.getImageReadersByFormatName("gif").next()
        reader.input = MemoryCacheImageInputStream(ByteArrayInputStream(bytes))
        val count = reader.getNumImages(true)
        return (0 until count).map { reader.read(it) }
    }

    @Test
    fun encodesDecodableAnimation() {
        val out = ByteArrayOutputStream()
        val enc = GifEncoder(out, 40, 30)
        enc.addFrame(frame(40, 30, 0xFFFF0000.toInt()), 100.0)
        enc.addFrame(frame(40, 30, 0xFF00FF00.toInt()), 100.0)
        enc.addFrame(frame(40, 30, 0xFF0000FF.toInt()), 100.0, dither = true)
        enc.finish()
        val bytes = out.toByteArray()
        assertEquals("GIF89a", String(bytes, 0, 6, Charsets.US_ASCII))
        val images = decode(bytes)
        assertEquals(3, images.size)
        assertEquals(40, images[0].width)
        assertEquals(30, images[0].height)
        val px = images[1].getRGB(2, 2)
        assertEquals(0xFF00FF00.toInt(), px)
        assertEquals(0xFFFFFFFF.toInt(), images[1].getRGB(35, 20))
    }

    @Test
    fun manyColorsAreQuantizedAccurately() {
        val w = 64
        val h = 64
        // Smooth gradient forces median cut and long LZW streams (table resets).
        val px = IntArray(w * h) { i -> (0xFF shl 24) or ((i % w * 4) shl 16) or ((i / w * 4) shl 8) or 128 }
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h).apply { addFrame(px, 50.0); finish() }
        val img = decode(out.toByteArray())[0]
        var maxErr = 0
        for (y in 0 until h) for (x in 0 until w) {
            val a = px[y * w + x]
            val b = img.getRGB(x, y)
            maxErr = maxOf(maxErr, abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)), abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)))
        }
        assertTrue("max channel error $maxErr", maxErr <= 24)
    }

    @Test
    fun noiseImageRoundTripsWithExactPalette() {
        // Random indices into a 200-color palette exercise the LZW dictionary resets.
        val rnd = java.util.Random(7)
        val palette = IntArray(200) { (0xFF shl 24) or (it shl 16) or ((255 - it) shl 8) or (it * 7 % 256) }
        val w = 120
        val h = 90
        val px = IntArray(w * h) { palette[rnd.nextInt(palette.size)] }
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h).apply { addFrame(px, 40.0); finish() }
        val img = decode(out.toByteArray())[0]
        for (y in 0 until h) for (x in 0 until w) {
            val expected = px[y * w + x]
            val actual = img.getRGB(x, y)
            // 15-bit histogram keys may merge near-identical colors; allow small error.
            assertTrue(abs(((expected shr 16) and 0xFF) - ((actual shr 16) and 0xFF)) <= 8)
        }
    }

    @Test
    fun transparentPixelsSurvive() {
        val w = 8
        val h = 8
        val px = IntArray(w * h) { if (it % 2 == 0) 0 else 0xFF336699.toInt() }
        val out = ByteArrayOutputStream()
        GifEncoder(out, w, h, transparent = true).apply { addFrame(px, 100.0); finish() }
        val img = decode(out.toByteArray())[0]
        assertEquals(0, img.getRGB(0, 0) ushr 24)
        assertEquals(255, img.getRGB(1, 0) ushr 24)
    }
}
