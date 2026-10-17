package com.godraw.core.gif

/**
 * Median-cut color quantizer working on a 15-bit (5:5:5) histogram. Produces up to [maxColors]
 * palette entries and a fast 32K lookup table mapping every 15-bit color to its nearest entry.
 */
class ColorQuantizer(pixels: IntArray, maxColors: Int = 255) {

    val palette: IntArray
    private val lookup = IntArray(32768) { -1 }

    init {
        val histogram = IntArray(32768)
        for (c in pixels) {
            if ((c ushr 24) < 128) continue
            histogram[key(c)]++
        }
        val colors = histogram.indices.filter { histogram[it] > 0 }
        palette = if (colors.size <= maxColors) {
            colors.map { expand(it) }.toIntArray()
        } else {
            medianCut(colors, histogram, maxColors)
        }
    }

    private fun key(c: Int): Int = ((c shr 9) and 0x7C00) or ((c shr 6) and 0x03E0) or ((c shr 3) and 0x001F)

    private fun expand(k: Int): Int {
        val r = (k shr 10) and 31
        val g = (k shr 5) and 31
        val b = k and 31
        return ((r shl 3) or (r shr 2)) shl 16 or (((g shl 3) or (g shr 2)) shl 8) or ((b shl 3) or (b shr 2))
    }

    private class Box(val colors: MutableList<Int>)

    private fun medianCut(colors: List<Int>, histogram: IntArray, maxColors: Int): IntArray {
        val boxes = mutableListOf(Box(colors.toMutableList()))
        while (boxes.size < maxColors) {
            // Split the box with the widest channel range (weighted by population).
            var bestIndex = -1
            var bestScore = -1L
            for ((i, box) in boxes.withIndex()) {
                if (box.colors.size < 2) continue
                val (range, _) = widestChannel(box)
                val pop = box.colors.sumOf { histogram[it].toLong() }
                val score = range.toLong() * pop
                if (score > bestScore) { bestScore = score; bestIndex = i }
            }
            if (bestIndex < 0) break
            val box = boxes.removeAt(bestIndex)
            val (_, channel) = widestChannel(box)
            val shift = when (channel) { 0 -> 10; 1 -> 5; else -> 0 }
            box.colors.sortBy { (it shr shift) and 31 }
            // Split at the population median.
            val total = box.colors.sumOf { histogram[it].toLong() }
            var acc = 0L
            var split = 1
            for ((i, c) in box.colors.withIndex()) {
                acc += histogram[c]
                if (acc * 2 >= total) { split = (i + 1).coerceIn(1, box.colors.size - 1); break }
            }
            boxes += Box(box.colors.subList(0, split).toMutableList())
            boxes += Box(box.colors.subList(split, box.colors.size).toMutableList())
        }
        return boxes.map { box ->
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (c in box.colors) {
                val count = histogram[c].toLong()
                val e = expand(c)
                r += ((e shr 16) and 0xFF) * count
                g += ((e shr 8) and 0xFF) * count
                b += (e and 0xFF) * count
                n += count
            }
            if (n == 0L) 0 else ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
        }.toIntArray()
    }

    private fun widestChannel(box: Box): Pair<Int, Int> {
        var minR = 31; var maxR = 0; var minG = 31; var maxG = 0; var minB = 31; var maxB = 0
        for (c in box.colors) {
            val r = (c shr 10) and 31; val g = (c shr 5) and 31; val b = c and 31
            if (r < minR) minR = r; if (r > maxR) maxR = r
            if (g < minG) minG = g; if (g > maxG) maxG = g
            if (b < minB) minB = b; if (b > maxB) maxB = b
        }
        val rr = maxR - minR; val gr = maxG - minG; val br = maxB - minB
        return when {
            gr >= rr && gr >= br -> gr to 1
            rr >= br -> rr to 0
            else -> br to 2
        }
    }

    /** Index of the palette entry nearest to RGB color [c]. */
    fun indexOf(c: Int): Int {
        val k = key(c)
        val cached = lookup[k]
        if (cached >= 0) return cached
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        var best = 0
        var bestD = Int.MAX_VALUE
        for (i in palette.indices) {
            val p = palette[i]
            val dr = r - ((p shr 16) and 0xFF)
            val dg = g - ((p shr 8) and 0xFF)
            val db = b - (p and 0xFF)
            val d = 2 * dr * dr + 4 * dg * dg + 3 * db * db
            if (d < bestD) { bestD = d; best = i }
        }
        lookup[k] = best
        return best
    }
}
