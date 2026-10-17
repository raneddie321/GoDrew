package com.godraw.core.effects

import com.godraw.core.color.ColorUtils
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Applies [EffectType]s to non-premultiplied ARGB pixel buffers. Every effect returns a new
 * array of the same size; the input is never modified. Algorithms are classic image-processing
 * techniques (box-blur gaussian approximation, Sobel, Kuwahara via summed-area tables, ...).
 */
object EffectProcessor {

    fun apply(type: EffectType, src: IntArray, w: Int, h: Int, params: Map<String, Float> = type.defaults()): IntArray {
        require(src.size == w * h) { "Pixel buffer size mismatch" }
        fun p(key: String): Float = params[key] ?: type.params.first { it.key == key }.default
        return when (type) {
            EffectType.BLUR -> blur(src, w, h, p("radius"))
            EffectType.GLOW -> glow(src, w, h, p("radius"), p("strength"))
            EffectType.DROP_SHADOW -> dropShadow(src, w, h, p("distance"), p("blur"))
            EffectType.BRIGHTNESS_CONTRAST -> brightnessContrast(src, p("brightness"), p("contrast"))
            EffectType.HUE_SATURATION -> hueSaturation(src, p("hue"), p("saturation"))
            EffectType.GRAYSCALE -> mapRgb(src) { _, r, g, b -> val l = lum(r, g, b); rgb(l, l, l) }
            EffectType.SEPIA -> sepia(src, p("amount"))
            EffectType.INVERT -> mapRgb(src) { _, r, g, b -> rgb(255 - r, 255 - g, 255 - b) }
            EffectType.POSTERIZE -> posterize(src, p("levels").roundToInt())
            EffectType.THRESHOLD -> mapRgb(src) { _, r, g, b -> if (lum(r, g, b) >= p("level")) rgb(255, 255, 255) else rgb(0, 0, 0) }
            EffectType.PIXELATE -> pixelate(src, w, h, p("size").roundToInt())
            EffectType.SHARPEN -> sharpen(src, w, h, p("amount"))
            EffectType.EMBOSS -> emboss(src, w, h, p("strength"))
            EffectType.EDGE_DETECT -> edgeDetect(src, w, h)
            EffectType.VIGNETTE -> vignette(src, w, h, p("amount"))
            EffectType.NOISE -> noise(src, p("amount"))
            EffectType.MOTION_BLUR -> motionBlur(src, w, h, p("distance"), p("angle"))
            EffectType.OUTLINE -> outline(src, w, h, p("width").roundToInt())
            EffectType.INNER_SHADOW -> innerShadow(src, w, h, p("distance"), p("blur"))
            EffectType.COLOR_BALANCE -> mapRgb(src) { _, r, g, b ->
                rgb(r + (p("red") * 0.6f).toInt(), g - ((p("red") + p("blue")) * 0.15f).toInt(), b + (p("blue") * 0.6f).toInt())
            }
            EffectType.EXPOSURE -> {
                val f = 2f.pow(p("stops"))
                mapRgb(src) { _, r, g, b -> rgb((r * f).toInt(), (g * f).toInt(), (b * f).toInt()) }
            }
            EffectType.TEMPERATURE -> {
                val t = p("warmth") * 0.5f
                mapRgb(src) { _, r, g, b -> rgb((r + t).toInt(), (g + t * 0.15f).toInt(), (b - t).toInt()) }
            }
            EffectType.DUOTONE -> duotone(src, p("hue"))
            EffectType.GRADIENT_MAP -> gradientMap(src, p("hue"))
            EffectType.CHROMATIC_ABERRATION -> chromatic(src, w, h, p("offset").roundToInt())
            EffectType.HALFTONE -> halftone(src, w, h, p("cell").roundToInt())
            EffectType.OIL_PAINT -> kuwahara(src, w, h, p("radius").roundToInt())
            EffectType.SPIN_BLUR -> spinBlur(src, w, h, p("angle"))
            EffectType.ZOOM_BLUR -> zoomBlur(src, w, h, p("amount"))
            EffectType.LONG_SHADOW -> longShadow(src, w, h, p("length"), p("angle"))
            EffectType.NEON_EDGES -> neon(src, w, h, p("radius"))
            EffectType.SOLARIZE -> {
                val l = p("level").toInt()
                mapRgb(src) { _, r, g, b -> rgb(sol(r, l), sol(g, l), sol(b, l)) }
            }
            EffectType.GLITCH -> glitch(src, w, h, p("amount").roundToInt())
            EffectType.BLOOM -> bloom(src, w, h, p("threshold"), p("radius"))
            EffectType.CROSSHATCH -> crosshatch(src, w, h, p("spacing").roundToInt())
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun a(c: Int) = c ushr 24
    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun clamp(v: Int) = if (v < 0) 0 else if (v > 255) 255 else v
    private fun rgb(r: Int, g: Int, b: Int) = (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
    private fun lum(r: Int, g: Int, b: Int) = (r * 54 + g * 183 + b * 19) shr 8
    private fun sol(v: Int, level: Int) = if (v > level) 255 - v else v

    /** Maps RGB keeping alpha; [f] returns 0xRRGGBB. */
    private inline fun mapRgb(src: IntArray, f: (Int, Int, Int, Int) -> Int): IntArray =
        IntArray(src.size) { i ->
            val c = src[i]
            (c and 0xFF000000.toInt()) or (f(i, r(c), g(c), b(c)) and 0xFFFFFF)
        }

    /** Separate premultiplied channel planes. */
    private class Planes(val a: IntArray, val r: IntArray, val g: IntArray, val b: IntArray) {
        fun copy() = Planes(a.copyOf(), r.copyOf(), g.copyOf(), b.copyOf())
    }

    private fun toPlanes(src: IntArray): Planes {
        val n = src.size
        val pa = IntArray(n); val pr = IntArray(n); val pg = IntArray(n); val pb = IntArray(n)
        for (i in 0 until n) {
            val c = src[i]
            val al = a(c)
            pa[i] = al
            pr[i] = r(c) * al / 255
            pg[i] = g(c) * al / 255
            pb[i] = b(c) * al / 255
        }
        return Planes(pa, pr, pg, pb)
    }

    private fun fromPlanes(p: Planes): IntArray = IntArray(p.a.size) { i ->
        val al = clamp(p.a[i])
        if (al == 0) 0 else ColorUtils.argb(al, p.r[i] * 255 / al, p.g[i] * 255 / al, p.b[i] * 255 / al)
    }

    private fun boxBlurPlane(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return src.copyOf()
        val tmp = IntArray(src.size)
        val out = IntArray(src.size)
        val div = radius * 2 + 1
        for (y in 0 until h) {
            val row = y * w
            var sum = 0
            for (k in -radius..radius) sum += src[row + k.coerceIn(0, w - 1)]
            for (x in 0 until w) {
                tmp[row + x] = sum / div
                sum += src[row + (x + radius + 1).coerceAtMost(w - 1)] - src[row + (x - radius).coerceAtLeast(0)]
            }
        }
        for (x in 0 until w) {
            var sum = 0
            for (k in -radius..radius) sum += tmp[k.coerceIn(0, h - 1) * w + x]
            for (y in 0 until h) {
                out[y * w + x] = sum / div
                sum += tmp[(y + radius + 1).coerceAtMost(h - 1) * w + x] - tmp[(y - radius).coerceAtLeast(0) * w + x]
            }
        }
        return out
    }

    /** Gaussian approximation: three box blur passes. */
    private fun gaussianPlane(plane: IntArray, w: Int, h: Int, radius: Float): IntArray {
        val box = max(1, (radius / 2f).roundToInt())
        var p = plane
        repeat(3) { p = boxBlurPlane(p, w, h, box) }
        return p
    }

    private fun blurPlanes(p: Planes, w: Int, h: Int, radius: Float) = Planes(
        gaussianPlane(p.a, w, h, radius), gaussianPlane(p.r, w, h, radius),
        gaussianPlane(p.g, w, h, radius), gaussianPlane(p.b, w, h, radius),
    )

    fun blur(src: IntArray, w: Int, h: Int, radius: Float): IntArray =
        fromPlanes(blurPlanes(toPlanes(src), w, h, radius))

    /** Source-over composite of [top] onto [bottom] (both non-premultiplied). */
    private fun over(top: IntArray, bottom: IntArray): IntArray = IntArray(top.size) { i -> blendOver(bottom[i], top[i]) }

    private fun blendOver(dst: Int, src: Int): Int {
        val sa = a(src)
        if (sa == 255) return src
        if (sa == 0) return dst
        val da = a(dst)
        val outA = sa + da * (255 - sa) / 255
        if (outA == 0) return 0
        fun ch(s: Int, d: Int) = (s * sa + d * da * (255 - sa) / 255) / outA
        return ColorUtils.argb(outA, ch(r(src), r(dst)), ch(g(src), g(dst)), ch(b(src), b(dst)))
    }

    private fun shiftAlpha(alpha: IntArray, w: Int, h: Int, dx: Int, dy: Int): IntArray {
        val out = IntArray(alpha.size)
        for (y in 0 until h) {
            val sy = y - dy
            if (sy !in 0 until h) continue
            for (x in 0 until w) {
                val sx = x - dx
                if (sx in 0 until w) out[y * w + x] = alpha[sy * w + sx]
            }
        }
        return out
    }

    private fun sample(src: IntArray, w: Int, h: Int, x: Int, y: Int): Int =
        src[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)]

    // ---------------------------------------------------------------- effects

    private fun glow(src: IntArray, w: Int, h: Int, radius: Float, strength: Float): IntArray {
        val blurred = blur(src, w, h, radius)
        val glowLayer = IntArray(src.size) { i ->
            val c = blurred[i]
            val al = clamp((a(c) * strength * 1.4f).toInt())
            ColorUtils.argb(al, r(c) + (255 - r(c)) / 3, g(c) + (255 - g(c)) / 3, b(c) + (255 - b(c)) / 3)
        }
        return over(src, glowLayer)
    }

    private fun dropShadow(src: IntArray, w: Int, h: Int, distance: Float, blurRadius: Float): IntArray {
        val alpha = IntArray(src.size) { a(src[it]) }
        val d = (distance * 0.7071f).roundToInt()
        var shadow = shiftAlpha(alpha, w, h, d, d)
        if (blurRadius > 0) shadow = gaussianPlane(shadow, w, h, blurRadius)
        val shadowLayer = IntArray(src.size) { (clamp(shadow[it] * 6 / 10) shl 24) }
        return over(src, shadowLayer)
    }

    private fun innerShadow(src: IntArray, w: Int, h: Int, distance: Float, blurRadius: Float): IntArray {
        val inverse = IntArray(src.size) { 255 - a(src[it]) }
        val d = (distance * 0.7071f).roundToInt()
        var shadow = shiftAlpha(inverse, w, h, d, d)
        // Pixels shifted in from outside the canvas count as "outside" too.
        for (y in 0 until h) for (x in 0 until w) if (x < d || y < d) shadow[y * w + x] = 255
        if (blurRadius > 0) shadow = gaussianPlane(shadow, w, h, blurRadius)
        return IntArray(src.size) { i ->
            val c = src[i]
            val s = shadow[i] * 6 / 10
            val k = 255 - s
            (c and 0xFF000000.toInt()) or rgb(r(c) * k / 255, g(c) * k / 255, b(c) * k / 255)
        }
    }

    private fun brightnessContrast(src: IntArray, brightness: Float, contrast: Float): IntArray {
        val c = contrast * 2.55f
        val factor = (259f * (c + 255f)) / (255f * (259f - c))
        val shift = brightness * 1.28f
        val lut = IntArray(256) { v -> clamp((factor * (v - 128) + 128 + shift).roundToInt()) }
        return mapRgb(src) { _, r, g, b -> rgb(lut[r], lut[g], lut[b]) }
    }

    private fun hueSaturation(src: IntArray, hue: Float, saturation: Float): IntArray {
        val hsl = FloatArray(3)
        val satFactor = 1f + saturation / 100f
        return IntArray(src.size) { i ->
            val c = src[i]
            if (a(c) == 0) return@IntArray c
            ColorUtils.toHsl(r(c), g(c), b(c), hsl)
            ColorUtils.fromHsl(hsl[0] + hue, (hsl[1] * satFactor).coerceIn(0f, 1f), hsl[2], a(c))
        }
    }

    private fun sepia(src: IntArray, amount: Float): IntArray = mapRgb(src) { _, r, g, b ->
        val sr = (r * 0.393f + g * 0.769f + b * 0.189f)
        val sg = (r * 0.349f + g * 0.686f + b * 0.168f)
        val sb = (r * 0.272f + g * 0.534f + b * 0.131f)
        rgb(
            (r + (sr - r) * amount).toInt(),
            (g + (sg - g) * amount).toInt(),
            (b + (sb - b) * amount).toInt(),
        )
    }

    private fun posterize(src: IntArray, levels: Int): IntArray {
        val l = levels.coerceIn(2, 255)
        val lut = IntArray(256) { v -> ((v * (l - 1) + 127) / 255) * 255 / (l - 1) }
        return mapRgb(src) { _, r, g, b -> rgb(lut[r], lut[g], lut[b]) }
    }

    private fun pixelate(src: IntArray, w: Int, h: Int, size: Int): IntArray {
        val s = size.coerceAtLeast(1)
        val p = toPlanes(src)
        val out = IntArray(src.size)
        var by = 0
        while (by < h) {
            var bx = 0
            while (bx < w) {
                var sa = 0L; var sr = 0L; var sg = 0L; var sb = 0L; var n = 0
                for (y in by until min(by + s, h)) for (x in bx until min(bx + s, w)) {
                    val i = y * w + x
                    sa += p.a[i]; sr += p.r[i]; sg += p.g[i]; sb += p.b[i]; n++
                }
                val al = (sa / n).toInt()
                val c = if (al == 0) 0 else ColorUtils.argb(al, (sr / n * 255 / al).toInt(), (sg / n * 255 / al).toInt(), (sb / n * 255 / al).toInt())
                for (y in by until min(by + s, h)) for (x in bx until min(bx + s, w)) out[y * w + x] = c
                bx += s
            }
            by += s
        }
        return out
    }

    private fun sharpen(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val blurred = blur(src, w, h, 2f)
        return IntArray(src.size) { i ->
            val c = src[i]
            val bl = blurred[i]
            (c and 0xFF000000.toInt()) or rgb(
                (r(c) + amount * (r(c) - r(bl))).toInt(),
                (g(c) + amount * (g(c) - g(bl))).toInt(),
                (b(c) + amount * (b(c) - b(bl))).toInt(),
            )
        }
    }

    private fun luminancePlane(src: IntArray) = IntArray(src.size) { val c = src[it]; lum(r(c), g(c), b(c)) * a(c) / 255 + (255 - a(c)) }

    private fun emboss(src: IntArray, w: Int, h: Int, strength: Float): IntArray {
        val l = IntArray(src.size) { val c = src[it]; lum(r(c), g(c), b(c)) }
        return IntArray(src.size) { i ->
            val x = i % w
            val y = i / w
            fun s(dx: Int, dy: Int) = l[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
            val v = (-2 * s(-1, -1) - s(0, -1) - s(-1, 0) + s(1, 0) + s(0, 1) + 2 * s(1, 1)) * strength
            val gray = clamp((128 + v).toInt())
            (src[i] and 0xFF000000.toInt()) or rgb(gray, gray, gray)
        }
    }

    private fun sobel(l: IntArray, w: Int, h: Int): IntArray = IntArray(l.size) { i ->
        val x = i % w
        val y = i / w
        fun s(dx: Int, dy: Int) = l[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
        val gx = -s(-1, -1) - 2 * s(-1, 0) - s(-1, 1) + s(1, -1) + 2 * s(1, 0) + s(1, 1)
        val gy = -s(-1, -1) - 2 * s(0, -1) - s(1, -1) + s(-1, 1) + 2 * s(0, 1) + s(1, 1)
        clamp(sqrt((gx * gx + gy * gy).toFloat()).toInt())
    }

    private fun edgeDetect(src: IntArray, w: Int, h: Int): IntArray {
        val edges = sobel(luminancePlane(src), w, h)
        return IntArray(src.size) { i ->
            val v = 255 - edges[i]
            (src[i] and 0xFF000000.toInt()) or rgb(v, v, v)
        }
    }

    private fun vignette(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val cx = w / 2f
        val cy = h / 2f
        val maxD = hypot(cx, cy)
        return mapRgb(src) { i, r, g, b ->
            val d = hypot(i % w - cx, i / w - cy) / maxD
            val f = 1f - amount * (d * d).coerceIn(0f, 1f)
            rgb((r * f).toInt(), (g * f).toInt(), (b * f).toInt())
        }
    }

    private fun noise(src: IntArray, amount: Float): IntArray {
        val rnd = Random(0x60D7A3)
        val amp = amount.toInt().coerceAtLeast(1)
        return mapRgb(src) { _, r, g, b ->
            val n = rnd.nextInt(-amp, amp + 1)
            rgb(r + n, g + n, b + n)
        }
    }

    private fun directionalSamples(src: IntArray, w: Int, h: Int, offsets: (x: Int, y: Int, k: Int) -> Pair<Int, Int>, count: Int): IntArray {
        val p = toPlanes(src)
        val out = Planes(IntArray(src.size), IntArray(src.size), IntArray(src.size), IntArray(src.size))
        for (y in 0 until h) for (x in 0 until w) {
            var sa = 0; var sr = 0; var sg = 0; var sb = 0
            for (k in 0 until count) {
                val (sx, sy) = offsets(x, y, k)
                val j = sy.coerceIn(0, h - 1) * w + sx.coerceIn(0, w - 1)
                sa += p.a[j]; sr += p.r[j]; sg += p.g[j]; sb += p.b[j]
            }
            val i = y * w + x
            out.a[i] = sa / count; out.r[i] = sr / count; out.g[i] = sg / count; out.b[i] = sb / count
        }
        return fromPlanes(out)
    }

    private fun motionBlur(src: IntArray, w: Int, h: Int, distance: Float, angle: Float): IntArray {
        val count = distance.roundToInt().coerceIn(2, 32)
        val step = distance / count
        val rad = angle * PI.toFloat() / 180f
        val dx = cos(rad) * step
        val dy = sin(rad) * step
        return directionalSamples(src, w, h, { x, y, k ->
            val t = k - count / 2
            (x + dx * t).roundToInt() to (y + dy * t).roundToInt()
        }, count)
    }

    private fun spinBlur(src: IntArray, w: Int, h: Int, angle: Float): IntArray {
        val count = 16
        val cx = w / 2f
        val cy = h / 2f
        val total = angle * PI.toFloat() / 180f
        val cosT = FloatArray(count) { cos(total * (it - count / 2) / count) }
        val sinT = FloatArray(count) { sin(total * (it - count / 2) / count) }
        return directionalSamples(src, w, h, { x, y, k ->
            val dx = x - cx
            val dy = y - cy
            (cx + dx * cosT[k] - dy * sinT[k]).roundToInt() to (cy + dx * sinT[k] + dy * cosT[k]).roundToInt()
        }, count)
    }

    private fun zoomBlur(src: IntArray, w: Int, h: Int, amount: Float): IntArray {
        val count = 16
        val cx = w / 2f
        val cy = h / 2f
        return directionalSamples(src, w, h, { x, y, k ->
            val s = 1f - amount * k / count
            (cx + (x - cx) * s).roundToInt() to (cy + (y - cy) * s).roundToInt()
        }, count)
    }

    /** Max filter on an alpha plane (square structuring element, separable). */
    private fun dilateAlpha(alpha: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val tmp = IntArray(alpha.size)
        val out = IntArray(alpha.size)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (k in max(0, x - radius)..min(w - 1, x + radius)) m = max(m, alpha[y * w + k])
            tmp[y * w + x] = m
        }
        for (y in 0 until h) for (x in 0 until w) {
            var m = 0
            for (k in max(0, y - radius)..min(h - 1, y + radius)) m = max(m, tmp[k * w + x])
            out[y * w + x] = m
        }
        return out
    }

    private fun outline(src: IntArray, w: Int, h: Int, width: Int): IntArray {
        val alpha = IntArray(src.size) { a(src[it]) }
        val grown = dilateAlpha(alpha, w, h, width.coerceAtLeast(1))
        val stroke = IntArray(src.size) { grown[it] shl 24 }
        return over(src, stroke)
    }

    private fun duotone(src: IntArray, hue: Float): IntArray {
        val dark = ColorUtils.fromHsl(hue, 0.8f, 0.15f, 255)
        val light = ColorUtils.fromHsl(hue + 40f, 0.65f, 0.88f, 255)
        return mapRgb(src) { _, r, g, b -> mix(dark, light, lum(r, g, b) / 255f) }
    }

    private fun gradientMap(src: IntArray, hue: Float): IntArray {
        val mid = ColorUtils.fromHsl(hue, 0.85f, 0.5f, 255)
        return mapRgb(src) { _, r, g, b ->
            val t = lum(r, g, b) / 255f
            if (t < 0.5f) mix(0xFF000000.toInt(), mid, t * 2f) else mix(mid, 0xFFFFFFFF.toInt(), (t - 0.5f) * 2f)
        }
    }

    private fun mix(c1: Int, c2: Int, t: Float): Int = rgb(
        (r(c1) + (r(c2) - r(c1)) * t).toInt(),
        (g(c1) + (g(c2) - g(c1)) * t).toInt(),
        (b(c1) + (b(c2) - b(c1)) * t).toInt(),
    )

    private fun chromatic(src: IntArray, w: Int, h: Int, offset: Int): IntArray = IntArray(src.size) { i ->
        val x = i % w
        val y = i / w
        val left = sample(src, w, h, x - offset, y)
        val right = sample(src, w, h, x + offset, y)
        val c = src[i]
        val al = max(a(c), max(a(left), a(right)))
        ColorUtils.argb(al, r(left), g(c), b(right))
    }

    private fun halftone(src: IntArray, w: Int, h: Int, cell: Int): IntArray {
        val s = cell.coerceAtLeast(2)
        val avg = pixelate(src, w, h, s)
        return IntArray(src.size) { i ->
            val x = i % w
            val y = i / w
            val c = avg[i]
            if (a(c) == 0) return@IntArray 0
            val darkness = 1f - lum(r(c), g(c), b(c)) / 255f
            val radius = s * 0.5f * sqrt(darkness.coerceIn(0.05f, 1f)) * 1.15f
            val cx = (x / s) * s + s / 2f
            val cy = (y / s) * s + s / 2f
            if (hypot(x + 0.5f - cx, y + 0.5f - cy) <= radius) c else 0
        }
    }

    /** Kuwahara (oil paint) filter using summed-area tables for O(1) region statistics. */
    private fun kuwahara(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        val rr = radius.coerceIn(1, 16)
        val sw = w + 1
        val satR = LongArray(sw * (h + 1)); val satG = LongArray(sw * (h + 1)); val satB = LongArray(sw * (h + 1))
        val satL = LongArray(sw * (h + 1)); val satL2 = LongArray(sw * (h + 1))
        for (y in 0 until h) {
            var cr = 0L; var cg = 0L; var cb = 0L; var cl = 0L; var cl2 = 0L
            for (x in 0 until w) {
                val c = src[y * w + x]
                val l = lum(r(c), g(c), b(c)).toLong()
                cr += r(c); cg += g(c); cb += b(c); cl += l; cl2 += l * l
                val o = (y + 1) * sw + (x + 1)
                val up = y * sw + (x + 1)
                satR[o] = satR[up] + cr; satG[o] = satG[up] + cg; satB[o] = satB[up] + cb
                satL[o] = satL[up] + cl; satL2[o] = satL2[up] + cl2
            }
        }
        fun sum(t: LongArray, x0: Int, y0: Int, x1: Int, y1: Int): Long =
            t[(y1 + 1) * sw + (x1 + 1)] - t[y0 * sw + (x1 + 1)] - t[(y1 + 1) * sw + x0] + t[y0 * sw + x0]

        return IntArray(src.size) { i ->
            val x = i % w
            val y = i / w
            var bestVar = Double.MAX_VALUE
            var best = 0
            for (q in 0 until 4) {
                val x0 = if (q % 2 == 0) max(0, x - rr) else x
                val x1 = if (q % 2 == 0) x else min(w - 1, x + rr)
                val y0 = if (q < 2) max(0, y - rr) else y
                val y1 = if (q < 2) y else min(h - 1, y + rr)
                val n = ((x1 - x0 + 1) * (y1 - y0 + 1)).toDouble()
                val meanL = sum(satL, x0, y0, x1, y1) / n
                val variance = sum(satL2, x0, y0, x1, y1) / n - meanL * meanL
                if (variance < bestVar) {
                    bestVar = variance
                    best = rgb(
                        (sum(satR, x0, y0, x1, y1) / n).toInt(),
                        (sum(satG, x0, y0, x1, y1) / n).toInt(),
                        (sum(satB, x0, y0, x1, y1) / n).toInt(),
                    )
                }
            }
            (src[i] and 0xFF000000.toInt()) or best
        }
    }

    /** Long shadow built by repeated doubling of shifted alpha masks (O(n log length)). */
    private fun longShadow(src: IntArray, w: Int, h: Int, length: Float, angle: Float): IntArray {
        val rad = angle * PI.toFloat() / 180f
        val ux = cos(rad)
        val uy = sin(rad)
        var mask = IntArray(src.size) { a(src[it]) }
        var covered = 1f
        while (covered < length) {
            val step = min(covered, length - covered)
            val shifted = shiftAlpha(mask, w, h, (ux * step).roundToInt(), (uy * step).roundToInt())
            mask = IntArray(mask.size) { max(mask[it], shifted[it]) }
            covered += step
        }
        val shadow = IntArray(src.size) { (mask[it] * 45 / 100 shl 24) or 0x101018 }
        return over(src, shadow)
    }

    private fun neon(src: IntArray, w: Int, h: Int, radius: Float): IntArray {
        val edges = sobel(IntArray(src.size) { a(src[it]) }, w, h)
        val lines = IntArray(src.size) { i ->
            val e = edges[i]
            if (e == 0) 0 else {
                val c = src[i]
                val hsl = FloatArray(3)
                ColorUtils.toHsl(r(c), g(c), b(c), hsl)
                ColorUtils.fromHsl(hsl[0], 1f, 0.6f, e)
            }
        }
        val glowLayer = blur(lines, w, h, radius).map { c -> ColorUtils.withAlpha(c, clamp(a(c) * 3)) }.toIntArray()
        val dimmed = IntArray(src.size) { i -> val c = src[i]; ColorUtils.withAlpha(c, a(c) / 4) }
        return over(lines, over(glowLayer, dimmed))
    }

    private fun glitch(src: IntArray, w: Int, h: Int, amount: Int): IntArray {
        val rnd = Random(0x611C4)
        val out = chromatic(src, w, h, max(1, amount / 4))
        var y = 0
        while (y < h) {
            val sliceH = rnd.nextInt(2, max(3, h / 12))
            val shift = if (rnd.nextFloat() < 0.4f) rnd.nextInt(-amount, amount + 1) else 0
            if (shift != 0) {
                for (yy in y until min(h, y + sliceH)) {
                    val row = IntArray(w) { x -> out[yy * w + ((x - shift) % w + w) % w] }
                    row.copyInto(out, yy * w)
                }
            }
            y += sliceH
        }
        return out
    }

    private fun bloom(src: IntArray, w: Int, h: Int, threshold: Float, radius: Float): IntArray {
        val bright = IntArray(src.size) { i ->
            val c = src[i]
            if (lum(r(c), g(c), b(c)) >= threshold) c else 0
        }
        val blurred = blur(bright, w, h, radius)
        return IntArray(src.size) { i ->
            val c = src[i]
            val bl = blurred[i]
            val k = a(bl)
            ColorUtils.argb(
                max(a(c), k),
                r(c) + r(bl) * k / 255,
                g(c) + g(bl) * k / 255,
                b(c) + b(bl) * k / 255,
            )
        }
    }

    private fun crosshatch(src: IntArray, w: Int, h: Int, spacing: Int): IntArray {
        val s = spacing.coerceAtLeast(2)
        return mapRgb(src) { i, r, g, b ->
            val x = i % w
            val y = i / w
            val l = lum(r, g, b)
            val ink = (l < 200 && (x + y) % s == 0) ||
                (l < 140 && abs(x - y) % s == 0) ||
                (l < 80 && x % s == 0) ||
                (l < 40 && y % s == 0)
            if (ink) rgb(20, 20, 30) else rgb(250, 248, 240)
        }
    }
}
