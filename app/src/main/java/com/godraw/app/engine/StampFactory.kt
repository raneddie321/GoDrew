package com.godraw.app.engine

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import com.godraw.core.brush.BrushPreset
import com.godraw.core.brush.BrushTip
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Generates and caches brush tip stamps: white bitmaps whose alpha is the tip shape. They are
 * tinted at draw time, so one stamp serves every color.
 */
class StampFactory {

    private data class Key(val tip: BrushTip, val hardness: Int, val roundness: Int, val texture: Int, val wet: Int, val variant: Int)

    private val cache = HashMap<Key, Bitmap>()
    private var grainTile: Bitmap? = null

    fun stamp(brush: BrushPreset, variant: Int = 0): Bitmap {
        val key = Key(
            brush.tip,
            (brush.hardness * 20).roundToInt(),
            (brush.roundness * 20).roundToInt(),
            (brush.texture * 10).roundToInt(),
            (brush.wetEdges * 10).roundToInt(),
            if (brush.tip == BrushTip.SPECKLE || brush.tip == BrushTip.GRAIN) variant % VARIANTS else 0,
        )
        return cache.getOrPut(key) { build(key) }
    }

    private fun build(k: Key): Bitmap {
        val size = SIZE
        val pixels = IntArray(size * size)
        val hardness = k.hardness / 20f
        val roundness = (k.roundness / 20f).coerceIn(0.05f, 1f)
        val texture = k.texture / 10f
        val wet = k.wet / 10f
        val rnd = Random(1234 + k.variant * 7919)
        val c = (size - 1) / 2f
        val r = size / 2f - 1f
        when (k.tip) {
            BrushTip.SPECKLE -> {
                val dots = 70
                repeat(dots) {
                    val ang = rnd.nextFloat() * 6.2831855f
                    val dist = sqrt(rnd.nextFloat()) * r * 0.92f
                    val dx = c + kotlin.math.cos(ang) * dist
                    val dy = c + kotlin.math.sin(ang) * dist
                    val dr = 1.5f + rnd.nextFloat() * 3.5f
                    for (y in max(0, (dy - dr).toInt())..min(size - 1, (dy + dr).toInt() + 1)) {
                        for (x in max(0, (dx - dr).toInt())..min(size - 1, (dx + dr).toInt() + 1)) {
                            val d = sqrt((x - dx) * (x - dx) + (y - dy) * (y - dy))
                            val a = ((dr - d) * 255).coerceIn(0f, 255f).toInt()
                            val i = y * size + x
                            val prev = pixels[i] ushr 24
                            if (a > prev) pixels[i] = (a shl 24) or 0xFFFFFF
                        }
                    }
                }
            }
            else -> {
                for (y in 0 until size) for (x in 0 until size) {
                    val dx = x - c
                    val dy = (y - c) / (if (k.tip == BrushTip.FLAT) roundness else 1f)
                    val d = when (k.tip) {
                        BrushTip.SQUARE -> max(abs(dx), abs(dy)) / r
                        else -> sqrt(dx * dx + dy * dy) / r
                    }
                    if (d >= 1f) continue
                    // Hard core up to `hardness`, smooth falloff beyond (with a 2px AA edge minimum).
                    val edge = max(hardness, 0.001f).coerceAtMost(1f - 2f / size)
                    var a = if (d <= edge) 1f else {
                        val t = ((d - edge) / (1f - edge)).coerceIn(0f, 1f)
                        1f - t * t * (3 - 2 * t)
                    }
                    if (wet > 0f) a *= (1f - wet * 0.55f) + wet * 0.55f * (d * d * d)
                    if (k.tip == BrushTip.GRAIN || texture > 0f) {
                        val n = rnd.nextFloat()
                        a *= 1f - texture * n * 0.85f
                    }
                    val alpha = (a * 255).roundToInt().coerceIn(0, 255)
                    pixels[y * size + x] = (alpha shl 24) or 0xFFFFFF
                }
            }
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }

    /** Canvas-anchored paper grain used by textured brushes. */
    fun grainShader(): BitmapShader {
        val tile = grainTile ?: run {
            val s = 128
            val rnd = Random(42)
            val px = IntArray(s * s) {
                val v = 150 + rnd.nextInt(106)
                (v shl 24) or 0xFFFFFF
            }
            Bitmap.createBitmap(px, s, s, Bitmap.Config.ARGB_8888).also { grainTile = it }
        }
        return BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    companion object {
        const val SIZE = 128
        const val VARIANTS = 4
    }
}
