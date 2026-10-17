package com.godraw.core.color

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Color math on packed ARGB integers (non-premultiplied). */
object ColorUtils {

    fun alpha(c: Int): Int = c ushr 24
    fun red(c: Int): Int = (c shr 16) and 0xFF
    fun green(c: Int): Int = (c shr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a.coerceIn(0, 255) shl 24)

    /** Converts to HSV: hue 0..360, saturation 0..1, value 0..1. */
    fun toHsv(c: Int): FloatArray {
        val r = red(c) / 255f
        val g = green(c) / 255f
        val b = blue(c) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0) it + 360f else it }
        val s = if (mx == 0f) 0f else d / mx
        return floatArrayOf(h, s, mx)
    }

    fun fromHsv(h: Float, s: Float, v: Float, alpha: Int = 255): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val ss = s.coerceIn(0f, 1f)
        val vv = v.coerceIn(0f, 1f)
        val c = vv * ss
        val x = c * (1 - abs((hh / 60f) % 2f - 1))
        val m = vv - c
        val (r, g, b) = when {
            hh < 60 -> Triple(c, x, 0f)
            hh < 120 -> Triple(x, c, 0f)
            hh < 180 -> Triple(0f, c, x)
            hh < 240 -> Triple(0f, x, c)
            hh < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return argb(alpha, ((r + m) * 255).roundToInt(), ((g + m) * 255).roundToInt(), ((b + m) * 255).roundToInt())
    }

    /** HSL used by hue/saturation adjustments: h 0..360, s 0..1, l 0..1. */
    fun toHsl(r: Int, g: Int, b: Int, out: FloatArray) {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val mx = max(rf, max(gf, bf))
        val mn = min(rf, min(gf, bf))
        val l = (mx + mn) / 2f
        val d = mx - mn
        var h = 0f
        var s = 0f
        if (d != 0f) {
            s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            h = when (mx) {
                rf -> ((gf - bf) / d + (if (gf < bf) 6f else 0f))
                gf -> ((bf - rf) / d + 2f)
                else -> ((rf - gf) / d + 4f)
            } * 60f
        }
        out[0] = h; out[1] = s; out[2] = l
    }

    fun fromHsl(h: Float, s: Float, l: Float, a: Int): Int {
        if (s <= 0f) {
            val v = (l * 255).roundToInt()
            return argb(a, v, v, v)
        }
        val q = if (l < 0.5f) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        val hk = (((h % 360f) + 360f) % 360f) / 360f
        fun hue(t0: Float): Float {
            var t = t0
            if (t < 0) t += 1f
            if (t > 1) t -= 1f
            return when {
                t < 1f / 6f -> p + (q - p) * 6f * t
                t < 0.5f -> q
                t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
                else -> p
            }
        }
        return argb(
            a,
            (hue(hk + 1f / 3f) * 255).roundToInt(),
            (hue(hk) * 255).roundToInt(),
            (hue(hk - 1f / 3f) * 255).roundToInt(),
        )
    }

    /** Formats as #RRGGBB, or #AARRGGBB when not fully opaque. */
    fun toHex(c: Int, includeAlpha: Boolean = alpha(c) != 255): String =
        if (includeAlpha) "#%08X".format(c) else "#%06X".format(c and 0xFFFFFF)

    /** Parses #RGB, #RRGGBB or #AARRGGBB (with or without #). Returns null when invalid. */
    fun parseHex(input: String): Int? {
        val s = input.trim().removePrefix("#")
        if (s.isEmpty() || !s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        return when (s.length) {
            3 -> {
                val r = s[0].digitToInt(16) * 17
                val g = s[1].digitToInt(16) * 17
                val b = s[2].digitToInt(16) * 17
                argb(255, r, g, b)
            }
            6 -> (0xFF000000.toInt()) or s.toLong(16).toInt()
            8 -> s.toLong(16).toInt()
            else -> null
        }
    }

    /** Relative luminance 0..1 (sRGB, approximate). */
    fun luminance(c: Int): Float = (0.2126f * red(c) + 0.7152f * green(c) + 0.0722f * blue(c)) / 255f

    /** Squared RGBA distance, used by tolerance-based tools. */
    fun distance(a: Int, b: Int): Int {
        val da = alpha(a) - alpha(b)
        val dr = red(a) - red(b)
        val dg = green(a) - green(b)
        val db = blue(a) - blue(b)
        return max(max(abs(da), abs(dr)), max(abs(dg), abs(db)))
    }
}
