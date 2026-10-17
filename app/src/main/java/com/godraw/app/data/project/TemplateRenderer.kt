package com.godraw.app.data.project

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import com.godraw.core.model.CellKey
import com.godraw.core.model.Frame
import com.godraw.core.model.Ids
import com.godraw.core.model.Layer
import com.godraw.core.model.ProjectDocument
import com.godraw.core.model.TemplateDecoration
import kotlin.math.max

/** Localized names for layers created by templates. */
data class TemplateNames(val panels: String, val guide: String, val storyboard: String)

/** Adds the starting artwork (panels, guides, grids) that templates provide. */
object TemplateRenderer {

    fun apply(
        doc: ProjectDocument,
        decoration: TemplateDecoration,
        names: TemplateNames,
    ): Pair<ProjectDocument, Map<CellKey, Bitmap>> = when (decoration) {
        TemplateDecoration.NONE -> doc to emptyMap()
        TemplateDecoration.PIXEL_GRID -> doc.copy(guides = doc.guides.copy(showGrid = true, gridSize = max(1, doc.width / 16))) to emptyMap()
        TemplateDecoration.COMIC_PANELS -> addOverlay(doc, names.panels, static = false) { c, w, h -> drawComicPanels(c, w, h) }
        TemplateDecoration.STORYBOARD -> addOverlay(doc, names.storyboard, static = false) { c, w, h -> drawStoryboard(c, w, h) }
        TemplateDecoration.BOUNCING_BALL_GUIDE -> {
            val (withGuide, cells) = addOverlay(doc, names.guide, static = true, opacity = 0.5f, locked = true) { c, w, h ->
                drawBallGuide(c, w, h)
            }
            val frames = withGuide.frames + List(11) { Frame(Ids.newId()) }
            withGuide.copy(frames = frames) to cells
        }
    }

    private fun addOverlay(
        doc: ProjectDocument,
        name: String,
        static: Boolean,
        opacity: Float = 1f,
        locked: Boolean = false,
        draw: (Canvas, Float, Float) -> Unit,
    ): Pair<ProjectDocument, Map<CellKey, Bitmap>> {
        val layer = Layer(id = Ids.newId(), name = name, isStatic = static, opacity = opacity, locked = locked)
        val bitmap = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        draw(Canvas(bitmap), doc.width.toFloat(), doc.height.toFloat())
        val key = CellKey.forLayer(layer, doc.frames.first().id)
        // Overlay goes on top; drawing continues on the existing active layer below it.
        return doc.copy(layers = doc.layers + layer) to mapOf(key to bitmap)
    }

    private fun linePaint(width: Float, color: Int = 0xFF111111.toInt()) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        this.color = color
        strokeJoin = Paint.Join.MITER
    }

    private fun drawComicPanels(c: Canvas, w: Float, h: Float) {
        val margin = w * 0.05f
        val gutter = w * 0.025f
        val paint = linePaint(max(2f, w * 0.005f))
        val rows = listOf(listOf(0.5f, 0.5f), listOf(1f), listOf(0.38f, 0.62f))
        val rowHeights = listOf(0.3f, 0.34f, 0.36f)
        val innerW = w - margin * 2
        val innerH = h - margin * 2 - gutter * (rows.size - 1)
        var y = margin
        rows.forEachIndexed { r, cols ->
            val rowH = innerH * rowHeights[r]
            val availableW = innerW - gutter * (cols.size - 1)
            var x = margin
            cols.forEach { frac ->
                val cw = availableW * frac
                c.drawRect(x, y, x + cw, y + rowH, paint)
                x += cw + gutter
            }
            y += rowH + gutter
        }
    }

    private fun drawStoryboard(c: Canvas, w: Float, h: Float) {
        val cols = 3
        val rows = 2
        val margin = w * 0.04f
        val gap = w * 0.03f
        val captionH = h * 0.08f
        val cellW = (w - margin * 2 - gap * (cols - 1)) / cols
        val cellH = (h - margin * 2 - gap * (rows - 1)) / rows - captionH
        val frame = linePaint(max(2f, w * 0.002f), 0xFF333333.toInt())
        val caption = linePaint(max(1f, w * 0.001f), 0xFFAAAAAA.toInt())
        for (r in 0 until rows) for (col in 0 until cols) {
            val x = margin + col * (cellW + gap)
            val y = margin + r * (cellH + captionH + gap)
            c.drawRect(x, y, x + cellW, y + cellH, frame)
            val l1 = y + cellH + captionH * 0.4f
            val l2 = y + cellH + captionH * 0.8f
            c.drawLine(x, l1, x + cellW, l1, caption)
            c.drawLine(x, l2, x + cellW * 0.7f, l2, caption)
        }
    }

    private fun drawBallGuide(c: Canvas, w: Float, h: Float) {
        val ground = h * 0.85f
        val groundPaint = linePaint(max(2f, w * 0.003f), 0xFF5A6070.toInt())
        c.drawLine(0f, ground, w, ground, groundPaint)
        val arcPaint = linePaint(max(1.5f, w * 0.002f), 0xFF1F6BFF.toInt()).apply {
            pathEffect = DashPathEffect(floatArrayOf(w * 0.01f, w * 0.008f), 0f)
        }
        // Three decaying bounces as parabolic arcs.
        val path = Path()
        var x = w * 0.08f
        var height = h * 0.65f
        var span = w * 0.36f
        path.moveTo(x, ground - height)
        path.quadTo(x + span * 0.25f, ground - height, x + span * 0.5f, ground)
        x += span * 0.5f
        repeat(3) {
            height *= 0.55f
            span *= 0.7f
            path.quadTo(x + span / 2f, ground - height * 2f, x + span, ground)
            x += span
        }
        c.drawPath(path, arcPaint)
    }
}
