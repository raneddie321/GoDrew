package com.godraw.app.engine

import android.app.Application
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.godraw.core.animation.EasingType
import com.godraw.core.animation.KeyframeSampler
import com.godraw.core.animation.TransitionPreset
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.entitlement.Tier
import com.godraw.core.model.CellKey
import com.godraw.core.model.GuideSettings
import com.godraw.core.model.LayerBlendMode
import com.godraw.core.model.ProjectDocument
import com.godraw.core.model.ProjectFactory
import com.godraw.core.model.ProjectType
import com.godraw.core.model.SymmetryMode
import com.godraw.core.model.TransformProperty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DrawingEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var tier = Tier.FREE
    private lateinit var engine: DrawingEngine
    private val events = mutableListOf<EngineEvent>()

    private val strings = object : EngineStrings {
        override fun layerName(number: Int) = "Layer $number"
        override fun groupName(number: Int) = "Group $number"
        override fun copyOf(name: String) = "$name copy"
        override val mergedName = "Merged"
        override val flattenedName = "Flattened"
        override val textLayerName = "Text"
    }

    private fun build(doc: ProjectDocument) {
        val store = CellStore(doc.width, doc.height, loader = { null }, spill = { _, _ -> true }, budgetBytes = 256L * 1024 * 1024)
        val snapshots = SnapshotManager(store, tmp.newFolder()) { key -> java.io.File(tmp.root, key.fileName) }
        val scope = CoroutineScope(Dispatchers.Unconfined)
        engine = DrawingEngine(doc, store, snapshots, { FeatureGate(tier) }, strings, scope, 0f)
        engine.input = InputSettings(pressureEnabled = false)
        events.clear()
        engine.events.onEach { events += it }.launchIn(scope)
    }

    @Before
    fun setUp() {
        tier = Tier.FREE
        build(ProjectFactory.create("Test", ProjectType.ANIMATION, 100, 80, Color.WHITE))
    }

    private fun activeCell() = engine.store.get(CellKey.forLayer(engine.doc.layer(engine.doc.activeLayerId)!!, engine.doc.frames[engine.doc.activeFrameIndex].id))

    private fun stroke(x0: Float, y0: Float, x1: Float, y1: Float, tool: ToolType = ToolType.BRUSH) {
        engine.setTool(tool)
        engine.pointerDown(x0, y0, 1f, 0)
        for (i in 1..20) engine.pointerMove(x0 + (x1 - x0) * i / 20f, y0 + (y1 - y0) * i / 20f, 1f, i * 8L)
        engine.pointerUp()
    }

    private fun alphaAt(x: Int, y: Int): Int = activeCell()?.getPixel(x, y)?.let { Color.alpha(it) } ?: 0

    private fun lockedEvents() = events.count { it is EngineEvent.Locked }

    @Test
    fun strokePaintsAndUndoRedoRestorePixels() {
        engine.setColor(Color.RED)
        stroke(10f, 40f, 90f, 40f)
        assertTrue(alphaAt(50, 40) > 0)
        assertTrue(engine.state.value.canUndo)
        engine.undo()
        assertEquals(0, alphaAt(50, 40))
        engine.redo()
        assertTrue(alphaAt(50, 40) > 0)
    }

    @Test
    fun eraserRemovesPaint() {
        stroke(10f, 40f, 90f, 40f)
        engine.setBrush(ToolType.ERASER, defaultBrushFor(ToolType.ERASER).copy(size = 30f, opacity = 1f, flow = 1f, hardness = 1f))
        stroke(10f, 40f, 90f, 40f, ToolType.ERASER)
        assertTrue(alphaAt(50, 40) < 10)
    }

    @Test
    fun lockedAndHiddenLayersCannotBePainted() {
        val id = engine.doc.activeLayerId
        engine.setLayerLocked(id, true)
        stroke(10f, 40f, 90f, 40f)
        assertEquals(0, alphaAt(50, 40))
        assertTrue(events.any { it is EngineEvent.Message && it.kind == MessageKind.LAYER_LOCKED })
    }

    @Test
    fun selectionRestrictsPainting() {
        engine.setTool(ToolType.SELECT_RECT)
        engine.pointerDown(0f, 0f, 1f, 0)
        engine.pointerMove(40f, 80f, 1f, 1)
        engine.pointerUp()
        assertTrue(engine.state.value.hasSelection)
        stroke(5f, 40f, 95f, 40f)
        assertTrue(alphaAt(20, 40) > 0)
        assertEquals(0, alphaAt(70, 40))
    }

    @Test
    fun freeLayerLimitIsEnforcedAndProAllowsMore() {
        while (engine.doc.rasterLayerCount < 15) engine.addLayer()
        assertEquals(15, engine.doc.rasterLayerCount)
        engine.addLayer()
        assertEquals(15, engine.doc.rasterLayerCount)
        engine.duplicateLayer(engine.doc.activeLayerId)
        assertEquals(15, engine.doc.rasterLayerCount)
        assertTrue(lockedEvents() >= 2)
        tier = Tier.PRO
        engine.addLayer()
        assertEquals(16, engine.doc.rasterLayerCount)
    }

    @Test
    fun downgradeKeepsExistingLayersAndArtwork() {
        tier = Tier.PRO
        repeat(25) { engine.addLayer() }
        stroke(10f, 40f, 90f, 40f)
        val count = engine.doc.rasterLayerCount
        tier = Tier.FREE
        assertEquals(count, engine.doc.rasterLayerCount)
        assertTrue(alphaAt(50, 40) > 0)
        engine.addLayer()
        assertEquals(count, engine.doc.rasterLayerCount)
        // Existing layers stay fully editable.
        engine.setColor(Color.BLUE)
        stroke(10f, 60f, 90f, 60f)
        assertTrue(alphaAt(50, 60) > 0)
    }

    @Test
    fun proFeaturesAreLockedOnFreeThroughEveryEnginePath() {
        val id = engine.doc.activeLayerId
        engine.setTool(ToolType.SELECT_LASSO)
        assertNotEquals(ToolType.SELECT_LASSO, engine.state.value.tool)
        engine.addMask(id)
        assertFalse(engine.doc.layer(id)!!.hasMask)
        engine.setClipping(id, true)
        assertFalse(engine.doc.layer(id)!!.clipToBelow)
        engine.setBlendMode(id, LayerBlendMode.COLOR_DODGE)
        assertEquals(LayerBlendMode.NORMAL, engine.doc.layer(id)!!.blendMode)
        engine.groupActiveLayer()
        assertTrue(engine.doc.layers.none { it.isGroup })
        engine.setGuides(GuideSettings(symmetry = SymmetryMode.RADIAL))
        assertEquals(SymmetryMode.NONE, engine.doc.guides.symmetry)
        engine.setFps(30)
        assertEquals(12, engine.doc.fps)
        engine.applyTransition(id, TransitionPreset.SLIDE_IN_LEFT, 6)
        assertFalse(engine.doc.layer(id)!!.animation.isAnimated)
        engine.setKeyframe(id, TransformProperty.ROTATION, 45f, EasingType.ELASTIC)
        assertFalse(engine.doc.layer(id)!!.animation.isAnimated)
        assertTrue(lockedEvents() >= 9)

        // Basic features remain available on Free.
        engine.setBlendMode(id, LayerBlendMode.MULTIPLY)
        assertEquals(LayerBlendMode.MULTIPLY, engine.doc.layer(id)!!.blendMode)
        engine.applyTransition(id, TransitionPreset.FADE_IN, 6)
        assertTrue(engine.doc.layer(id)!!.animation.isAnimated)
        engine.setFps(24)
        assertEquals(24, engine.doc.fps)
    }

    @Test
    fun proUnlocksProFeaturesButNotMaxOnes() {
        tier = Tier.PRO
        val id = engine.doc.activeLayerId
        engine.addMask(id)
        assertTrue(engine.doc.layer(id)!!.hasMask)
        engine.setBlendMode(id, LayerBlendMode.COLOR_DODGE)
        assertEquals(LayerBlendMode.COLOR_DODGE, engine.doc.layer(id)!!.blendMode)
        engine.setFps(30)
        assertEquals(30, engine.doc.fps)
        engine.setFps(60)
        assertEquals(30, engine.doc.fps)
        engine.setKeyframe(id, TransformProperty.ROTATION, 45f, EasingType.ELASTIC)
        assertFalse(engine.doc.layer(id)!!.animation.isAnimated)
        tier = Tier.MAX
        engine.setFps(60)
        assertEquals(60, engine.doc.fps)
        engine.setKeyframe(id, TransformProperty.ROTATION, 45f, EasingType.ELASTIC)
        assertTrue(engine.doc.layer(id)!!.animation.isAnimated)
    }

    @Test
    fun frameOperationsKeepDrawingsWithTheirFrames() {
        engine.setColor(Color.RED)
        stroke(10f, 40f, 90f, 40f)
        engine.duplicateFrames(listOf(0))
        assertEquals(2, engine.doc.frames.size)
        assertEquals(1, engine.doc.activeFrameIndex)
        assertTrue(alphaAt(50, 40) > 0) // duplicated drawing
        engine.addFrame()
        assertEquals(3, engine.doc.frames.size)
        assertEquals(0, alphaAt(50, 40)) // blank frame
        engine.deleteFrames(listOf(0))
        assertEquals(2, engine.doc.frames.size)
        engine.undo()
        assertEquals(3, engine.doc.frames.size)
        engine.setActiveFrame(0)
        assertTrue(alphaAt(50, 40) > 0)
        val first = engine.doc.frames[0].id
        engine.moveFrames(listOf(0), 2)
        assertEquals(first, engine.doc.frames[2].id)
    }

    @Test
    fun freeFrameLimitIsEnforced() {
        while (engine.doc.frames.size < 500) engine.addFrame()
        engine.addFrame()
        assertEquals(500, engine.doc.frames.size)
        assertTrue(lockedEvents() >= 1)
    }

    @Test
    fun keyframesInterpolateBetweenFrames() {
        repeat(10) { engine.addFrame() }
        val id = engine.doc.activeLayerId
        engine.setActiveFrame(0)
        engine.setKeyframe(id, TransformProperty.POSITION_X, 0f, EasingType.LINEAR)
        engine.setActiveFrame(10)
        engine.setKeyframe(id, TransformProperty.POSITION_X, 100f, EasingType.LINEAR)
        val anim = engine.doc.layer(id)!!.animation
        assertEquals(50f, KeyframeSampler.sample(anim, 5f).x, 0.01f)
        engine.undo()
        assertEquals(1, engine.doc.layer(id)!!.animation.track(TransformProperty.POSITION_X).size)
    }

    @Test
    fun mergeDownCombinesPixelsAndIsUndoable() {
        engine.setColor(Color.RED)
        stroke(10f, 20f, 90f, 20f)
        val lower = engine.doc.activeLayerId
        engine.addLayer()
        val upper = engine.doc.activeLayerId
        engine.setColor(Color.BLUE)
        stroke(10f, 60f, 90f, 60f)
        val before = engine.doc.rasterLayerCount
        engine.mergeDown(upper)
        assertEquals(before - 1, engine.doc.rasterLayerCount)
        assertEquals(lower, engine.doc.activeLayerId)
        assertTrue(alphaAt(50, 20) > 0)
        assertTrue(alphaAt(50, 60) > 0)
        engine.undo()
        assertEquals(before, engine.doc.rasterLayerCount)
    }

    @Test
    fun fillFloodsTheLayer() {
        engine.setColor(Color.GREEN)
        engine.setFillSettings(FillSettings(tolerance = 10, sampleAllLayers = false, expand = 0))
        engine.fillAt(50f, 40f)
        val deadline = System.currentTimeMillis() + 5_000
        while (engine.state.value.busy && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertEquals(Color.GREEN, activeCell()!!.getPixel(50, 40))
        assertEquals(Color.GREEN, activeCell()!!.getPixel(0, 0))
    }

    @Test
    fun saveSnapshotContainsChangesAndClearsDirtyState() {
        stroke(10f, 40f, 90f, 40f)
        assertTrue(engine.hasUnsavedChanges)
        val snapshot = engine.snapshotForSave()
        assertEquals(1, snapshot.cells.size)
        assertTrue(snapshot.thumbnail != null)
        engine.onSaved(snapshot)
        assertFalse(engine.store.hasUnsavedChanges)
    }
}
