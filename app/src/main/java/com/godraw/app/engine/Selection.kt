package com.godraw.app.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import com.godraw.core.model.CellKey
import kotlin.math.ceil
import kotlin.math.floor

/** A selection outline in canvas coordinates. */
class Selection(val path: Path) {
    val bounds: RectF = RectF().also { path.computeBounds(it, true) }

    fun integerBounds(width: Int, height: Int): Rect = Rect(
        floor(bounds.left).toInt().coerceIn(0, width),
        floor(bounds.top).toInt().coerceIn(0, height),
        ceil(bounds.right).toInt().coerceIn(0, width),
        ceil(bounds.bottom).toInt().coerceIn(0, height),
    )

    /** Rasterized coverage (0..255) for pixel operations such as fill and effects. */
    fun mask(width: Int, height: Int): ByteArray {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        Canvas(bmp).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG))
        val buffer = java.nio.ByteBuffer.allocate(bmp.rowBytes * height)
        bmp.copyPixelsToBuffer(buffer)
        val out = ByteArray(width * height)
        val row = bmp.rowBytes
        val raw = buffer.array()
        for (y in 0 until height) System.arraycopy(raw, y * row, out, y * width, width)
        bmp.recycle()
        return out
    }

    fun contains(x: Float, y: Float): Boolean {
        val region = Region()
        val clip = Region(
            floor(bounds.left).toInt(), floor(bounds.top).toInt(),
            ceil(bounds.right).toInt() + 1, ceil(bounds.bottom).toInt() + 1,
        )
        region.setPath(path, clip)
        return region.contains(x.toInt(), y.toInt())
    }

    companion object {
        fun rect(r: RectF) = Selection(Path().apply { addRect(r, Path.Direction.CW) })
        fun all(width: Int, height: Int) = rect(RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }
}

/**
 * Pixels lifted from a layer for moving, scaling, rotating, flipping or distorting. The
 * original cell is kept so the operation can be cancelled or undone as a single step.
 */
class FloatingContent(
    val key: CellKey,
    val bitmap: Bitmap,
    /** Where the pixels came from (empty for pasted or text content). */
    val sourceRect: Rect,
    /** Copy of the whole cell before lifting, for cancel and undo. */
    val originalCell: Bitmap?,
    /** Maps floating bitmap coordinates to canvas coordinates. */
    val matrix: Matrix,
) {
    var distort = false

    /** Current corners (x0,y0 .. x3,y3) in canvas space: TL, TR, BR, BL. */
    fun corners(): FloatArray {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val pts = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
        matrix.mapPoints(pts)
        return pts
    }

    fun boundsInCanvas(): RectF {
        val r = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matrix.mapRect(r)
        return r
    }

    fun center(): Pair<Float, Float> {
        val p = floatArrayOf(bitmap.width / 2f, bitmap.height / 2f)
        matrix.mapPoints(p)
        return p[0] to p[1]
    }

    /** Sets the four corners (distort). */
    fun setCorners(dst: FloatArray) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        matrix.setPolyToPoly(floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h), 0, dst, 0, 4)
    }

    fun flipHorizontal() {
        val (cx, cy) = center()
        matrix.postScale(-1f, 1f, cx, cy)
    }

    fun flipVertical() {
        val (cx, cy) = center()
        matrix.postScale(1f, -1f, cx, cy)
    }

    fun rotate(degrees: Float) {
        val (cx, cy) = center()
        matrix.postRotate(degrees, cx, cy)
    }
}
