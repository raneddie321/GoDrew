package com.godraw.app.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import com.godraw.core.animation.KeyframeSampler
import com.godraw.core.animation.LayerTransform
import com.godraw.core.model.CellKey
import com.godraw.core.model.Layer
import com.godraw.core.model.ProjectDocument

/** Supplies cell bitmaps to the compositor (null = empty cell). */
fun interface CellSource {
    fun cell(key: CellKey): Bitmap?
}

/** Pixels being edited that must be shown in place of / on top of a layer. */
class LayerOverlay(
    val layerId: String,
    /** Replaces the layer's cell (effect previews, mask editing). */
    val replacement: Bitmap? = null,
    /** Stroke in progress, drawn on top of the layer content. */
    val stroke: StrokeRenderer? = null,
    val strokeClip: Path? = null,
    /** Floating selection being transformed. */
    val floating: Bitmap? = null,
    val floatingMatrix: Matrix? = null,
    /** Shape preview drawn into the layer so it blends like the final result. */
    val shapePath: Path? = null,
    val shapePaint: Paint? = null,
    /** When editing a mask, the mask stroke is applied to the mask instead of the pixels. */
    val strokeTargetsMask: Boolean = false,
)

data class RenderOptions(
    val drawBackground: Boolean = true,
    val overlay: LayerOverlay? = null,
    /** Fractional frame position used to sample keyframes (defaults to the frame index). */
    val time: Float? = null,
    val hiddenLayerIds: Set<String> = emptySet(),
    /** When rendering a single layer for merging, everything else is skipped. */
    val onlyLayerId: String? = null,
)

/**
 * Renders a frame of a document onto any Canvas (screen, thumbnail, export). Implements
 * per-layer opacity and blend modes, groups, clipping masks, layer masks, static layers,
 * keyframe transforms and the background color.
 */
class Compositor {

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val maskPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { blendMode = BlendMode.DST_IN }
    private val atopPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { blendMode = BlendMode.SRC_ATOP }
    private val tmpMatrix = Matrix()

    fun drawFrame(canvas: Canvas, doc: ProjectDocument, frameIndex: Int, cells: CellSource, options: RenderOptions = RenderOptions()) {
        val frameId = doc.frames.getOrNull(frameIndex)?.id ?: return
        val bounds = RectF(0f, 0f, doc.width.toFloat(), doc.height.toFloat())
        canvas.save()
        canvas.clipRect(bounds)
        val ctx = Ctx(doc, frameId, options.time ?: frameIndex.toFloat(), cells, options, bounds)
        drawSiblings(canvas, doc.layers.filter { it.parentId == null }, ctx)
        canvas.restore()
    }

    /** Draws a frame tinted with [color] for onion skinning. */
    fun drawOnionSkin(canvas: Canvas, doc: ProjectDocument, frameIndex: Int, cells: CellSource, color: Int?, alpha: Float) {
        val bounds = RectF(0f, 0f, doc.width.toFloat(), doc.height.toFloat())
        val paint = Paint().apply {
            this.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            if (color != null) colorFilter = PorterDuffColorFilter(color or 0xFF000000.toInt(), PorterDuff.Mode.SRC_IN)
        }
        canvas.saveLayer(bounds, paint)
        drawFrame(canvas, doc, frameIndex, cells, RenderOptions(drawBackground = false))
        canvas.restore()
    }

    private class Ctx(
        val doc: ProjectDocument,
        val frameId: String,
        val time: Float,
        val cells: CellSource,
        val options: RenderOptions,
        val bounds: RectF,
    )

    private fun isVisible(layer: Layer, ctx: Ctx): Boolean {
        if (!layer.visible || layer.id in ctx.options.hiddenLayerIds) return false
        val only = ctx.options.onlyLayerId
        if (only != null && layer.id != only && !(layer.isGroup && ctx.doc.children(layer.id).any { it.id == only })) return false
        if (layer.isBackground && !ctx.options.drawBackground) return false
        return true
    }

    private fun drawSiblings(canvas: Canvas, layers: List<Layer>, ctx: Ctx) {
        var i = 0
        while (i < layers.size) {
            val base = layers[i]
            var j = i + 1
            val clipped = ArrayList<Layer>()
            while (j < layers.size && layers[j].clipToBelow && !layers[j].isGroup && !base.isGroup) {
                clipped += layers[j]
                j++
            }
            if (isVisible(base, ctx)) {
                val visibleClipped = clipped.filter { isVisible(it, ctx) }
                if (visibleClipped.isEmpty()) {
                    drawLayer(canvas, base, ctx)
                } else {
                    drawClippingGroup(canvas, base, visibleClipped, ctx)
                }
            }
            i = j
        }
    }

    private fun layerPaint(layer: Layer, extraAlpha: Float = 1f): Paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        alpha = (layer.opacity * extraAlpha * 255).toInt().coerceIn(0, 255)
        blendMode = layer.blendMode.toAndroid()
    }

    private fun transformOf(layer: Layer, ctx: Ctx): LayerTransform =
        if (layer.animation.isAnimated) KeyframeSampler.sample(layer.animation, ctx.time) else LayerTransform.IDENTITY

    /** Canvas transform for a layer's keyframes: rotate/scale about the canvas center, then move. */
    fun layerMatrix(t: LayerTransform, doc: ProjectDocument, out: Matrix = Matrix()): Matrix {
        out.reset()
        val cx = doc.width / 2f
        val cy = doc.height / 2f
        out.postScale(t.scale, t.scale, cx, cy)
        out.postRotate(t.rotation, cx, cy)
        out.postTranslate(t.x, t.y)
        return out
    }

    private fun drawLayer(canvas: Canvas, layer: Layer, ctx: Ctx) {
        if (layer.isGroup) {
            val t = transformOf(layer, ctx)
            canvas.saveLayer(ctx.bounds, layerPaint(layer, t.opacity))
            if (!t.isIdentity) canvas.concat(layerMatrix(t, ctx.doc, tmpMatrix))
            drawSiblings(canvas, ctx.doc.children(layer.id), ctx)
            canvas.restore()
            return
        }
        val t = transformOf(layer, ctx)
        val overlay = ctx.options.overlay?.takeIf { it.layerId == layer.id }
        val needsOffscreen = overlay != null || (layer.hasMask && layer.maskEnabled) || layer.isBackground
        if (!needsOffscreen) {
            val bmp = ctx.cells.cell(CellKey.forLayer(layer, ctx.frameId)) ?: return
            canvas.save()
            if (!t.isIdentity) canvas.concat(layerMatrix(t, ctx.doc, tmpMatrix))
            canvas.drawBitmap(bmp, 0f, 0f, layerPaint(layer, t.opacity))
            canvas.restore()
            return
        }
        canvas.saveLayer(ctx.bounds, layerPaint(layer, t.opacity))
        drawLayerContent(canvas, layer, t, overlay, ctx)
        canvas.restore()
    }

    /** Draws the layer's pixels at full opacity (caller applies opacity/blend via saveLayer). */
    private fun drawLayerContent(canvas: Canvas, layer: Layer, t: LayerTransform, overlay: LayerOverlay?, ctx: Ctx) {
        canvas.save()
        if (!t.isIdentity) canvas.concat(layerMatrix(t, ctx.doc, tmpMatrix))
        if (layer.isBackground && ctx.doc.backgroundColor != 0) {
            canvas.drawColor(ctx.doc.backgroundColor)
        }
        val cell = overlay?.replacement?.takeIf { !overlay.strokeTargetsMask } ?: ctx.cells.cell(CellKey.forLayer(layer, ctx.frameId))
        if (cell != null) canvas.drawBitmap(cell, 0f, 0f, bitmapPaint)
        if (overlay != null) {
            if (!overlay.strokeTargetsMask) overlay.stroke?.takeIf { it.active }?.drawStroke(canvas, overlay.strokeClip)
            if (overlay.shapePath != null && overlay.shapePaint != null) {
                canvas.save()
                overlay.strokeClip?.let { canvas.clipPath(it) }
                canvas.drawPath(overlay.shapePath, overlay.shapePaint)
                canvas.restore()
            }
            val floating = overlay.floating
            if (floating != null) canvas.drawBitmap(floating, overlay.floatingMatrix ?: Matrix(), bitmapPaint)
        }
        if (layer.hasMask && layer.maskEnabled) {
            val maskBmp = if (overlay?.strokeTargetsMask == true && overlay.replacement != null) {
                overlay.replacement
            } else {
                ctx.cells.cell(CellKey.forLayer(layer, ctx.frameId, isMask = true))
            }
            if (overlay?.strokeTargetsMask == true && overlay.stroke?.active == true) {
                // Preview the mask stroke: render mask + stroke into a temporary layer, then apply.
                canvas.saveLayer(ctx.bounds, maskPaint)
                if (maskBmp != null) canvas.drawBitmap(maskBmp, 0f, 0f, bitmapPaint) else canvas.drawColor(0xFFFFFFFF.toInt())
                overlay.stroke.drawStroke(canvas, overlay.strokeClip)
                canvas.restore()
            } else if (maskBmp != null) {
                canvas.drawBitmap(maskBmp, 0f, 0f, maskPaint)
            }
        }
        canvas.restore()
    }

    private fun drawClippingGroup(canvas: Canvas, base: Layer, clipped: List<Layer>, ctx: Ctx) {
        val baseT = transformOf(base, ctx)
        canvas.saveLayer(ctx.bounds, layerPaint(base, baseT.opacity))
        drawLayerContent(canvas, base, baseT, ctx.options.overlay?.takeIf { it.layerId == base.id }, ctx)
        for (layer in clipped) {
            val t = transformOf(layer, ctx)
            // Each clipped layer is composited SRC_ATOP so it only shows where the base has pixels.
            val paint = Paint(atopPaint).apply { alpha = (layer.opacity * t.opacity * 255).toInt().coerceIn(0, 255) }
            canvas.saveLayer(ctx.bounds, paint)
            drawLayerContent(canvas, layer, t, ctx.options.overlay?.takeIf { it.layerId == layer.id }, ctx)
            canvas.restore()
        }
        canvas.restore()
    }

    /** Renders one layer (with its transform, mask and opacity) — used by merge down. */
    fun drawSingleLayer(canvas: Canvas, doc: ProjectDocument, layer: Layer, frameIndex: Int, cells: CellSource) {
        drawFrame(
            canvas,
            doc.copy(layers = doc.layers.map { if (it.id == layer.id) it.copy(clipToBelow = false, parentId = null) else it }),
            frameIndex,
            cells,
            RenderOptions(onlyLayerId = layer.id),
        )
    }

    /** Renders a whole frame into a new bitmap of the given size. */
    fun renderBitmap(doc: ProjectDocument, frameIndex: Int, cells: CellSource, outWidth: Int, outHeight: Int, options: RenderOptions = RenderOptions()): Bitmap {
        val bmp = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(outWidth / doc.width.toFloat(), outHeight / doc.height.toFloat())
        drawFrame(canvas, doc, frameIndex, cells, options)
        return bmp
    }
}
