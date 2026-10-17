package com.godraw.core.raster

import com.godraw.core.color.ColorUtils

/** Integer rectangle (right/bottom exclusive). */
data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0
}

/**
 * Scanline flood fill on ARGB pixel arrays.
 *
 * The region is computed on [reference] (for example the merged image, so lines on other layers
 * act as boundaries) and painted into [target]. Optionally the fill is grown by [expand] pixels
 * to tuck under anti-aliased line edges.
 */
object FloodFill {

    fun fill(
        target: IntArray,
        reference: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        color: Int,
        tolerance: Int = 32,
        expand: Int = 1,
    ): IntRect? {
        val mask = regionMask(reference, width, height, startX, startY, tolerance) ?: return null
        if (expand > 0) dilate(mask, width, height, expand)
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (mask[row + x]) {
                    target[row + x] = blendOver(target[row + x], color)
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < 0) null else IntRect(minX, minY, maxX + 1, maxY + 1)
    }

    /** Returns the contiguous region similar to the start pixel, or null if the start is outside. */
    fun regionMask(pixels: IntArray, width: Int, height: Int, startX: Int, startY: Int, tolerance: Int): BooleanArray? {
        if (startX !in 0 until width || startY !in 0 until height) return null
        val seed = pixels[startY * width + startX]
        val visited = BooleanArray(width * height)
        val stack = IntArray(width * height / 4 + 64).let { GrowableIntStack(it) }
        fun matches(i: Int) = !visited[i] && ColorUtils.distance(pixels[i], seed) <= tolerance

        stack.push(startY * width + startX)
        while (stack.isNotEmpty()) {
            val idx = stack.pop()
            if (!matches(idx)) continue
            val y = idx / width
            var x = idx % width
            // Walk to the left edge of this span.
            while (x > 0 && matches(y * width + x - 1)) x--
            var spanAbove = false
            var spanBelow = false
            while (x < width && matches(y * width + x)) {
                val i = y * width + x
                visited[i] = true
                if (y > 0) {
                    val up = i - width
                    if (matches(up)) {
                        if (!spanAbove) { stack.push(up); spanAbove = true }
                    } else spanAbove = false
                }
                if (y < height - 1) {
                    val down = i + width
                    if (matches(down)) {
                        if (!spanBelow) { stack.push(down); spanBelow = true }
                    } else spanBelow = false
                }
                x++
            }
        }
        return visited
    }

    fun dilate(mask: BooleanArray, width: Int, height: Int, radius: Int) {
        repeat(radius) {
            val copy = mask.copyOf()
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    if (copy[i]) continue
                    if ((x > 0 && copy[i - 1]) || (x < width - 1 && copy[i + 1]) ||
                        (y > 0 && copy[i - width]) || (y < height - 1 && copy[i + width])
                    ) {
                        mask[i] = true
                    }
                }
            }
        }
    }

    /** Source-over compositing of non-premultiplied colors. */
    fun blendOver(dst: Int, src: Int): Int {
        val sa = ColorUtils.alpha(src)
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = ColorUtils.alpha(dst)
        val outA = sa + da * (255 - sa) / 255
        if (outA == 0) return 0
        fun ch(s: Int, d: Int) = (s * sa + d * da * (255 - sa) / 255) / outA
        return ColorUtils.argb(
            outA,
            ch(ColorUtils.red(src), ColorUtils.red(dst)),
            ch(ColorUtils.green(src), ColorUtils.green(dst)),
            ch(ColorUtils.blue(src), ColorUtils.blue(dst)),
        )
    }

    private class GrowableIntStack(private var data: IntArray) {
        private var size = 0
        fun push(v: Int) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun pop(): Int = data[--size]
        fun isNotEmpty() = size > 0
    }
}
