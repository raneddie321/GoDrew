package com.godraw.core.gif

import java.io.OutputStream

/**
 * Animated GIF89a encoder. Each frame gets its own optimized local palette (median cut), an
 * optional transparent index and optional Floyd–Steinberg dithering. Frames are streamed to
 * [out] so memory stays bounded by one frame.
 */
class GifEncoder(
    private val out: OutputStream,
    val width: Int,
    val height: Int,
    private val loop: Boolean = true,
    private val transparent: Boolean = false,
) {
    init {
        require(width in 1..65535 && height in 1..65535) { "Invalid GIF size" }
    }

    private var started = false
    private var finished = false
    private var delayRemainder = 0.0
    var frameCount = 0
        private set

    /** Adds a frame of non-premultiplied ARGB pixels displayed for [delayMs] milliseconds. */
    fun addFrame(argb: IntArray, delayMs: Double, dither: Boolean = false) {
        check(!finished) { "Encoder already finished" }
        require(argb.size == width * height) { "Frame size mismatch" }
        if (!started) writeHeader()

        val quantizer = ColorQuantizer(argb, if (transparent) 255 else 256)
        val palette = quantizer.palette
        val transparentIndex = if (transparent) palette.size else -1
        val colorCount = palette.size + if (transparent) 1 else 0
        var bits = 1
        while ((1 shl bits) < colorCount.coerceAtLeast(2)) bits++
        val tableSize = 1 shl bits

        val indices = if (dither) ditherIndices(argb, quantizer, transparentIndex) else IntArray(argb.size) { i ->
            val c = argb[i]
            if (transparent && (c ushr 24) < 128) transparentIndex else quantizer.indexOf(c)
        }

        // GIF delays are in centiseconds; carry rounding error forward to keep long animations in sync.
        val exact = delayMs / 10.0 + delayRemainder
        val delayCs = exact.toInt().coerceAtLeast(2)
        delayRemainder = exact - delayCs

        // Graphic Control Extension
        out.write(0x21); out.write(0xF9); out.write(4)
        val disposal = if (transparent) 2 else 1
        out.write((disposal shl 2) or (if (transparent) 1 else 0))
        writeShort(delayCs)
        out.write(if (transparent) transparentIndex else 0)
        out.write(0)

        // Image Descriptor with local color table
        out.write(0x2C)
        writeShort(0); writeShort(0); writeShort(width); writeShort(height)
        out.write(0x80 or (bits - 1))
        for (i in 0 until tableSize) {
            val c = if (i < palette.size) palette[i] else 0
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        LzwEncoder(indices, bits.coerceAtLeast(2)).encode(out)
        frameCount++
    }

    fun finish() {
        if (finished) return
        if (!started) writeHeader()
        out.write(0x3B)
        out.flush()
        finished = true
    }

    private fun writeHeader() {
        started = true
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(width); writeShort(height)
        out.write(0x00) // no global color table
        out.write(0); out.write(0)
        if (loop) {
            out.write(0x21); out.write(0xFF); out.write(11)
            out.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
            out.write(3); out.write(1); writeShort(0); out.write(0)
        }
    }

    private fun writeShort(v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
    }

    private fun ditherIndices(argb: IntArray, q: ColorQuantizer, transparentIndex: Int): IntArray {
        val n = argb.size
        val er = FloatArray(n); val eg = FloatArray(n); val eb = FloatArray(n)
        val result = IntArray(n)
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            val c = argb[i]
            if (transparentIndex >= 0 && (c ushr 24) < 128) { result[i] = transparentIndex; continue }
            val r = (((c shr 16) and 0xFF) + er[i]).toInt().coerceIn(0, 255)
            val g = (((c shr 8) and 0xFF) + eg[i]).toInt().coerceIn(0, 255)
            val b = ((c and 0xFF) + eb[i]).toInt().coerceIn(0, 255)
            val idx = q.indexOf((r shl 16) or (g shl 8) or b)
            result[i] = idx
            val p = q.palette[idx]
            val dr = r - ((p shr 16) and 0xFF)
            val dg = g - ((p shr 8) and 0xFF)
            val db = b - (p and 0xFF)
            fun spread(xx: Int, yy: Int, f: Float) {
                if (xx !in 0 until width || yy >= height) return
                val j = yy * width + xx
                er[j] += dr * f; eg[j] += dg * f; eb[j] += db * f
            }
            spread(x + 1, y, 7 / 16f)
            spread(x - 1, y + 1, 3 / 16f)
            spread(x, y + 1, 5 / 16f)
            spread(x + 1, y + 1, 1 / 16f)
        }
        return result
    }
}

/** Variable-length-code LZW compressor as specified by GIF89a. */
internal class LzwEncoder(private val pixels: IntArray, private val minCodeSize: Int) {

    private val block = ByteArray(255)
    private var blockLen = 0
    private var bitBuffer = 0
    private var bitCount = 0

    fun encode(out: OutputStream) {
        out.write(minCodeSize)
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        var codeSize = minCodeSize + 1
        var nextCode = eoi + 1
        // Dictionary keyed by (prefixCode shl 8) | pixel.
        val table = HashMap<Int, Int>(8192)

        writeCode(out, clear, codeSize)
        if (pixels.isEmpty()) {
            writeCode(out, eoi, codeSize)
            flush(out)
            return
        }
        var prefix = pixels[0]
        for (i in 1 until pixels.size) {
            val k = pixels[i]
            val key = (prefix shl 8) or k
            val existing = table[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            writeCode(out, prefix, codeSize)
            if (nextCode < 4096) {
                table[key] = nextCode++
                if (nextCode > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                writeCode(out, clear, codeSize)
                table.clear()
                codeSize = minCodeSize + 1
                nextCode = eoi + 1
            }
            prefix = k
        }
        writeCode(out, prefix, codeSize)
        writeCode(out, eoi, codeSize)
        flush(out)
    }

    private fun writeCode(out: OutputStream, code: Int, size: Int) {
        bitBuffer = bitBuffer or (code shl bitCount)
        bitCount += size
        while (bitCount >= 8) {
            addByte(out, bitBuffer and 0xFF)
            bitBuffer = bitBuffer ushr 8
            bitCount -= 8
        }
    }

    private fun addByte(out: OutputStream, b: Int) {
        block[blockLen++] = b.toByte()
        if (blockLen == 255) {
            out.write(255)
            out.write(block, 0, 255)
            blockLen = 0
        }
    }

    private fun flush(out: OutputStream) {
        if (bitCount > 0) {
            addByte(out, bitBuffer and 0xFF)
            bitBuffer = 0
            bitCount = 0
        }
        if (blockLen > 0) {
            out.write(blockLen)
            out.write(block, 0, blockLen)
            blockLen = 0
        }
        out.write(0)
    }
}
