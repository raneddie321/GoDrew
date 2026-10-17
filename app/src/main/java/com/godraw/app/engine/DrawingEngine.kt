package com.godraw.app.engine

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.godraw.core.brush.BrushPreset
import com.godraw.core.color.ColorUtils
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.geometry.Guides
import com.godraw.core.geometry.StrokePoint
import com.godraw.core.geometry.Symmetry
import com.godraw.core.history.UndoStack
import com.godraw.core.model.CellKey
import com.godraw.core.model.Layer
import com.godraw.core.model.ProjectDocument
import com.godraw.core.model.SymmetryMode
import com.godraw.core.raster.FloodFill
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Localized names the engine needs when it creates layers. */
interface EngineStrings {
    fun layerName(number: Int): String
    fun groupName(number: Int): String
    fun copyOf(name: String): String
    val mergedName: String
    val flattenedName: String
    val textLayerName: String
}

/** One-off notifications for the UI. */
sealed interface EngineEvent {
    data class Locked(val access: Access.Locked, val feature: Feature? = null) : EngineEvent
    data class TextRequested(val x: Float, val y: Float) : EngineEvent
    data class Message(val kind: MessageKind) : EngineEvent
    data class ColorPicked(val color: Int) : EngineEvent
}

enum class MessageKind {
    LAYER_LOCKED,
    LAYER_HIDDEN,
    NOT_A_RASTER_LAYER,
    NOTHING_SELECTED,
    CANNOT_DELETE_LAST_LAYER,
    MERGE_NOT_POSSIBLE,
    CLIPBOARD_EMPTY,
    OPERATION_FAILED,
}

data class EngineState(
    val doc: ProjectDocument,
    val tool: ToolType,
    val brushes: Map<ToolType, BrushPreset>,
    val color: Int,
    val stabilization: Float,
    val fill: FillSettings,
    val shape: ShapeSettings,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val hasSelection: Boolean = false,
    val floating: Boolean = false,
    val floatingDistort: Boolean = false,
    val editingMask: Boolean = false,
    val busy: Boolean = false,
    val progress: Float? = null,
    val revision: Long = 0,
    val effectPreviewActive: Boolean = false,
    val hasClipboard: Boolean = false,
    val playing: Boolean = false,
    val displayFrame: Int = 0,
) {
    val activeLayer: Layer? get() = doc.layer(doc.activeLayerId)
    val brush: BrushPreset get() = brushes[if (tool.isPainting) tool else ToolType.BRUSH] ?: defaultBrushFor(tool)
}

/** Input settings that come from the app settings. */
data class InputSettings(
    val pressureEnabled: Boolean = true,
    val pressureCurve: Float = 1f,
)

/**
 * The editing engine for one open project. Owns the document, cell bitmaps, history and tools.
 * All methods must be called on the main thread; heavy pixel work runs on background
 * dispatchers and is applied back on the main thread.
 */
class DrawingEngine(
    initialDoc: ProjectDocument,
    internal val store: CellStore,
    internal val snapshots: SnapshotManager,
    internal val gate: () -> FeatureGate,
    internal val strings: EngineStrings,
    internal val scope: CoroutineScope,
    stabilization: Float,
) {
    var doc: ProjectDocument = initialDoc
        private set

    internal val undo = UndoStack()
    val compositor = Compositor()
    private val stamps = StampFactory()
    val stroke = StrokeRenderer(initialDoc.width, initialDoc.height, stamps)
    val cellSource = CellSource { key -> store.get(key) }

    var input = InputSettings()

    private val _state = MutableStateFlow(
        EngineState(
            doc = initialDoc,
            tool = ToolType.BRUSH,
            brushes = mapOf(
                ToolType.BRUSH to defaultBrushFor(ToolType.BRUSH),
                ToolType.PENCIL to defaultBrushFor(ToolType.PENCIL),
                ToolType.PEN to defaultBrushFor(ToolType.PEN),
                ToolType.ERASER to defaultBrushFor(ToolType.ERASER),
            ),
            color = 0xFF1B2133.toInt(),
            stabilization = stabilization,
            fill = FillSettings(),
            shape = ShapeSettings(),
            displayFrame = initialDoc.activeFrameIndex,
        ),
    )
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    /** Called whenever the canvas needs to be redrawn. */
    var onInvalidate: (() -> Unit)? = null

    /** Set when the document or pixels changed since the last save. */
    var docDirty = false
        private set
    private var savedDoc: ProjectDocument = initialDoc

    var selection: Selection? = null
        private set
    var floating: FloatingContent? = null
        private set
    /** Selection outline being drawn (rect or lasso). */
    var pendingSelection: Path? = null
        private set
    var shapePreview: Path? = null
        private set
    var effectPreview: Bitmap? = null
        internal set
    private var clipboard: Pair<Bitmap, Rect>? = null

    /** Frame shown on the canvas: the active frame, or the playback position. */
    var displayFrame: Int = initialDoc.activeFrameIndex
        private set

    init {
        undo.setOnChangedListener { publish() }
    }

    // ------------------------------------------------------------------ state helpers

    internal fun publish() {
        _state.value = _state.value.copy(
            doc = doc,
            canUndo = undo.canUndo,
            canRedo = undo.canRedo,
            hasSelection = selection != null,
            floating = floating != null,
            floatingDistort = floating?.distort == true,
            effectPreviewActive = effectPreview != null,
            hasClipboard = clipboard != null,
            displayFrame = displayFrame,
        )
    }

    internal fun updateState(transform: (EngineState) -> EngineState) {
        _state.value = transform(_state.value)
    }

    internal fun emit(event: EngineEvent) {
        _events.tryEmit(event)
    }

    internal fun invalidate() {
        onInvalidate?.invoke()
    }

    internal fun bumpRevision() {
        docDirty = true
        _state.value = _state.value.copy(revision = _state.value.revision + 1)
    }

    /** Replaces the document without recording history (navigation, view settings). */
    internal fun setDocQuiet(newDoc: ProjectDocument) {
        doc = newDoc.copy(modifiedAt = System.currentTimeMillis())
        docDirty = true
        if (!_state.value.playing) displayFrame = doc.activeFrameIndex.coerceIn(0, doc.frames.lastIndex)
        updatePins()
        publish()
        invalidate()
    }

    /** Records a document change (and optional cell changes) as one undoable step. */
    internal fun commitDocChange(
        label: String,
        newDoc: ProjectDocument,
        before: Map<CellKey, CellSnapshot> = emptyMap(),
        after: Map<CellKey, CellSnapshot> = emptyMap(),
    ) {
        val old = doc
        undo.push(DocumentChangeCommand(this, label, old, newDoc, before, after))
        doc = newDoc.copy(modifiedAt = System.currentTimeMillis())
        docDirty = true
        displayFrame = doc.activeFrameIndex.coerceIn(0, doc.frames.lastIndex)
        validateSelectionTargets()
        updatePins()
        bumpRevision()
        publish()
        invalidate()
    }

    /** Applies a document change without recording it (used for live slider previews). */
    private var continuousStart: ProjectDocument? = null

    fun beginContinuousEdit() {
        if (continuousStart == null) continuousStart = doc
    }

    fun updateContinuous(transform: (ProjectDocument) -> ProjectDocument) {
        beginContinuousEdit()
        doc = transform(doc)
        docDirty = true
        publish()
        invalidate()
    }

    fun endContinuousEdit(label: String) {
        val start = continuousStart ?: return
        continuousStart = null
        if (start != doc) {
            undo.push(DocumentChangeCommand(this, label, start, doc))
            bumpRevision()
        }
        publish()
    }

    internal fun restoreState(newDoc: ProjectDocument, cells: Map<CellKey, CellSnapshot>) {
        doc = newDoc
        cells.forEach { (key, snapshot) -> snapshots.restore(key, snapshot) }
        displayFrame = doc.activeFrameIndex.coerceIn(0, doc.frames.lastIndex)
        validateSelectionTargets()
        updatePins()
        bumpRevision()
        publish()
        invalidate()
    }

    internal fun writeRegion(key: CellKey, rect: Rect, pixels: Bitmap) {
        val target = if (key.isMask) maskCell(key) else store.getOrCreate(key)
        PixelChangeCommand.paste(target, rect, pixels)
        store.markDirty(key)
        bumpRevision()
        invalidate()
    }

    private fun validateSelectionTargets() {
        if (doc.layer(doc.activeLayerId)?.isGroup != false) {
            val fallback = doc.layers.lastOrNull { !it.isGroup }
            if (fallback != null) doc = doc.copy(activeLayerId = fallback.id)
        }
        if (_state.value.editingMask && doc.layer(doc.activeLayerId)?.hasMask != true) {
            updateState { it.copy(editingMask = false) }
        }
    }

    /** Keeps the current frame (and onion skin neighbours) in memory. */
    internal fun updatePins() {
        val frames = doc.frames
        if (frames.isEmpty()) return
        val idx = displayFrame.coerceIn(0, frames.lastIndex)
        val range = (idx - doc.onionSkin.framesBefore).coerceAtLeast(0)..(idx + doc.onionSkin.framesAfter).coerceAtMost(frames.lastIndex)
        val keys = HashSet<CellKey>()
        for (layer in doc.layers) {
            if (layer.isGroup) continue
            for (f in range) {
                keys += CellKey.forLayer(layer, frames[f].id)
                if (layer.hasMask) keys += CellKey.forLayer(layer, frames[f].id, isMask = true)
            }
        }
        store.pinned = keys
    }

    val markSavedDoc: (ProjectDocument) -> Unit = { saved ->
        savedDoc = saved
        if (doc == saved && !store.hasUnsavedChanges) docDirty = false
    }

    val hasUnsavedChanges: Boolean get() = docDirty || store.hasUnsavedChanges || doc != savedDoc

    // ------------------------------------------------------------------ tools & settings

    fun setTool(tool: ToolType) {
        val feature = tool.requiredFeature
        if (feature != null) {
            val access = gate().check(feature)
            if (access is Access.Locked) {
                emit(EngineEvent.Locked(access, feature))
                return
            }
        }
        if (_state.value.tool == ToolType.TRANSFORM && tool != ToolType.TRANSFORM && floating != null) commitFloating()
        cancelInteraction()
        updateState { it.copy(tool = tool) }
        invalidate()
    }

    fun setColor(color: Int) {
        updateState { it.copy(color = color) }
    }

    fun setBrush(tool: ToolType, brush: BrushPreset) {
        val key = if (tool.isPainting) tool else ToolType.BRUSH
        updateState { it.copy(brushes = it.brushes + (key to brush)) }
    }

    fun setStabilization(value: Float) = updateState { it.copy(stabilization = value.coerceIn(0f, 1f)) }
    fun setFillSettings(fill: FillSettings) = updateState { it.copy(fill = fill) }
    fun setShapeSettings(shape: ShapeSettings) = updateState { it.copy(shape = shape) }

    fun setEditingMask(editing: Boolean) {
        val layer = _state.value.activeLayer
        if (editing && layer?.hasMask != true) return
        updateState { it.copy(editingMask = editing) }
        invalidate()
    }

    // ------------------------------------------------------------------ frames shown

    fun setDisplayFrame(index: Int) {
        displayFrame = index.coerceIn(0, doc.frames.lastIndex)
        updatePins()
        publish()
        invalidate()
    }

    fun setPlaying(playing: Boolean) {
        if (playing) cancelInteraction()
        updateState { it.copy(playing = playing) }
        if (!playing) setDisplayFrame(doc.activeFrameIndex)
    }

    // ------------------------------------------------------------------ geometry helpers

    private val tmpMatrix = Matrix()

    /** Matrix mapping layer space to canvas space for the active layer at the shown frame. */
    fun activeLayerMatrix(): Matrix? {
        val layer = _state.value.activeLayer ?: return null
        if (!layer.animation.isAnimated && layer.parentId?.let { doc.layer(it)?.animation?.isAnimated } != true) return null
        val m = Matrix()
        layer.parentId?.let { pid ->
            doc.layer(pid)?.takeIf { it.animation.isAnimated }?.let { g ->
                m.set(compositor.layerMatrix(com.godraw.core.animation.KeyframeSampler.sample(g.animation, displayFrame.toFloat()), doc))
            }
        }
        if (layer.animation.isAnimated) {
            m.preConcat(compositor.layerMatrix(com.godraw.core.animation.KeyframeSampler.sample(layer.animation, displayFrame.toFloat()), doc, tmpMatrix))
        }
        return m
    }

    private fun toLayerSpace(x: Float, y: Float): Pair<Float, Float> {
        val m = activeLayerMatrix() ?: return x to y
        val inv = Matrix()
        if (!m.invert(inv)) return x to y
        val p = floatArrayOf(x, y)
        inv.mapPoints(p)
        return p[0] to p[1]
    }

    private fun selectionInLayerSpace(): Path? {
        val sel = selection ?: return null
        val m = activeLayerMatrix() ?: return sel.path
        val inv = Matrix()
        if (!m.invert(inv)) return sel.path
        return Path(sel.path).apply { transform(inv) }
    }

    internal fun activeKey(frameIndex: Int = doc.activeFrameIndex): CellKey? {
        val layer = _state.value.activeLayer ?: return null
        if (layer.isGroup) return null
        val frame = doc.frames.getOrNull(frameIndex) ?: return null
        return CellKey.forLayer(layer, frame.id, isMask = _state.value.editingMask)
    }

    /** Mask cells default to fully visible (white). */
    internal fun maskCell(key: CellKey): Bitmap {
        store.get(key)?.let { return it }
        val bmp = Bitmap.createBitmap(doc.width, doc.height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(0xFFFFFFFF.toInt())
        store.set(key, bmp)
        return bmp
    }

    private fun editableCheck(): Boolean {
        val layer = _state.value.activeLayer
        when {
            layer == null || layer.isGroup -> emit(EngineEvent.Message(MessageKind.NOT_A_RASTER_LAYER))
            layer.locked -> emit(EngineEvent.Message(MessageKind.LAYER_LOCKED))
            !layer.visible -> emit(EngineEvent.Message(MessageKind.LAYER_HIDDEN))
            else -> return true
        }
        return false
    }

    private fun effectiveSymmetry(): Symmetry? {
        val g = doc.guides
        if (g.symmetry == SymmetryMode.NONE) return null
        if (g.symmetry == SymmetryMode.RADIAL && !gate().check(Feature.RADIAL_SYMMETRY).isGranted) return null
        return Symmetry(g.symmetry, doc.width / 2f, doc.height / 2f, g.radialSegments)
    }

    private fun shapeBrush(): BrushPreset = _state.value.brushes[ToolType.BRUSH] ?: defaultBrushFor(ToolType.BRUSH)

    // ------------------------------------------------------------------ pointer input

    private var activeTool: ToolType? = null
    private var downX = 0f
    private var downY = 0f
    private var strokeStartX = 0f
    private var strokeStartY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var transformHandle = TransformHandle.NONE
    private var transformStartMatrix = Matrix()
    private var transformStartCorners = FloatArray(8)
    private var animateStart: ProjectDocument? = null

    fun pointerDown(x: Float, y: Float, rawPressure: Float, timeMs: Long, toolOverride: ToolType? = null) {
        val st = _state.value
        if (st.busy || st.playing) return
        val tool = toolOverride ?: st.tool
        activeTool = tool
        downX = x; downY = y; lastX = x; lastY = y
        val pressure = mapPressure(rawPressure)
        when {
            tool.isPainting -> beginPaint(tool, x, y, pressure, timeMs)
            tool.isShape -> if (editableCheck()) { strokeStartX = x; strokeStartY = y } else activeTool = null
            tool == ToolType.EYEDROPPER -> pickColor(x, y, final = false)
            tool == ToolType.SELECT_RECT || tool == ToolType.SELECT_LASSO -> {
                pendingSelection = Path().apply { moveTo(x, y) }
            }
            tool == ToolType.TRANSFORM -> beginTransform(x, y)
            tool == ToolType.ANIMATE -> beginAnimate(x, y)
            else -> Unit
        }
    }

    fun pointerMove(x: Float, y: Float, rawPressure: Float, timeMs: Long) {
        val tool = activeTool ?: return
        val pressure = mapPressure(rawPressure)
        when {
            tool.isPainting -> continuePaint(x, y, pressure, timeMs)
            tool.isShape -> updateShapePreview(tool, x, y)
            tool == ToolType.EYEDROPPER -> pickColor(x, y, final = false)
            tool == ToolType.SELECT_RECT -> {
                pendingSelection = Path().apply {
                    addRect(min(downX, x), min(downY, y), max(downX, x), max(downY, y), Path.Direction.CW)
                }
                invalidate()
            }
            tool == ToolType.SELECT_LASSO -> {
                pendingSelection?.lineTo(x, y)
                invalidate()
            }
            tool == ToolType.TRANSFORM -> moveTransform(x, y)
            tool == ToolType.ANIMATE -> moveAnimate(x, y)
            else -> Unit
        }
        lastX = x; lastY = y
    }

    fun pointerUp() {
        val tool = activeTool ?: return
        activeTool = null
        when {
            tool.isPainting -> endPaint()
            tool.isShape -> commitShape()
            tool == ToolType.EYEDROPPER -> pickColor(lastX, lastY, final = true)
            tool == ToolType.FILL -> fillAt(lastX, lastY)
            tool == ToolType.TEXT -> {
                if (editableCheck()) emit(EngineEvent.TextRequested(lastX, lastY))
            }
            tool == ToolType.SELECT_RECT || tool == ToolType.SELECT_LASSO -> finishSelection(tool)
            tool == ToolType.TRANSFORM -> transformHandle = TransformHandle.NONE
            tool == ToolType.ANIMATE -> endAnimate()
            else -> Unit
        }
    }

    /** Aborts the current gesture without changing the artwork (palm rejection, multi-touch). */
    fun pointerCancel() {
        val tool = activeTool ?: return
        activeTool = null
        when {
            tool.isPainting -> stroke.clear()
            tool.isShape -> shapePreview = null
            tool.isSelection -> pendingSelection = null
            tool == ToolType.ANIMATE -> animateStart?.let { doc = it; animateStart = null; publish() }
            else -> Unit
        }
        invalidate()
    }

    private fun cancelInteraction() {
        if (activeTool != null) pointerCancel()
    }

    private fun mapPressure(raw: Float): Float {
        if (!input.pressureEnabled) return 1f
        val p = raw.coerceIn(0f, 1f)
        return if (input.pressureCurve == 1f) p else Math.pow(p.toDouble(), input.pressureCurve.toDouble()).toFloat()
    }

    // ------------------------------------------------------------------ painting

    private fun constrain(x: Float, y: Float): Pair<Float, Float> {
        val g = doc.guides
        if (g.rulerEnabled && (g.rulerX1 != g.rulerX2 || g.rulerY1 != g.rulerY2)) {
            return Guides.projectOntoLine(x, y, g.rulerX1, g.rulerY1, g.rulerX2, g.rulerY2)
        }
        if (g.perspectiveEnabled && g.vanishingPoints.size >= 2 && gate().check(Feature.PERSPECTIVE_GUIDES).isGranted) {
            val vps = g.vanishingPoints.chunked(2).filter { it.size == 2 }.map { it[0] to it[1] }
            return Guides.snapToPerspective(strokeStartX, strokeStartY, x, y, vps)
        }
        return x to y
    }

    private fun beginPaint(tool: ToolType, x: Float, y: Float, pressure: Float, timeMs: Long) {
        if (!editableCheck()) {
            activeTool = null
            return
        }
        val st = _state.value
        val brush = st.brushes[tool] ?: defaultBrushFor(tool)
        val eraser = tool == ToolType.ERASER || (st.editingMask && ColorUtils.luminance(st.color) < 0.5f)
        val color = if (st.editingMask) 0xFFFFFFFF.toInt() else st.color
        val (lx, ly) = toLayerSpace(x, y)
        strokeStartX = lx; strokeStartY = ly
        stroke.begin(brush, color, eraser, st.stabilization, effectiveSymmetry())
        val (cx, cy) = constrain(lx, ly)
        stroke.add(StrokePoint(cx, cy, pressure, timeMs))
        invalidate()
    }

    private fun continuePaint(x: Float, y: Float, pressure: Float, timeMs: Long) {
        if (!stroke.active) return
        val (lx, ly) = toLayerSpace(x, y)
        val (cx, cy) = constrain(lx, ly)
        if (stroke.add(StrokePoint(cx, cy, pressure, timeMs)) != null) invalidate()
    }

    private fun endPaint() {
        if (!stroke.active) return
        stroke.finishInput()
        val key = activeKey()
        val rect = stroke.integerBounds()
        if (key != null && rect != null) {
            val target = if (key.isMask) maskCell(key) else store.getOrCreate(key)
            val before = PixelChangeCommand.crop(target, rect)
            stroke.drawStroke(Canvas(target), selectionInLayerSpace())
            val after = PixelChangeCommand.crop(target, rect)
            store.markDirty(key)
            undo.push(PixelChangeCommand(this, "stroke", key, rect, before, after))
            bumpRevision()
        }
        stroke.clear()
        invalidate()
    }

    // ------------------------------------------------------------------ shapes

    val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }

    private fun updateShapePaint() {
        val st = _state.value
        val b = shapeBrush()
        shapePaint.color = st.color
        shapePaint.alpha = (ColorUtils.alpha(st.color) * b.opacity).roundToInt().coerceIn(0, 255)
        shapePaint.strokeWidth = b.size
        shapePaint.style = if (st.shape.filled && st.tool != ToolType.LINE) Paint.Style.FILL else Paint.Style.STROKE
    }

    private fun updateShapePreview(tool: ToolType, x: Float, y: Float) {
        val (sx, sy) = toLayerSpace(strokeStartX, strokeStartY)
        val (ex, ey) = toLayerSpace(x, y)
        shapePreview = buildShape(tool, sx, sy, ex, ey)
        updateShapePaint()
        invalidate()
    }

    fun buildShape(tool: ToolType, x0: Float, y0: Float, x1: Float, y1: Float): Path {
        val path = Path()
        val l = min(x0, x1)
        val t = min(y0, y1)
        val r = max(x0, x1)
        val b = max(y0, y1)
        when (tool) {
            ToolType.LINE -> {
                var ex = x1
                var ey = y1
                val angle = atan2(y1 - y0, x1 - x0)
                val snapped = Guides.snapAngle(angle, 15f)
                if (abs(snapped - angle) < Math.toRadians(3.0).toFloat()) {
                    val len = hypot(x1 - x0, y1 - y0)
                    ex = x0 + cos(snapped) * len
                    ey = y0 + sin(snapped) * len
                }
                path.moveTo(x0, y0)
                path.lineTo(ex, ey)
            }
            ToolType.RECTANGLE -> path.addRect(l, t, r, b, Path.Direction.CW)
            ToolType.ELLIPSE -> path.addOval(RectF(l, t, r, b), Path.Direction.CW)
            ToolType.POLYGON -> {
                val shape = _state.value.shape
                val n = shape.sides.coerceIn(3, 16)
                val cx = (l + r) / 2
                val cy = (t + b) / 2
                val rx = (r - l) / 2
                val ry = (b - t) / 2
                val points = if (shape.star) n * 2 else n
                for (i in 0 until points) {
                    val a = -Math.PI / 2 + 2 * Math.PI * i / points
                    val k = if (shape.star && i % 2 == 1) 0.45f else 1f
                    val px = cx + (rx * k * cos(a)).toFloat()
                    val py = cy + (ry * k * sin(a)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
            }
            else -> Unit
        }
        return path
    }

    private fun commitShape() {
        val path = shapePreview ?: return
        shapePreview = null
        val key = activeKey() ?: return
        updateShapePaint()
        val bounds = RectF()
        path.computeBounds(bounds, true)
        val pad = shapePaint.strokeWidth + 2f
        bounds.inset(-pad, -pad)
        val rect = Rect(
            bounds.left.toInt().coerceIn(0, doc.width), bounds.top.toInt().coerceIn(0, doc.height),
            (bounds.right + 1).toInt().coerceIn(0, doc.width), (bounds.bottom + 1).toInt().coerceIn(0, doc.height),
        )
        if (rect.isEmpty) {
            invalidate()
            return
        }
        val target = if (key.isMask) maskCell(key) else store.getOrCreate(key)
        val before = PixelChangeCommand.crop(target, rect)
        val canvas = Canvas(target)
        selectionInLayerSpace()?.let { canvas.clipPath(it) }
        if (key.isMask) {
            val hide = ColorUtils.luminance(_state.value.color) < 0.5f
            val p = Paint(shapePaint).apply {
                color = 0xFFFFFFFF.toInt()
                if (hide) blendMode = BlendMode.DST_OUT
            }
            canvas.drawPath(path, p)
        } else {
            canvas.drawPath(path, shapePaint)
        }
        store.markDirty(key)
        undo.push(PixelChangeCommand(this, "shape", key, rect, before, PixelChangeCommand.crop(target, rect)))
        bumpRevision()
        invalidate()
    }

    // ------------------------------------------------------------------ eyedropper & fill

    private fun pickColor(x: Float, y: Float, final: Boolean) {
        val color = sampleColor(x, y) ?: return
        updateState { it.copy(color = color) }
        if (final) emit(EngineEvent.ColorPicked(color))
    }

    /** Samples the visible color at a canvas position (null outside the canvas or transparent). */
    fun sampleColor(x: Float, y: Float): Int? {
        if (x < 0 || y < 0 || x >= doc.width || y >= doc.height) return null
        val bmp = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.translate(-x.toInt().toFloat(), -y.toInt().toFloat())
        compositor.drawFrame(canvas, doc, displayFrame, cellSource)
        val c = bmp.getPixel(0, 0)
        bmp.recycle()
        return if (ColorUtils.alpha(c) == 0) null else ColorUtils.withAlpha(c, 255)
    }

    private var fillJob: Job? = null

    fun fillAt(x: Float, y: Float) {
        if (!editableCheck()) return
        val key = activeKey() ?: return
        val st = _state.value
        val (lx, ly) = toLayerSpace(x, y)
        val px = lx.toInt()
        val py = ly.toInt()
        if (px !in 0 until doc.width || py !in 0 until doc.height) return
        val w = doc.width
        val h = doc.height
        val target = if (key.isMask) maskCell(key) else store.getOrCreate(key)
        val targetPixels = IntArray(w * h).also { target.getPixels(it, 0, w, 0, 0, w, h) }
        val reference = if (st.fill.sampleAllLayers && !key.isMask) {
            val ref = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            compositor.drawFrame(Canvas(ref), doc, doc.activeFrameIndex, cellSource)
            IntArray(w * h).also { ref.getPixels(it, 0, w, 0, 0, w, h); ref.recycle() }
        } else {
            targetPixels.copyOf()
        }
        val selMask = selectionInLayerSpace()?.let { Selection(it).mask(w, h) }
        val fillColor = if (key.isMask) {
            if (ColorUtils.luminance(st.color) < 0.5f) 0 else 0xFFFFFFFF.toInt()
        } else {
            ColorUtils.withAlpha(st.color, (ColorUtils.alpha(st.color) * shapeBrush().opacity).roundToInt())
        }
        val replace = key.isMask
        updateState { it.copy(busy = true) }
        fillJob = scope.launch {
            val result = withContext(Dispatchers.Default) {
                val region = FloodFill.regionMask(reference, w, h, px, py, st.fill.tolerance) ?: return@withContext null
                if (selMask != null) for (i in region.indices) if (region[i] && (selMask[i].toInt() and 0xFF) < 128) region[i] = false
                if (st.fill.expand > 0) FloodFill.dilate(region, w, h, st.fill.expand)
                if (selMask != null) for (i in region.indices) if (region[i] && (selMask[i].toInt() and 0xFF) < 128) region[i] = false
                var minX = w; var minY = h; var maxX = -1; var maxY = -1
                for (i in region.indices) {
                    if (!region[i]) continue
                    targetPixels[i] = if (replace) fillColor else FloodFill.blendOver(targetPixels[i], fillColor)
                    val xx = i % w
                    val yy = i / w
                    if (xx < minX) minX = xx
                    if (xx > maxX) maxX = xx
                    if (yy < minY) minY = yy
                    if (yy > maxY) maxY = yy
                }
                if (maxX < 0) null else Rect(minX, minY, maxX + 1, maxY + 1)
            }
            if (result != null) {
                val live = if (key.isMask) maskCell(key) else store.getOrCreate(key)
                val before = PixelChangeCommand.crop(live, result)
                live.setPixels(targetPixels, result.top * w + result.left, w, result.left, result.top, result.width(), result.height())
                store.markDirty(key)
                undo.push(PixelChangeCommand(this@DrawingEngine, "fill", key, result, before, PixelChangeCommand.crop(live, result)))
                bumpRevision()
                invalidate()
            }
            updateState { it.copy(busy = false) }
        }
    }

    // ------------------------------------------------------------------ text

    fun placeText(spec: TextSpec, x: Float, y: Float) {
        if (!editableCheck()) return
        val key = activeKey() ?: return
        if (spec.text.isBlank()) return
        val base = when (spec.font) {
            TextFont.SANS -> Typeface.SANS_SERIF
            TextFont.SERIF -> Typeface.SERIF
            TextFont.MONO -> Typeface.MONOSPACE
            TextFont.HANDWRITING -> Typeface.create("cursive", Typeface.NORMAL)
        }
        val style = when {
            spec.bold && spec.italic -> Typeface.BOLD_ITALIC
            spec.bold -> Typeface.BOLD
            spec.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(base, style)
            textSize = spec.sizePx.coerceIn(4f, 2000f)
            color = _state.value.color
        }
        val lines = spec.text.lines()
        val lineHeight = paint.fontSpacing
        val textWidth = lines.maxOf { paint.measureText(it) }.coerceAtLeast(1f)
        val bw = (textWidth + 8).toInt().coerceAtMost(8192)
        val bh = (lineHeight * lines.size + 8).toInt().coerceAtMost(8192)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        lines.forEachIndexed { i, line -> c.drawText(line, 4f, 4f - paint.ascent() + i * lineHeight, paint) }
        val (lx, ly) = toLayerSpace(x, y)
        val target = if (key.isMask) maskCell(key) else store.getOrCreate(key)
        floating = FloatingContent(
            key = key,
            bitmap = bmp,
            sourceRect = Rect(),
            originalCell = target.copy(Bitmap.Config.ARGB_8888, false),
            matrix = Matrix().apply { setTranslate(lx, ly - bh / 2f) },
        )
        updateState { it.copy(tool = ToolType.TRANSFORM) }
        publish()
        invalidate()
    }

    // ------------------------------------------------------------------ selection

    private fun finishSelection(tool: ToolType) {
        val path = pendingSelection ?: return
        pendingSelection = null
        if (tool == ToolType.SELECT_LASSO) path.close()
        val sel = Selection(path)
        selection = if (sel.bounds.width() < 3f && sel.bounds.height() < 3f) null else sel
        publish()
        invalidate()
    }

    fun selectAll() {
        selection = Selection.all(doc.width, doc.height)
        publish()
        invalidate()
    }

    fun deselect() {
        selection = null
        publish()
        invalidate()
    }

    fun invertSelection() {
        val sel = selection ?: return selectAll()
        val all = Path().apply { addRect(0f, 0f, doc.width.toFloat(), doc.height.toFloat(), Path.Direction.CW) }
        val inverted = Path()
        inverted.op(all, sel.path, Path.Op.DIFFERENCE)
        selection = Selection(inverted)
        publish()
        invalidate()
    }

    /** Clears the selected pixels (or the whole layer without a selection). */
    fun clearSelectionPixels() {
        if (!editableCheck()) return
        val key = activeKey() ?: return
        val target = if (key.isMask) maskCell(key) else store.getOrCreate(key)
        val rect = selection?.integerBounds(doc.width, doc.height) ?: Rect(0, 0, doc.width, doc.height)
        if (rect.isEmpty) return
        val before = PixelChangeCommand.crop(target, rect)
        val canvas = Canvas(target)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.CLEAR }
        val path = selectionInLayerSpace()
        if (path != null) canvas.drawPath(path, paint) else canvas.drawRect(rect, paint)
        store.markDirty(key)
        undo.push(PixelChangeCommand(this, "clear", key, rect, before, PixelChangeCommand.crop(target, rect)))
        bumpRevision()
        invalidate()
    }

    fun fillSelection() {
        if (!editableCheck()) return
        val sel = selection ?: run { emit(EngineEvent.Message(MessageKind.NOTHING_SELECTED)); return }
        val key = activeKey() ?: return
        val target = if (key.isMask) maskCell(key) else store.getOrCreate(key)
        val rect = sel.integerBounds(doc.width, doc.height)
        if (rect.isEmpty) return
        val before = PixelChangeCommand.crop(target, rect)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = _state.value.color }
        Canvas(target).drawPath(selectionInLayerSpace() ?: sel.path, paint)
        store.markDirty(key)
        undo.push(PixelChangeCommand(this, "fill_selection", key, rect, before, PixelChangeCommand.crop(target, rect)))
        bumpRevision()
        invalidate()
    }

    fun copySelection(cut: Boolean = false) {
        val key = activeKey() ?: return
        val cell = store.get(key) ?: run { emit(EngineEvent.Message(MessageKind.NOTHING_SELECTED)); return }
        val rect = selection?.integerBounds(doc.width, doc.height) ?: Rect(0, 0, doc.width, doc.height)
        if (rect.isEmpty) return
        val copy = PixelChangeCommand.crop(cell, rect)
        selectionInLayerSpace()?.let { path ->
            // Keep only pixels inside the outline.
            val maskPath = Path(path).apply { offset(-rect.left.toFloat(), -rect.top.toFloat()) }
            val masked = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            val c = Canvas(masked)
            c.drawPath(maskPath, Paint(Paint.ANTI_ALIAS_FLAG))
            c.drawBitmap(copy, 0f, 0f, Paint().apply { blendMode = BlendMode.SRC_IN })
            clipboard = masked to rect
        } ?: run { clipboard = copy to rect }
        if (cut) clearSelectionPixels()
        publish()
    }

    fun paste() {
        val (bmp, rect) = clipboard ?: run { emit(EngineEvent.Message(MessageKind.CLIPBOARD_EMPTY)); return }
        if (!editableCheck()) return
        val key = activeKey() ?: return
        if (floating != null) commitFloating()
        val target = if (key.isMask) maskCell(key) else store.getOrCreate(key)
        floating = FloatingContent(
            key = key,
            bitmap = bmp.copy(Bitmap.Config.ARGB_8888, false),
            sourceRect = Rect(),
            originalCell = target.copy(Bitmap.Config.ARGB_8888, false),
            matrix = Matrix().apply { setTranslate(rect.left.toFloat(), rect.top.toFloat()) },
        )
        updateState { it.copy(tool = ToolType.TRANSFORM) }
        publish()
        invalidate()
    }

    // ------------------------------------------------------------------ transform

    enum class TransformHandle { NONE, MOVE, CORNER_0, CORNER_1, CORNER_2, CORNER_3, ROTATE }

    /** Screen-independent hit radius in canvas pixels; set by the view from the zoom level. */
    var handleRadius = 40f

    /** Lifts the selection (or whole layer) into a floating, transformable bitmap. */
    fun liftForTransform(): Boolean {
        if (floating != null) return true
        if (!editableCheck()) return false
        val key = activeKey() ?: return false
        val target = if (key.isMask) maskCell(key) else store.get(key) ?: return false
        val original = target.copy(Bitmap.Config.ARGB_8888, false)
        val selPath = selectionInLayerSpace()
        val rect = selPath?.let { Selection(it).integerBounds(doc.width, doc.height) } ?: contentBounds(target)
        if (rect == null || rect.isEmpty) return false
        val lifted = PixelChangeCommand.crop(target, rect)
        val canvas = Canvas(target)
        if (selPath != null) {
            val maskPath = Path(selPath).apply { offset(-rect.left.toFloat(), -rect.top.toFloat()) }
            val masked = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
            val mc = Canvas(masked)
            mc.drawPath(maskPath, Paint(Paint.ANTI_ALIAS_FLAG))
            mc.drawBitmap(lifted, 0f, 0f, Paint().apply { blendMode = BlendMode.SRC_IN })
            canvas.drawPath(selPath, Paint(Paint.ANTI_ALIAS_FLAG).apply { blendMode = BlendMode.CLEAR })
            floating = FloatingContent(key, masked, rect, original, Matrix().apply { setTranslate(rect.left.toFloat(), rect.top.toFloat()) })
        } else {
            canvas.drawRect(rect, Paint().apply { blendMode = BlendMode.CLEAR })
            floating = FloatingContent(key, lifted, rect, original, Matrix().apply { setTranslate(rect.left.toFloat(), rect.top.toFloat()) })
        }
        selection = null
        publish()
        invalidate()
        return true
    }

    /** Bounding box of non-transparent pixels, or null for an empty bitmap. */
    private fun contentBounds(bmp: Bitmap): Rect? {
        val w = bmp.width
        val h = bmp.height
        val row = IntArray(w)
        var minX = w; var minY = h; var maxX = -1; var maxY = -1
        for (y in 0 until h) {
            bmp.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) != 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    maxY = y
                }
            }
        }
        return if (maxX < 0) null else Rect(minX, minY, maxX + 1, maxY + 1)
    }

    private fun beginTransform(x: Float, y: Float) {
        if (floating == null && !liftForTransform()) {
            activeTool = null
            return
        }
        val f = floating ?: return
        val (lx, ly) = toLayerSpace(x, y)
        downX = lx; downY = ly
        transformStartMatrix = Matrix(f.matrix)
        transformStartCorners = f.corners()
        transformHandle = hitTestHandle(f, lx, ly)
    }

    private fun hitTestHandle(f: FloatingContent, x: Float, y: Float): TransformHandle {
        val c = f.corners()
        val r = handleRadius
        for (i in 0 until 4) {
            if (hypot(c[i * 2] - x, c[i * 2 + 1] - y) <= r) return TransformHandle.entries[TransformHandle.CORNER_0.ordinal + i]
        }
        val (rx, ry) = rotationHandle(f)
        if (hypot(rx - x, ry - y) <= r) return TransformHandle.ROTATE
        return TransformHandle.MOVE
    }

    /** Position of the rotation handle: above the middle of the top edge. */
    fun rotationHandle(f: FloatingContent): Pair<Float, Float> {
        val c = f.corners()
        val mx = (c[0] + c[2]) / 2
        val my = (c[1] + c[3]) / 2
        val (cx, cy) = f.center()
        val dx = mx - cx
        val dy = my - cy
        val len = hypot(dx, dy).coerceAtLeast(1f)
        val off = handleRadius * 1.6f
        return (mx + dx / len * off) to (my + dy / len * off)
    }

    private fun moveTransform(x: Float, y: Float) {
        val f = floating ?: return
        val (lx, ly) = toLayerSpace(x, y)
        val m = Matrix(transformStartMatrix)
        when (val h = transformHandle) {
            TransformHandle.MOVE -> m.postTranslate(lx - downX, ly - downY)
            TransformHandle.ROTATE -> {
                val (cx, cy) = centerOf(transformStartCorners)
                val a0 = atan2(downY - cy, downX - cx)
                val a1 = atan2(ly - cy, lx - cx)
                var deg = Math.toDegrees((a1 - a0).toDouble()).toFloat()
                val snapped = (deg / 15f).roundToInt() * 15f
                if (abs(deg - snapped) < 2f) deg = snapped
                m.postRotate(deg, cx, cy)
            }
            TransformHandle.CORNER_0, TransformHandle.CORNER_1, TransformHandle.CORNER_2, TransformHandle.CORNER_3 -> {
                val i = h.ordinal - TransformHandle.CORNER_0.ordinal
                if (f.distort) {
                    val corners = transformStartCorners.copyOf()
                    corners[i * 2] += lx - downX
                    corners[i * 2 + 1] += ly - downY
                    f.setCorners(corners)
                    invalidate()
                    return
                }
                // Uniform scale about the opposite corner.
                val o = (i + 2) % 4
                val ox = transformStartCorners[o * 2]
                val oy = transformStartCorners[o * 2 + 1]
                val d0 = hypot(downX - ox, downY - oy).coerceAtLeast(1f)
                val d1 = hypot(lx - ox, ly - oy)
                val s = (d1 / d0).coerceIn(0.02f, 50f)
                m.postScale(s, s, ox, oy)
            }
            TransformHandle.NONE -> return
        }
        f.matrix.set(m)
        invalidate()
    }

    private fun centerOf(c: FloatArray): Pair<Float, Float> =
        ((c[0] + c[2] + c[4] + c[6]) / 4f) to ((c[1] + c[3] + c[5] + c[7]) / 4f)

    fun setDistort(enabled: Boolean) {
        val f = floating ?: return
        if (enabled) {
            val access = gate().check(Feature.DISTORT_TRANSFORM)
            if (access is Access.Locked) {
                emit(EngineEvent.Locked(access, Feature.DISTORT_TRANSFORM))
                return
            }
        }
        f.distort = enabled
        publish()
    }

    fun flipFloating(horizontal: Boolean) {
        if (floating == null && !liftForTransform()) return
        val f = floating ?: return
        if (horizontal) f.flipHorizontal() else f.flipVertical()
        invalidate()
    }

    fun rotateFloating(degrees: Float) {
        if (floating == null && !liftForTransform()) return
        floating?.rotate(degrees)
        invalidate()
    }

    /** Drops the floating pixels onto the layer as one undoable step. */
    fun commitFloating() {
        val f = floating ?: return
        floating = null
        val target = if (f.key.isMask) maskCell(f.key) else store.getOrCreate(f.key)
        Canvas(target).drawBitmap(f.bitmap, f.matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val dest = f.boundsInCanvas()
        val rect = Rect(f.sourceRect)
        rect.union(Rect(dest.left.toInt() - 1, dest.top.toInt() - 1, dest.right.toInt() + 2, dest.bottom.toInt() + 2))
        if (rect.intersect(0, 0, doc.width, doc.height)) {
            val before = PixelChangeCommand.crop(f.originalCell, rect)
            val after = PixelChangeCommand.crop(target, rect)
            undo.push(PixelChangeCommand(this, "transform", f.key, rect, before, after))
        }
        store.markDirty(f.key)
        bumpRevision()
        publish()
        invalidate()
    }

    /** Puts lifted pixels back where they were. */
    fun cancelFloating() {
        val f = floating ?: return
        floating = null
        f.originalCell?.let { original ->
            val target = if (f.key.isMask) maskCell(f.key) else store.getOrCreate(f.key)
            PixelChangeCommand.paste(target, Rect(0, 0, original.width, original.height), original)
            store.markDirty(f.key)
        }
        publish()
        invalidate()
    }

    // ------------------------------------------------------------------ animate (auto-key)

    private var animateMode = TransformHandle.NONE
    private var animateStartTransform = com.godraw.core.animation.LayerTransform()

    private fun beginAnimate(x: Float, y: Float) {
        val layer = _state.value.activeLayer ?: run { activeTool = null; return }
        if (layer.locked) {
            emit(EngineEvent.Message(MessageKind.LAYER_LOCKED))
            activeTool = null
            return
        }
        animateStart = doc
        animateStartTransform = com.godraw.core.animation.KeyframeSampler.sample(layer.animation, doc.activeFrameIndex.toFloat())
        val cx = doc.width / 2f + animateStartTransform.x
        val cy = doc.height / 2f + animateStartTransform.y
        val dist = hypot(x - cx, y - cy)
        val radius = min(doc.width, doc.height) / 2f * animateStartTransform.scale
        animateMode = when {
            abs(dist - radius) < handleRadius -> TransformHandle.CORNER_0 // scale ring
            y < cy - radius - handleRadius * 0.5f && abs(x - cx) < handleRadius * 2 -> TransformHandle.ROTATE
            else -> TransformHandle.MOVE
        }
    }

    private fun moveAnimate(x: Float, y: Float) {
        val start = animateStart ?: return
        val layer = start.layer(start.activeLayerId) ?: return
        val t0 = animateStartTransform
        val frame = start.activeFrameIndex
        val cx = start.width / 2f + t0.x
        val cy = start.height / 2f + t0.y
        var anim = layer.animation
        fun key(p: com.godraw.core.model.TransformProperty, v: Float) {
            val existing = anim.track(p).firstOrNull { it.frame == frame }
            val kf = existing?.copy(value = v) ?: com.godraw.core.model.Keyframe(frame, v)
            anim = anim.withTrack(p, com.godraw.core.animation.KeyframeSampler.upsert(anim.track(p), kf))
        }
        when (animateMode) {
            TransformHandle.MOVE -> {
                key(com.godraw.core.model.TransformProperty.POSITION_X, t0.x + (x - downX))
                key(com.godraw.core.model.TransformProperty.POSITION_Y, t0.y + (y - downY))
            }
            TransformHandle.ROTATE -> {
                val a0 = atan2(downY - cy, downX - cx)
                val a1 = atan2(y - cy, x - cx)
                key(com.godraw.core.model.TransformProperty.ROTATION, t0.rotation + Math.toDegrees((a1 - a0).toDouble()).toFloat())
            }
            else -> {
                val d0 = hypot(downX - cx, downY - cy).coerceAtLeast(1f)
                val d1 = hypot(x - cx, y - cy)
                key(com.godraw.core.model.TransformProperty.SCALE, (t0.scale * d1 / d0).coerceIn(0.02f, 50f))
            }
        }
        doc = start.copy(layers = start.layers.map { if (it.id == layer.id) it.copy(animation = anim) else it })
        publish()
        invalidate()
    }

    private fun endAnimate() {
        val start = animateStart ?: return
        animateStart = null
        val changed = doc
        if (changed != start) {
            doc = start
            commitDocChange("keyframe", changed)
        }
    }

    // ------------------------------------------------------------------ history

    fun undo() {
        if (floating != null) {
            cancelFloating()
            return
        }
        if (activeTool != null) pointerCancel()
        undo.undo()
        invalidate()
    }

    fun redo() {
        if (floating != null) commitFloating()
        undo.redo()
        invalidate()
    }

    // ------------------------------------------------------------------ saving

    companion object {
        /** Maximum bitmap bytes copied per save round; larger change sets save in several rounds. */
        const val SAVE_BATCH_BYTES = 64L * 1024 * 1024
    }

    data class SaveSnapshot(
        val doc: ProjectDocument,
        val cells: Map<CellKey, Bitmap?>,
        val generations: Map<CellKey, Int>,
        val thumbnail: Bitmap?,
    )

    /** Captures everything needed to save in the background. */
    fun snapshotForSave(includeThumbnail: Boolean = true): SaveSnapshot {
        val cells = store.snapshotChanges(SAVE_BATCH_BYTES)
        val gens = store.generations(cells.keys)
        val thumb = if (includeThumbnail) renderThumbnail(512) else null
        return SaveSnapshot(doc, cells, gens, thumb)
    }

    fun onSaved(snapshot: SaveSnapshot) {
        store.markSaved(snapshot.cells.keys, snapshot.generations)
        markSavedDoc(snapshot.doc)
    }

    fun renderThumbnail(maxSize: Int, frameIndex: Int = doc.activeFrameIndex): Bitmap {
        val scale = min(1f, maxSize / max(doc.width, doc.height).toFloat())
        val w = max(1, (doc.width * scale).roundToInt())
        val h = max(1, (doc.height * scale).roundToInt())
        return compositor.renderBitmap(doc, frameIndex.coerceIn(0, doc.frames.lastIndex), cellSource, w, h)
    }

    /**
     * Copies a cell to a new key. Clean cells are copied as files (fast, no decoding); cells with
     * unsaved changes are copied in memory. Returns false when the source cell is empty.
     */
    internal fun copyCell(src: CellKey, dst: CellKey): Boolean {
        if (store.isLoaded(src) && store.isDirty(src) || !snapshots.cellFile(src).exists()) {
            val bmp = store.get(src) ?: return false
            store.set(dst, bmp.copy(Bitmap.Config.ARGB_8888, true))
            return true
        }
        if (store.isKnownEmpty(src)) return false
        val target = snapshots.cellFile(dst)
        return runCatching {
            snapshots.cellFile(src).copyTo(target, overwrite = true)
            store.forgetEmpty(dst)
            true
        }.getOrDefault(false)
    }

    fun release() {
        fillJob?.cancel()
        stroke.release()
        undo.clear()
        snapshots.clear()
    }
}
