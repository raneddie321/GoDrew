package com.godraw.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.data.settings.StylusButtonAction
import com.godraw.core.animation.KeyframeSampler
import com.godraw.core.model.ProjectDocument
import com.godraw.core.model.SymmetryMode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The drawing surface. Handles finger and stylus input with pressure, palm rejection, stylus
 * buttons and the eraser end, two-finger pan/zoom/rotate, multi-finger tap shortcuts, long-press
 * eyedropper, and draws the canvas with onion skins and all editing overlays.
 */
@SuppressLint("ViewConstructor")
class CanvasView(context: Context, private val engine: DrawingEngine) : View(context) {

    var settings: AppSettings = AppSettings()
    var backdropColor: Int = 0xFF0D0F14.toInt()
    var onUndoGesture: (() -> Unit)? = null
    var onRedoGesture: (() -> Unit)? = null
    var onColorPicked: ((Int) -> Unit)? = null

    val viewport = Viewport()
    private var fitted = false
    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    // ---------------------------------------------------------------- paints

    private val checkerTile: Bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
        val c = Canvas(this)
        c.drawColor(0xFFF0F0F2.toInt())
        val p = Paint().apply { color = 0xFFD5D5DA.toInt() }
        c.drawRect(0f, 0f, 16f, 16f, p)
        c.drawRect(16f, 16f, 32f, 32f, p)
    }
    private val checkerShader = BitmapShader(checkerTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    private val checkerPaint = Paint().apply { shader = checkerShader }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        setShadowLayer(18f, 0f, 4f, 0x66000000)
    }
    private val antsWhite = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.WHITE }
    private val antsBlack = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xAA00C6FF.toInt() }
    private val gridPaint = Paint().apply { style = Paint.Style.STROKE; color = 0x55808080 }
    private val rulerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xCCFF8A00.toInt() }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val handleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFF1F6BFF.toInt() }
    private val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFE91E8C.toInt() }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xAA000000.toInt() }
    private val cursorPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xAAFFFFFF.toInt() }
    private var antsPhase = 0f

    private val invalidateCallback: () -> Unit = { postInvalidateOnAnimation() }

    init {
        isFocusable = true
        engine.onInvalidate = invalidateCallback
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        engine.onInvalidate = invalidateCallback
    }

    fun resetView() {
        if (width > 0) viewport.fit(width, height, engine.doc.width, engine.doc.height, 24 * density)
        invalidate()
    }

    fun mirrorView() {
        viewport.mirror(width / 2f, height / 2f)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!fitted || oldw == 0) {
            viewport.fit(w, h, engine.doc.width, engine.doc.height, 24 * density)
            fitted = true
        } else {
            viewport.pan((w - oldw) / 2f, (h - oldh) / 2f)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (engine.onInvalidate === invalidateCallback) engine.onInvalidate = null
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        val doc = engine.doc
        val st = engine.state.value
        canvas.drawColor(backdropColor)
        val zoom = viewport.zoom
        engine.handleRadius = 26 * density / zoom
        val bounds = RectF(0f, 0f, doc.width.toFloat(), doc.height.toFloat())

        canvas.save()
        canvas.concat(viewport.matrix)
        canvas.drawRect(bounds, shadowPaint)
        if (settings.checkerboardBackground || doc.backgroundColor == 0) {
            val m = Matrix()
            m.setScale(1f / zoom, 1f / zoom)
            checkerShader.setLocalMatrix(m)
            canvas.drawRect(bounds, checkerPaint)
        } else {
            canvas.drawRect(bounds, Paint().apply { color = Color.WHITE })
        }

        val frame = engine.displayFrame.coerceIn(0, doc.frames.lastIndex)
        val overlay = buildOverlay(st)
        val onion = doc.onionSkin
        val showOnion = doc.isAnimation && onion.enabled && !st.playing && doc.frames.size > 1
        if (showOnion) {
            val background = doc.layers.firstOrNull { it.isBackground && it.visible }
            if (background != null) {
                engine.compositor.drawFrame(canvas, doc, frame, engine.cellSource, RenderOptions(onlyLayerId = background.id))
            }
            for (i in onion.framesBefore downTo 1) {
                val idx = frame - i
                if (idx < 0 && !doc.loop) continue
                val wrapped = ((idx % doc.frames.size) + doc.frames.size) % doc.frames.size
                if (wrapped == frame) continue
                val alpha = onion.opacity * (1f - (i - 1) / (onion.framesBefore + 1f))
                engine.compositor.drawOnionSkin(canvas, doc, wrapped, engine.cellSource, if (onion.tint) onion.beforeColor else null, alpha)
            }
            for (i in 1..onion.framesAfter) {
                val idx = frame + i
                if (idx >= doc.frames.size && !doc.loop) continue
                val wrapped = idx % doc.frames.size
                if (wrapped == frame) continue
                val alpha = onion.opacity * (1f - (i - 1) / (onion.framesAfter + 1f))
                engine.compositor.drawOnionSkin(canvas, doc, wrapped, engine.cellSource, if (onion.tint) onion.afterColor else null, alpha)
            }
            engine.compositor.drawFrame(
                canvas, doc, frame, engine.cellSource,
                RenderOptions(overlay = overlay, hiddenLayerIds = background?.let { setOf(it.id) } ?: emptySet()),
            )
        } else {
            engine.compositor.drawFrame(canvas, doc, frame, engine.cellSource, RenderOptions(overlay = overlay))
        }

        drawGuides(canvas, doc, zoom)
        drawSelection(canvas, zoom)
        drawFloatingHandles(canvas, zoom)
        if (st.tool == ToolType.ANIMATE && !st.playing) drawAnimateHandles(canvas, doc, zoom)
        canvas.restore()

        drawCursor(canvas)

        if (engine.selection != null || engine.pendingSelection != null) {
            antsPhase = (antsPhase + 1f) % 20f
            postInvalidateDelayed(80)
        }
    }

    private fun buildOverlay(st: EngineState): LayerOverlay? {
        val layer = st.activeLayer ?: return null
        val floating = engine.floating
        val clip = engine.selection?.path?.let { p ->
            val m = engine.activeLayerMatrix()
            if (m == null) p else {
                val inv = Matrix()
                m.invert(inv)
                Path(p).apply { transform(inv) }
            }
        }
        return LayerOverlay(
            layerId = layer.id,
            replacement = if (st.editingMask) null else engine.effectPreview,
            stroke = engine.stroke.takeIf { it.active },
            strokeClip = clip,
            floating = floating?.bitmap,
            floatingMatrix = floating?.matrix,
            shapePath = engine.shapePreview,
            shapePaint = engine.shapePaint,
            strokeTargetsMask = st.editingMask,
        )
    }

    private fun drawGuides(canvas: Canvas, doc: ProjectDocument, zoom: Float) {
        val g = doc.guides
        val w = doc.width.toFloat()
        val h = doc.height.toFloat()
        val stroke = 1.2f / zoom
        if (g.showGrid && g.gridSize * zoom >= 6f) {
            gridPaint.strokeWidth = 1f / zoom
            var x = g.gridSize.toFloat()
            while (x < w) { canvas.drawLine(x, 0f, x, h, gridPaint); x += g.gridSize }
            var y = g.gridSize.toFloat()
            while (y < h) { canvas.drawLine(0f, y, w, y, gridPaint); y += g.gridSize }
        }
        guidePaint.strokeWidth = stroke
        guidePaint.pathEffect = DashPathEffect(floatArrayOf(10f / zoom, 6f / zoom), 0f)
        when (g.symmetry) {
            SymmetryMode.VERTICAL -> canvas.drawLine(w / 2, 0f, w / 2, h, guidePaint)
            SymmetryMode.HORIZONTAL -> canvas.drawLine(0f, h / 2, w, h / 2, guidePaint)
            SymmetryMode.QUADRANT -> {
                canvas.drawLine(w / 2, 0f, w / 2, h, guidePaint)
                canvas.drawLine(0f, h / 2, w, h / 2, guidePaint)
            }
            SymmetryMode.RADIAL -> {
                val r = hypot(w, h)
                for (i in 0 until g.radialSegments) {
                    val a = 2 * Math.PI * i / g.radialSegments - Math.PI / 2
                    canvas.drawLine(w / 2, h / 2, w / 2 + (r * Math.cos(a)).toFloat(), h / 2 + (r * Math.sin(a)).toFloat(), guidePaint)
                }
            }
            SymmetryMode.NONE -> Unit
        }
        guidePaint.pathEffect = null
        if (g.rulerEnabled) {
            rulerPaint.strokeWidth = 3f / zoom
            val dx = g.rulerX2 - g.rulerX1
            val dy = g.rulerY2 - g.rulerY1
            val len = hypot(dx, dy).coerceAtLeast(1f)
            val ext = hypot(w, h) * 2
            canvas.drawLine(
                g.rulerX1 - dx / len * ext, g.rulerY1 - dy / len * ext,
                g.rulerX1 + dx / len * ext, g.rulerY1 + dy / len * ext, rulerPaint,
            )
        }
        if (g.perspectiveEnabled && g.vanishingPoints.size >= 2) {
            guidePaint.strokeWidth = stroke
            g.horizonY?.let { hy -> canvas.drawLine(-w, hy, w * 2, hy, guidePaint) }
            val vps = g.vanishingPoints.chunked(2).filter { it.size == 2 }
            val r = hypot(w, h) * 2
            for (vp in vps) {
                for (i in 0 until 24) {
                    val a = 2 * Math.PI * i / 24
                    canvas.drawLine(vp[0], vp[1], vp[0] + (r * Math.cos(a)).toFloat(), vp[1] + (r * Math.sin(a)).toFloat(), guidePaint)
                }
                canvas.drawCircle(vp[0], vp[1], 6f / zoom, handleStroke)
            }
        }
    }

    private fun drawSelection(canvas: Canvas, zoom: Float) {
        val path = engine.pendingSelection ?: engine.selection?.path ?: return
        antsWhite.strokeWidth = 1.5f / zoom
        antsBlack.strokeWidth = 1.5f / zoom
        antsBlack.pathEffect = DashPathEffect(floatArrayOf(6f / zoom, 6f / zoom), antsPhase / zoom)
        canvas.drawPath(path, antsWhite)
        canvas.drawPath(path, antsBlack)
    }

    private fun drawFloatingHandles(canvas: Canvas, zoom: Float) {
        val f = engine.floating ?: return
        canvas.save()
        engine.activeLayerMatrix()?.let { canvas.concat(it) }
        val c = f.corners()
        handleStroke.strokeWidth = 2f / zoom
        val quad = Path().apply {
            moveTo(c[0], c[1]); lineTo(c[2], c[3]); lineTo(c[4], c[5]); lineTo(c[6], c[7]); close()
        }
        canvas.drawPath(quad, handleStroke)
        val r = 9f * density / zoom
        for (i in 0 until 4) {
            canvas.drawCircle(c[i * 2], c[i * 2 + 1], r, handleFill)
            canvas.drawCircle(c[i * 2], c[i * 2 + 1], r, handleStroke)
        }
        val (rx, ry) = engine.rotationHandle(f)
        canvas.drawLine((c[0] + c[2]) / 2, (c[1] + c[3]) / 2, rx, ry, handleStroke)
        canvas.drawCircle(rx, ry, r, handleFill)
        canvas.drawCircle(rx, ry, r, handleStroke)
        canvas.restore()
    }

    private fun drawAnimateHandles(canvas: Canvas, doc: ProjectDocument, zoom: Float) {
        val layer = doc.layer(doc.activeLayerId) ?: return
        val t = KeyframeSampler.sample(layer.animation, engine.displayFrame.toFloat())
        val cx = doc.width / 2f + t.x
        val cy = doc.height / 2f + t.y
        val radius = min(doc.width, doc.height) / 2f * t.scale
        handleStroke.strokeWidth = 2f / zoom
        canvas.drawCircle(cx, cy, radius, handleStroke)
        canvas.drawCircle(cx, cy, 6f * density / zoom, handleFill)
        canvas.drawCircle(cx, cy - radius - engine.handleRadius, 9f * density / zoom, handleFill)
        canvas.drawCircle(cx, cy - radius - engine.handleRadius, 9f * density / zoom, handleStroke)
        val path = KeyframeSampler.motionPath(layer.animation)
        if (path.size > 1) {
            pathPaint.strokeWidth = 2f / zoom
            val p = Path()
            path.forEachIndexed { i, (x, y) ->
                val px = doc.width / 2f + x
                val py = doc.height / 2f + y
                if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
            }
            canvas.drawPath(p, pathPaint)
        }
    }

    private var cursorX = -1f
    private var cursorY = -1f
    private var cursorVisible = false

    private fun drawCursor(canvas: Canvas) {
        if (!settings.showBrushCursor || !cursorVisible) return
        val st = engine.state.value
        if (!st.tool.isPainting) return
        val r = max(2f, st.brush.size * viewport.zoom / 2f)
        cursorPaint.strokeWidth = 1.5f
        cursorPaint2.strokeWidth = 1.5f
        canvas.drawCircle(cursorX, cursorY, r, cursorPaint)
        canvas.drawCircle(cursorX, cursorY, r + 1.5f, cursorPaint2)
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS || event.getToolType(0) == MotionEvent.TOOL_TYPE_ERASER) {
            lastStylusTime = SystemClock.uptimeMillis()
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    cursorX = event.x; cursorY = event.y; cursorVisible = true
                }
                MotionEvent.ACTION_HOVER_EXIT -> cursorVisible = false
            }
            invalidate()
            return true
        }
        return super.onHoverEvent(event)
    }

    // ---------------------------------------------------------------- input

    private enum class Mode { NONE, DRAW, NAVIGATE, PICK }

    private var mode = Mode.NONE
    private var drawPointerId = -1
    private var downTime = 0L
    private var maxPointers = 0
    private var travelled = 0f
    private var lastStylusTime = 0L
    private var downX = 0f
    private var downY = 0f
    private val prev = FloatArray(4)
    private var prevCount = 0

    private val longPress = Runnable {
        if (mode == Mode.DRAW && travelled < touchSlop && settings.longPressEyedropper && engine.state.value.tool.isPainting) {
            engine.pointerCancel()
            mode = Mode.PICK
            pick(downX, downY, final = false)
        }
    }

    private fun isStylus(event: MotionEvent, index: Int): Boolean {
        val t = event.getToolType(index)
        return t == MotionEvent.TOOL_TYPE_STYLUS || t == MotionEvent.TOOL_TYPE_ERASER
    }

    private fun isPalm(event: MotionEvent, index: Int): Boolean {
        if (!settings.palmRejection) return false
        if (isStylus(event, index)) return false
        val recentStylus = SystemClock.uptimeMillis() - lastStylusTime < 3_000
        val large = event.getTouchMajor(index) > 42 * density
        return recentStylus || large
    }

    private fun toolOverride(event: MotionEvent, index: Int): ToolType? {
        if (event.getToolType(index) == MotionEvent.TOOL_TYPE_ERASER) return ToolType.ERASER
        val buttonPressed = event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY) != 0
        if (buttonPressed && isStylus(event, index)) {
            return when (settings.stylusButtonAction) {
                StylusButtonAction.ERASER -> ToolType.ERASER
                StylusButtonAction.EYEDROPPER -> ToolType.EYEDROPPER
                StylusButtonAction.NONE -> null
            }
        }
        return null
    }

    private fun pressureOf(event: MotionEvent, index: Int, historyPos: Int = -1): Float {
        if (!isStylus(event, index)) return 1f
        return if (historyPos >= 0) event.getHistoricalPressure(index, historyPos) else event.getPressure(index)
    }

    private fun wasCanceled(event: MotionEvent): Boolean =
        Build.VERSION.SDK_INT >= 33 && (event.flags and MotionEvent.FLAG_CANCELED) != 0

    private fun pick(x: Float, y: Float, final: Boolean) {
        val (cx, cy) = viewport.toCanvas(x, y)
        val color = engine.sampleColor(cx, cy) ?: return
        engine.setColor(color)
        if (final) onColorPicked?.invoke(color)
    }

    private fun storePrev(event: MotionEvent) {
        prevCount = min(2, event.pointerCount)
        for (i in 0 until prevCount) {
            prev[i * 2] = event.getX(i)
            prev[i * 2 + 1] = event.getY(i)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downTime = event.eventTime
                maxPointers = 1
                travelled = 0f
                downX = event.x
                downY = event.y
                val stylus = isStylus(event, 0)
                if (stylus) lastStylusTime = SystemClock.uptimeMillis()
                cursorX = event.x; cursorY = event.y; cursorVisible = stylus
                val st = engine.state.value
                val canDraw = st.tool != ToolType.HAND && !st.playing &&
                    (stylus || (settings.fingerDrawing && !isPalm(event, 0)))
                if (canDraw) {
                    mode = Mode.DRAW
                    drawPointerId = event.getPointerId(0)
                    val (cx, cy) = viewport.toCanvas(event.x, event.y)
                    engine.pointerDown(cx, cy, pressureOf(event, 0), event.eventTime, toolOverride(event, 0))
                    if (!stylus) postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
                } else {
                    mode = Mode.NAVIGATE
                    storePrev(event)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                maxPointers = max(maxPointers, event.pointerCount)
                val drawingWithStylus = mode == Mode.DRAW && event.findPointerIndex(drawPointerId).let { it >= 0 && isStylus(event, it) }
                if (drawingWithStylus) return true // extra fingers during a stylus stroke are palms
                if (mode == Mode.DRAW || mode == Mode.PICK) {
                    removeCallbacks(longPress)
                    engine.pointerCancel()
                }
                mode = Mode.NAVIGATE
                storePrev(event)
            }
            MotionEvent.ACTION_MOVE -> {
                travelled = max(travelled, hypot(event.x - downX, event.y - downY))
                when (mode) {
                    Mode.DRAW -> {
                        val idx = event.findPointerIndex(drawPointerId)
                        if (idx < 0) return true
                        if (travelled > touchSlop) removeCallbacks(longPress)
                        for (h in 0 until event.historySize) {
                            val (cx, cy) = viewport.toCanvas(event.getHistoricalX(idx, h), event.getHistoricalY(idx, h))
                            engine.pointerMove(cx, cy, pressureOf(event, idx, h), event.getHistoricalEventTime(h))
                        }
                        val (cx, cy) = viewport.toCanvas(event.getX(idx), event.getY(idx))
                        engine.pointerMove(cx, cy, pressureOf(event, idx), event.eventTime)
                        cursorX = event.getX(idx); cursorY = event.getY(idx)
                    }
                    Mode.PICK -> pick(event.x, event.y, final = false)
                    Mode.NAVIGATE -> {
                        if (event.pointerCount >= 2 && prevCount >= 2) {
                            viewport.pinch(prev[0], prev[1], prev[2], prev[3], event.getX(0), event.getY(0), event.getX(1), event.getY(1))
                        } else if (event.pointerCount == 1 && prevCount >= 1 && maxPointers == 1) {
                            viewport.pan(event.getX(0) - prev[0], event.getY(0) - prev[1])
                        }
                        storePrev(event)
                        invalidate()
                    }
                    Mode.NONE -> Unit
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val upId = event.getPointerId(event.actionIndex)
                if (mode == Mode.DRAW && upId == drawPointerId) {
                    if (wasCanceled(event)) engine.pointerCancel() else engine.pointerUp()
                    mode = Mode.NONE
                } else if (mode == Mode.NAVIGATE) {
                    // Re-anchor to the remaining fingers to avoid a jump.
                    prevCount = 0
                    var j = 0
                    for (i in 0 until event.pointerCount) {
                        if (i == event.actionIndex || j >= 2) continue
                        prev[j * 2] = event.getX(i)
                        prev[j * 2 + 1] = event.getY(i)
                        j++
                    }
                    prevCount = j
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                when (mode) {
                    Mode.DRAW -> if (wasCanceled(event)) engine.pointerCancel() else engine.pointerUp()
                    Mode.PICK -> pick(event.x, event.y, final = true)
                    Mode.NAVIGATE -> {
                        val quick = event.eventTime - downTime < 300 && travelled < touchSlop * 3
                        if (quick && maxPointers == 2 && settings.twoFingerTapUndo) onUndoGesture?.invoke()
                        if (quick && maxPointers == 3 && settings.threeFingerTapRedo) onRedoGesture?.invoke()
                        viewport.snapRotation(width / 2f, height / 2f)
                        invalidate()
                    }
                    Mode.NONE -> Unit
                }
                mode = Mode.NONE
                if (!isStylus(event, 0)) cursorVisible = false
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                if (mode == Mode.DRAW) engine.pointerCancel()
                mode = Mode.NONE
            }
        }
        return true
    }
}
