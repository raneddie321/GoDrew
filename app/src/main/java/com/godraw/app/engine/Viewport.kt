package com.godraw.app.engine

import android.graphics.Matrix
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/** Canvas ↔ screen mapping with zoom, pan, rotation and mirroring. */
class Viewport {
    val matrix = Matrix()
    private val inverse = Matrix()
    private val values = FloatArray(9)
    private val pt = FloatArray(2)

    var mirrored = false
        private set

    val zoom: Float
        get() {
            matrix.getValues(values)
            return hypot(values[Matrix.MSCALE_X], values[Matrix.MSKEW_Y])
        }

    val rotationDegrees: Float
        get() {
            matrix.getValues(values)
            return Math.toDegrees(atan2(values[Matrix.MSKEW_Y].toDouble(), values[Matrix.MSCALE_X].toDouble())).toFloat()
        }

    fun fit(viewW: Int, viewH: Int, canvasW: Int, canvasH: Int, padding: Float) {
        val scale = min((viewW - padding * 2) / canvasW, (viewH - padding * 2) / canvasH).coerceAtLeast(0.01f)
        matrix.reset()
        matrix.postScale(scale, scale)
        matrix.postTranslate((viewW - canvasW * scale) / 2f, (viewH - canvasH * scale) / 2f)
        mirrored = false
        updateInverse()
    }

    fun toCanvas(x: Float, y: Float): Pair<Float, Float> {
        pt[0] = x; pt[1] = y
        inverse.mapPoints(pt)
        return pt[0] to pt[1]
    }

    fun toScreen(x: Float, y: Float): Pair<Float, Float> {
        pt[0] = x; pt[1] = y
        matrix.mapPoints(pt)
        return pt[0] to pt[1]
    }

    /** Applies a two-finger transform from the previous to the current finger positions. */
    fun pinch(px0: Float, py0: Float, px1: Float, py1: Float, cx0: Float, cy0: Float, cx1: Float, cy1: Float) {
        val prevDist = hypot(px1 - px0, py1 - py0).coerceAtLeast(1f)
        val curDist = hypot(cx1 - cx0, cy1 - cy0).coerceAtLeast(1f)
        var scale = curDist / prevDist
        val z = zoom
        val newZoom = (z * scale).coerceIn(MIN_ZOOM, MAX_ZOOM)
        scale = newZoom / z
        val prevAngle = atan2(py1 - py0, px1 - px0)
        val curAngle = atan2(cy1 - cy0, cx1 - cx0)
        val degrees = Math.toDegrees((curAngle - prevAngle).toDouble()).toFloat()
        val pmx = (px0 + px1) / 2f
        val pmy = (py0 + py1) / 2f
        val cmx = (cx0 + cx1) / 2f
        val cmy = (cy0 + cy1) / 2f
        matrix.postTranslate(-pmx, -pmy)
        matrix.postScale(scale, scale)
        matrix.postRotate(degrees)
        matrix.postTranslate(cmx, cmy)
        updateInverse()
    }

    fun pan(dx: Float, dy: Float) {
        matrix.postTranslate(dx, dy)
        updateInverse()
    }

    /** Snaps nearly-straight rotations back to 0° after a gesture. */
    fun snapRotation(viewCx: Float, viewCy: Float) {
        val r = rotationDegrees
        val nearest = Math.round(r / 90f) * 90f
        if (kotlin.math.abs(r - nearest) < 4f && r != nearest) {
            matrix.postRotate(nearest - r, viewCx, viewCy)
            updateInverse()
        }
    }

    fun mirror(viewCx: Float, viewCy: Float) {
        matrix.postScale(-1f, 1f, viewCx, viewCy)
        mirrored = !mirrored
        updateInverse()
    }

    private fun updateInverse() {
        matrix.invert(inverse)
    }

    companion object {
        const val MIN_ZOOM = 0.02f
        const val MAX_ZOOM = 64f
    }
}
