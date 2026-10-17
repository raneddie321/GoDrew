package com.godraw.app.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import com.godraw.core.brush.BrushPreset
import com.godraw.core.brush.BrushTip
import com.godraw.core.geometry.Dab
import com.godraw.core.geometry.DabSpacer
import com.godraw.core.geometry.StrokePoint
import com.godraw.core.geometry.StrokeSmoother
import com.godraw.core.geometry.Symmetry
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Renders a stroke in progress. Dabs accumulate in a tiled buffer (256 px tiles allocated on
 * demand), so only touched tiles are re-uploaded to the GPU while drawing. The finished stroke
 * is composited onto the layer with the brush opacity and blend mode, which avoids the
 * darkening that overlapping semi-transparent dabs would otherwise cause.
 */
class StrokeRenderer(val width: Int, val height: Int, private val stamps: StampFactory) {

    private val cols = ceil(width / TILE.toFloat()).toInt()
    private val rows = ceil(height / TILE.toFloat()).toInt()
    private val tiles = arrayOfNulls<Bitmap>(cols * rows)
    private val tileCanvases = arrayOfNulls<Canvas>(cols * rows)
    private val touched = BooleanArray(cols * rows)

    private val dabPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val pixelPaint = Paint()
    private val dst = RectF()
    private var rnd = Random(0)

    var brush: BrushPreset? = null
        private set
    var color: Int = Color.BLACK
        private set
    var isEraser = false
        private set
    var active = false
        private set

    /** Union of everything drawn by the current stroke, in canvas coordinates. */
    val bounds = RectF()

    private var smoother: StrokeSmoother? = null
    private var spacers: List<DabSpacer> = emptyList()
    private var symmetry: Symmetry? = null
    private var dabIndex = 0

    fun begin(brush: BrushPreset, color: Int, eraser: Boolean, stabilization: Float, symmetry: Symmetry?) {
        clear()
        this.brush = brush
        this.color = color
        this.isEraser = eraser
        this.symmetry = symmetry
        rnd = Random(System.nanoTime())
        val amount = max(stabilization, brush.smoothing).coerceIn(0f, 1f)
        smoother = if (brush.pixelPerfect) null else StrokeSmoother(amount, brush.size)
        val copies = symmetry?.copies ?: 1
        spacers = List(copies) { DabSpacer(brush.spacing) { p -> brush.diameterAt(p) } }
        dabPaint.colorFilter = PorterDuffColorFilter(color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
        pixelPaint.color = color or 0xFF000000.toInt()
        dabIndex = 0
        active = true
    }

    /** Adds an input point; returns the dirty region in canvas coordinates (or null). */
    fun add(point: StrokePoint): RectF? {
        val b = brush ?: return null
        val smoothed = smoother?.add(point) ?: if (smoother == null) point else return null
        return emit(b, smoothed)
    }

    /** Ends input; flushes the stabilizer so the stroke reaches the pen position. */
    fun finishInput(): RectF? {
        val b = brush ?: return null
        val dirty = RectF()
        smoother?.finish()?.forEach { p -> emit(b, p)?.let { dirty.union(it) } }
        return if (dirty.isEmpty) null else dirty
    }

    private fun emit(b: BrushPreset, p: StrokePoint): RectF? {
        val dirty = RectF()
        val sym = symmetry
        val copies = sym?.copies ?: 1
        for (i in 0 until copies) {
            val (x, y) = sym?.map(i, p.x, p.y) ?: (p.x to p.y)
            val mirrored = sym?.isMirrored(i) ?: false
            val dabs = spacers[i].add(p.copy(x = x, y = y))
            for (d in dabs) drawDab(b, d, mirrored, dirty)
        }
        if (dirty.isEmpty) return null
        bounds.union(dirty)
        return dirty
    }

    private fun drawDab(b: BrushPreset, dab: Dab, mirrored: Boolean, dirty: RectF) {
        dabIndex++
        var diameter = b.diameterAt(dab.pressure)
        if (b.sizeJitter > 0f) diameter *= 1f - b.sizeJitter * rnd.nextFloat()
        var alpha = b.flow * (1f - b.pressureOpacity + b.pressureOpacity * dab.pressure.coerceIn(0f, 1f))
        if (b.opacityJitter > 0f) alpha *= 1f - b.opacityJitter * rnd.nextFloat()
        var cx = dab.x
        var cy = dab.y
        if (b.scatter > 0f) {
            cx += (rnd.nextFloat() * 2f - 1f) * b.scatter * diameter * 2f
            cy += (rnd.nextFloat() * 2f - 1f) * b.scatter * diameter * 2f
        }
        val half = diameter / 2f
        if (b.pixelPerfect) {
            val s = max(1, diameter.toInt())
            val left = floor(cx - s / 2f).toInt()
            val top = floor(cy - s / 2f).toInt()
            pixelPaint.alpha = (alpha * 255).toInt().coerceIn(1, 255)
            forTiles(left.toFloat(), top.toFloat(), (left + s).toFloat(), (top + s).toFloat()) { canvas, ox, oy ->
                canvas.drawRect((left - ox).toFloat(), (top - oy).toFloat(), (left + s - ox).toFloat(), (top + s - oy).toFloat(), pixelPaint)
            }
            dirty.union(left.toFloat(), top.toFloat(), (left + s).toFloat(), (top + s).toFloat())
            return
        }
        dabPaint.alpha = (alpha * 255).toInt().coerceIn(1, 255)
        val stamp = stamps.stamp(b, dabIndex)
        val rotateTip = b.tip == BrushTip.FLAT
        val angle = if (mirrored) -b.angle else b.angle
        val randomRotation = if (b.tip == BrushTip.GRAIN || b.tip == BrushTip.SPECKLE) rnd.nextFloat() * 360f else 0f
        val extent = if (rotateTip || randomRotation != 0f) half * 1.42f else half
        forTiles(cx - extent, cy - extent, cx + extent, cy + extent) { canvas, ox, oy ->
            val lx = cx - ox
            val ly = cy - oy
            dst.set(lx - half, ly - half, lx + half, ly + half)
            if (rotateTip || randomRotation != 0f) {
                canvas.save()
                canvas.rotate(if (rotateTip) angle else randomRotation, lx, ly)
                canvas.drawBitmap(stamp, null, dst, dabPaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(stamp, null, dst, dabPaint)
            }
        }
        dirty.union(cx - extent - 1, cy - extent - 1, cx + extent + 1, cy + extent + 1)
    }

    private inline fun forTiles(l: Float, t: Float, r: Float, b: Float, block: (Canvas, Int, Int) -> Unit) {
        val c0 = max(0, floor(l / TILE).toInt())
        val c1 = min(cols - 1, floor(r / TILE).toInt())
        val r0 = max(0, floor(t / TILE).toInt())
        val r1 = min(rows - 1, floor(b / TILE).toInt())
        for (row in r0..r1) for (col in c0..c1) {
            val i = row * cols + col
            val canvas = tileCanvases[i] ?: run {
                val bmp = Bitmap.createBitmap(TILE, TILE, Bitmap.Config.ARGB_8888)
                tiles[i] = bmp
                Canvas(bmp).also { tileCanvases[i] = it }
            }
            touched[i] = true
            block(canvas, col * TILE, row * TILE)
        }
    }

    /** Paint used to composite the stroke onto its layer. */
    fun compositePaint(): Paint {
        val b = brush
        return Paint(Paint.FILTER_BITMAP_FLAG).apply {
            alpha = ((b?.opacity ?: 1f) * 255).toInt().coerceIn(0, 255)
            blendMode = when {
                isEraser -> BlendMode.DST_OUT
                b != null -> b.blendMode.toAndroid()
                else -> BlendMode.SRC_OVER
            }
        }
    }

    /** Draws the stroke tiles onto [canvas] (already in canvas coordinates) with [paint]. */
    fun drawTiles(canvas: Canvas, paint: Paint?) {
        for (row in 0 until rows) for (col in 0 until cols) {
            val i = row * cols + col
            if (!touched[i]) continue
            val bmp = tiles[i] ?: continue
            canvas.drawBitmap(bmp, (col * TILE).toFloat(), (row * TILE).toFloat(), paint)
        }
    }

    /**
     * Draws the stroke as it should appear: textured brushes get paper grain applied, and the
     * composite paint carries opacity and blend mode. [clip] restricts it to a selection.
     */
    fun drawStroke(canvas: Canvas, clip: Path?) {
        if (bounds.isEmpty) return
        val b = brush ?: return
        canvas.save()
        clip?.let { canvas.clipPath(it) }
        val paint = compositePaint()
        if (b.texture > 0f && b.tip == BrushTip.GRAIN) {
            val layerBounds = RectF(bounds)
            canvas.saveLayer(layerBounds, paint)
            drawTiles(canvas, null)
            val grain = Paint().apply {
                shader = stamps.grainShader()
                blendMode = BlendMode.DST_IN
            }
            canvas.drawRect(layerBounds, grain)
            canvas.restore()
        } else {
            drawTiles(canvas, paint)
        }
        canvas.restore()
    }

    /** Integer bounds of the stroke clamped to the canvas, for undo snapshots. */
    fun integerBounds(): Rect? {
        if (bounds.isEmpty) return null
        val r = Rect(
            floor(bounds.left).toInt().coerceIn(0, width),
            floor(bounds.top).toInt().coerceIn(0, height),
            ceil(bounds.right).toInt().coerceIn(0, width),
            ceil(bounds.bottom).toInt().coerceIn(0, height),
        )
        return if (r.isEmpty) null else r
    }

    fun clear() {
        for (i in tiles.indices) {
            if (touched[i]) {
                tiles[i]?.eraseColor(Color.TRANSPARENT)
                touched[i] = false
            }
        }
        bounds.setEmpty()
        active = false
        brush = null
    }

    fun release() {
        tiles.forEach { it?.recycle() }
        tiles.fill(null)
        tileCanvases.fill(null)
    }

    companion object {
        const val TILE = 256
    }
}
