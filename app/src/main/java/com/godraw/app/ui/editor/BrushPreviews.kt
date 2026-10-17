package com.godraw.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import com.godraw.app.engine.StampFactory
import com.godraw.app.engine.StrokeRenderer
import com.godraw.core.brush.BrushPreset
import com.godraw.core.geometry.StrokePoint
import kotlin.math.sin

/** Renders small sample strokes for the brush library. */
object BrushPreviews {
    private const val W = 220
    private const val H = 64
    private val stamps = StampFactory()
    private val cache = LruCache<String, Bitmap>(260)

    fun preview(brush: BrushPreset, color: Int): Bitmap {
        val key = brush.hashCode().toString() + ":" + color
        cache.get(key)?.let { return it }
        val renderer = StrokeRenderer(W, H, stamps)
        // Scale big brushes down so the sample stays readable.
        val sample = brush.copy(size = brush.size.coerceIn(2f, 26f), smoothing = 0f)
        renderer.begin(sample, color, eraser = false, stabilization = 0f, symmetry = null)
        val steps = 48
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = 14f + t * (W - 28f)
            val y = H / 2f + sin(t * Math.PI * 2).toFloat() * (H / 2f - 18f)
            val pressure = 0.25f + 0.75f * sin(t * Math.PI).toFloat()
            renderer.add(StrokePoint(x, y, pressure, i * 8L))
        }
        renderer.finishInput()
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        renderer.drawStroke(Canvas(bmp), null)
        renderer.release()
        cache.put(key, bmp)
        return bmp
    }
}
