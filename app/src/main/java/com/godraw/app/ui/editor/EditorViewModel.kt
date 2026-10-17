package com.godraw.app.ui.editor

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godraw.app.AppContainer
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.engine.CellStore
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EffectController
import com.godraw.app.engine.EngineEvent
import com.godraw.app.engine.EngineStrings
import com.godraw.app.engine.FrameClipboard
import com.godraw.app.engine.PlaybackController
import com.godraw.app.engine.SnapshotManager
import com.godraw.app.engine.ToolType
import com.godraw.app.engine.addAudioClip
import com.godraw.app.engine.copyFrames
import com.godraw.app.engine.pasteFrames
import com.godraw.app.export.ExportError
import com.godraw.app.export.ExportException
import com.godraw.app.export.ExportManager
import com.godraw.app.export.ExportOptions
import com.godraw.app.export.ExportState
import com.godraw.core.brush.BrushPreset
import com.godraw.core.color.Palette
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.entitlement.Tier
import com.godraw.core.model.Ids
import com.godraw.core.model.LayerAnimation
import com.godraw.core.storage.AtomicFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

sealed interface EditorLoad {
    data object Loading : EditorLoad
    data class Ready(val engine: DrawingEngine) : EditorLoad
    data object Failed : EditorLoad
}

enum class EditorPanel { NONE, BRUSHES, COLOR, LAYERS, EFFECTS, ANIMATE, AUDIO, GUIDES, EXPORT, TOOLBAR }

/** UI-level messages produced by the editor. */
sealed interface EditorMessage {
    data class Engine(val event: EngineEvent) : EditorMessage
    data class Locked(val access: Access.Locked, val feature: Feature? = null) : EditorMessage
    data object SaveFailed : EditorMessage
    data object AudioImportFailed : EditorMessage
    data object Saved : EditorMessage
    data object BrushSaved : EditorMessage
}

class EditorViewModel(
    private val container: AppContainer,
    val projectId: String,
    private val strings: EngineStrings,
) : ViewModel() {

    private val repo = container.projects

    private val _load = MutableStateFlow<EditorLoad>(EditorLoad.Loading)
    val load: StateFlow<EditorLoad> = _load.asStateFlow()

    val settings: StateFlow<AppSettings> = container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val gate: StateFlow<FeatureGate> = container.entitlements.gate
    val tier: StateFlow<Tier> = container.entitlements.tier

    val panel = MutableStateFlow(EditorPanel.NONE)
    val exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val saving = MutableStateFlow(false)
    val selectedFrames = MutableStateFlow<Set<Int>>(emptySet())
    val timelineExpanded = MutableStateFlow(true)

    private val _messages = MutableSharedFlow<EditorMessage>(extraBufferCapacity = 16)
    val messages: SharedFlow<EditorMessage> = _messages.asSharedFlow()

    var engine: DrawingEngine? = null
        private set
    var playback: PlaybackController? = null
        private set
    var effects: EffectController? = null
        private set
    val exporter = ExportManager(
        container.appContext,
        cellLoader = { key -> repo.loadCell(projectId, key) },
        audioFile = { clip -> repo.audioFile(projectId, clip) },
    )

    private var frameClipboard: FrameClipboard? = null
    private var animationClipboard: LayerAnimation? = null
    private var autosaveJob: Job? = null
    private var saveJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val doc = repo.load(projectId)
                val s = container.settings.current()
                val budget = (Runtime.getRuntime().maxMemory() / 3).coerceAtLeast(48L * 1024 * 1024)
                val store = CellStore(
                    width = doc.width,
                    height = doc.height,
                    loader = { key -> repo.loadCell(projectId, key) },
                    spill = { key, bmp -> spillCell(key, bmp) },
                    budgetBytes = budget,
                )
                val undoDir = File(container.appContext.cacheDir, "undo/$projectId").apply { deleteRecursively() }
                val snapshots = SnapshotManager(store, undoDir) { key -> File(repo.cellsDir(projectId), key.fileName) }
                val e = DrawingEngine(doc, store, snapshots, { gate.value }, strings, viewModelScope, s.defaultStabilization)
                e.updatePins()
                engine = e
                playback = PlaybackController(e) { clip -> repo.audioFile(projectId, clip) }
                effects = EffectController(e)
                _load.value = EditorLoad.Ready(e)
                launch { e.events.collect { ev -> forwardEngineEvent(ev) } }
                launch { settings.collect { st -> e.input = e.input.copy(pressureEnabled = st.pressureEnabled, pressureCurve = st.pressureCurve) } }
                startAutosave()
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _load.value = EditorLoad.Failed
            }
        }
    }

    private fun forwardEngineEvent(ev: EngineEvent) {
        when (ev) {
            is EngineEvent.Locked -> _messages.tryEmit(EditorMessage.Locked(ev.access, ev.feature))
            is EngineEvent.ColorPicked -> addRecentColor(ev.color)
            else -> _messages.tryEmit(EditorMessage.Engine(ev))
        }
    }

    /** Writes an evicted, unsaved cell into the project so memory can be released safely. */
    private fun spillCell(key: com.godraw.core.model.CellKey, bmp: Bitmap): Boolean = runCatching {
        AtomicFiles.write(File(repo.cellsDir(projectId), key.fileName)) { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.isSuccess

    private fun startAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            while (isActive) {
                val s = settings.value
                delay(s.autosaveIntervalSec.coerceAtLeast(10) * 1000L)
                val e = engine ?: continue
                if (s.autosaveEnabled && e.hasUnsavedChanges && !e.stroke.active && !e.state.value.busy && !e.state.value.playing) {
                    save(manual = false)
                }
            }
        }
    }

    /** Saves in the background. Drawing can continue during the save. */
    fun save(manual: Boolean = true): Job? {
        val e = engine ?: return null
        if (saveJob?.isActive == true) return saveJob
        val snapshot = e.snapshotForSave()
        saving.value = true
        saveJob = viewModelScope.launch {
            try {
                repo.save(snapshot.doc, snapshot.cells, snapshot.thumbnail)
                e.onSaved(snapshot)
                // Large change sets are written in bounded rounds to keep memory use flat.
                var rounds = 0
                while (e.store.hasUnsavedChanges && rounds++ < MAX_SAVE_ROUNDS) {
                    val next = e.snapshotForSave(includeThumbnail = false)
                    if (next.cells.isEmpty()) break
                    repo.save(next.doc, next.cells, null)
                    e.onSaved(next)
                }
                if (manual) _messages.tryEmit(EditorMessage.Saved)
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                _messages.tryEmit(EditorMessage.SaveFailed)
            } finally {
                saving.value = false
            }
        }
        return saveJob
    }

    /** Commits pending edits, saves, then calls [onDone]. */
    fun close(onDone: () -> Unit) {
        val e = engine
        if (e == null) {
            onDone()
            return
        }
        playback?.stop()
        if (e.floating != null) e.commitFloating()
        viewModelScope.launch {
            saveJob?.join()
            if (e.hasUnsavedChanges) save(manual = false)?.join()
            onDone()
        }
    }

    /** Called when the app goes to the background. */
    fun onStop() {
        val e = engine ?: return
        playback?.stop()
        if (e.hasUnsavedChanges && !e.stroke.active) save(manual = false)
    }

    fun setPanel(p: EditorPanel) {
        panel.value = if (panel.value == p) EditorPanel.NONE else p
        if (panel.value != EditorPanel.EFFECTS) effects?.cancel()
    }

    fun locked(access: Access.Locked, feature: Feature? = null) {
        _messages.tryEmit(EditorMessage.Locked(access, feature))
    }

    // ---------------------------------------------------------------- frames

    fun toggleFrameSelection(index: Int) {
        val access = gate.value.check(Feature.MULTI_FRAME_SELECTION)
        if (access is Access.Locked) {
            locked(access, Feature.MULTI_FRAME_SELECTION)
            return
        }
        selectedFrames.value = selectedFrames.value.let { if (index in it) it - index else it + index }
    }

    fun clearFrameSelection() {
        selectedFrames.value = emptySet()
    }

    /** Frames targeted by frame commands: the multi-selection or the active frame. */
    fun targetFrames(): List<Int> {
        val e = engine ?: return emptyList()
        val sel = selectedFrames.value.filter { it in e.doc.frames.indices }
        return sel.ifEmpty { listOf(e.doc.activeFrameIndex) }
    }

    fun copyFrames() {
        frameClipboard = engine?.copyFrames(targetFrames())
    }

    fun pasteFrames() {
        val clip = frameClipboard ?: return
        engine?.pasteFrames(clip)
        clearFrameSelection()
    }

    val hasFrameClipboard: Boolean get() = frameClipboard != null

    fun copyAnimation(anim: LayerAnimation?) {
        if (anim != null) animationClipboard = anim
    }

    val animationClipboardValue: LayerAnimation? get() = animationClipboard

    // ---------------------------------------------------------------- colors, brushes, palettes

    fun addRecentColor(color: Int) = viewModelScope.launch { container.settings.addRecentColor(color) }

    fun saveCustomBrush(brush: BrushPreset, name: String) {
        val access = gate.value.check(Feature.CUSTOM_BRUSHES)
        if (access is Access.Locked) {
            locked(access, Feature.CUSTOM_BRUSHES)
            return
        }
        viewModelScope.launch {
            val custom = brush.copy(id = "custom_" + Ids.newId(), customName = name.trim().take(40), isCustom = true)
            container.settings.setCustomBrushes(settings.value.customBrushes + custom)
            _messages.tryEmit(EditorMessage.BrushSaved)
        }
    }

    fun deleteCustomBrush(id: String) = viewModelScope.launch {
        container.settings.setCustomBrushes(settings.value.customBrushes.filterNot { it.id == id })
    }

    fun savePalette(name: String, colors: List<Int>) = viewModelScope.launch {
        if (colors.isEmpty()) return@launch
        val palette = Palette(Ids.newId(), name.trim().take(40), colors.distinct().take(64))
        container.settings.setSavedPalettes(settings.value.savedPalettes + palette)
    }

    fun deletePalette(id: String) = viewModelScope.launch {
        container.settings.setSavedPalettes(settings.value.savedPalettes.filterNot { it.id == id })
    }

    fun addColorToPalette(paletteId: String, color: Int) = viewModelScope.launch {
        container.settings.setSavedPalettes(
            settings.value.savedPalettes.map { if (it.id == paletteId) it.copy(colors = (it.colors + color).distinct().take(64)) else it },
        )
    }

    fun setQuickTools(tools: List<ToolType>) {
        val access = gate.value.check(Feature.TOOLBAR_CUSTOMIZATION)
        if (access is Access.Locked) {
            locked(access, Feature.TOOLBAR_CUSTOMIZATION)
            return
        }
        viewModelScope.launch { container.settings.setQuickTools(tools.map { it.id }) }
    }

    fun renameProject(name: String) {
        val e = engine ?: return
        val clean = name.trim().take(80)
        if (clean.isEmpty()) return
        e.setDocQuiet(e.doc.copy(name = clean))
        save(manual = false)
    }

    fun setWorkspaceMode(mode: com.godraw.app.data.settings.WorkspaceMode) =
        viewModelScope.launch { container.settings.setWorkspaceMode(mode) }

    // ---------------------------------------------------------------- audio

    fun importAudio(uri: Uri) {
        val e = engine ?: return
        val g = gate.value
        if (e.doc.audioClips.isNotEmpty()) {
            val access = g.check(Feature.MULTIPLE_AUDIO_CLIPS)
            if (access is Access.Locked) {
                locked(access, Feature.MULTIPLE_AUDIO_CLIPS)
                return
            }
        }
        val limit = g.canAddAudioClip(e.doc.audioClips.size)
        if (limit is Access.Locked) {
            locked(limit)
            return
        }
        viewModelScope.launch {
            runCatching { repo.importAudio(projectId, uri) }
                .onSuccess { clip -> if (!e.addAudioClip(clip)) repo.audioFile(projectId, clip).delete() }
                .onFailure { _messages.tryEmit(EditorMessage.AudioImportFailed) }
        }
    }

    // ---------------------------------------------------------------- export

    private var exportJob: Job? = null

    fun export(options: ExportOptions, destination: Uri?) {
        val e = engine ?: return
        if (exportJob?.isActive == true) return
        options.format.feature?.let { f ->
            val access = gate.value.check(f)
            if (access is Access.Locked) {
                locked(access, f)
                return
            }
        }
        if (e.floating != null) e.commitFloating()
        exportState.value = ExportState.Running(0f)
        exportJob = viewModelScope.launch {
            try {
                // Make sure the files on disk match what the user sees.
                saveJob?.join()
                if (e.hasUnsavedChanges) save(manual = false)?.join()
                val result = exporter.export(e.doc, options, gate.value, destination) { p ->
                    exportState.value = ExportState.Running(p)
                }
                exportState.value = result
            } catch (c: CancellationException) {
                exportState.value = ExportState.Idle
                throw c
            } catch (x: ExportException) {
                exportState.value = ExportState.Failed(x.error)
            } catch (t: Throwable) {
                exportState.value = ExportState.Failed(ExportError.UNKNOWN)
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        exportState.value = ExportState.Idle
    }

    fun resetExport() {
        if (exportJob?.isActive != true) exportState.value = ExportState.Idle
    }

    private companion object {
        const val MAX_SAVE_ROUNDS = 200
    }

    override fun onCleared() {
        val e = engine
        if (e != null && e.hasUnsavedChanges) {
            // Save with the app scope so the save completes even though this ViewModel is gone.
            if (e.floating != null) e.commitFloating()
            val snapshot = e.snapshotForSave()
            container.appScope.launch {
                runCatching { repo.save(snapshot.doc, snapshot.cells, snapshot.thumbnail) }
            }
        }
        playback?.release()
        e?.release()
        super.onCleared()
    }
}
