package com.godraw.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.godraw.app.AppContainer
import com.godraw.app.R
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.data.settings.WorkspaceMode
import com.godraw.app.engine.CanvasView
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineEvent
import com.godraw.app.engine.ToolType
import com.godraw.app.ui.common.UpgradeDialog
import com.godraw.app.ui.home.TextInputDialog
import com.godraw.app.ui.theme.LocalGoDrawExtras
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import kotlinx.coroutines.launch

@Composable
fun EditorScreen(
    container: AppContainer,
    projectId: String,
    onClose: () -> Unit,
    onOpenSubscription: () -> Unit,
) {
    val context = LocalContext.current
    val vm: EditorViewModel = viewModel(
        key = "editor_$projectId",
        factory = viewModelFactory { initializer { EditorViewModel(container, projectId, ResourceEngineStrings(context.applicationContext)) } },
    )
    val load by vm.load.collectAsStateWithLifecycle()
    when (val l = load) {
        EditorLoad.Loading -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        EditorLoad.Failed -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.error_open_project), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onClose) { Text(stringResource(R.string.back)) }
            }
        }
        is EditorLoad.Ready -> Editor(vm, l.engine, onClose, onOpenSubscription)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(vm: EditorViewModel, engine: DrawingEngine, onClose: () -> Unit, onOpenSubscription: () -> Unit) {
    val st by engine.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val panel by vm.panel.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val gate by vm.gate.collectAsStateWithLifecycle()
    val extras = LocalGoDrawExtras.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val simple = settings.workspaceMode == WorkspaceMode.SIMPLE

    var lockedDialog by remember { mutableStateOf<Pair<Access.Locked, Feature?>?>(null) }
    var textRequest by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var canvasView by remember { mutableStateOf<CanvasView?>(null) }

    BackHandler { vm.close(onClose) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) vm.onStop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            when (m) {
                is EditorMessage.Locked -> lockedDialog = m.access to m.feature
                is EditorMessage.Engine -> when (val e = m.event) {
                    is EngineEvent.TextRequested -> textRequest = e.x to e.y
                    is EngineEvent.Message -> scope.launch { snackbar.showSnackbar(context.getString(messageText(e.kind))) }
                    else -> Unit
                }
                EditorMessage.SaveFailed -> scope.launch { snackbar.showSnackbar(context.getString(R.string.error_save)) }
                EditorMessage.AudioImportFailed -> scope.launch { snackbar.showSnackbar(context.getString(R.string.error_audio_import)) }
                EditorMessage.Saved -> scope.launch { snackbar.showSnackbar(context.getString(R.string.saved)) }
                EditorMessage.BrushSaved -> scope.launch { snackbar.showSnackbar(context.getString(R.string.brush_saved)) }
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(extras.canvasBackdrop),
    ) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            // ---------------------------------------------------------------- top bar
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { vm.close(onClose) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Column(
                        Modifier
                            .weight(1f)
                            .clickable { renaming = true },
                    ) {
                        Text(st.doc.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (saving) stringResource(R.string.saving) else "${st.doc.width}×${st.doc.height}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { engine.undo() }, enabled = st.canUndo || st.floating) {
                        Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo))
                    }
                    IconButton(onClick = { engine.redo() }, enabled = st.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo))
                    }
                    IconButton(onClick = { vm.setWorkspaceMode(if (simple) WorkspaceMode.ADVANCED else WorkspaceMode.SIMPLE) }) {
                        Icon(Icons.Filled.ViewQuilt, stringResource(if (simple) R.string.switch_to_advanced else R.string.switch_to_simple))
                    }
                    IconButton(onClick = { vm.setPanel(EditorPanel.EXPORT) }) {
                        Icon(Icons.Filled.IosShare, stringResource(R.string.export))
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.save_now)) },
                                leadingIcon = { Icon(Icons.Filled.Save, null) },
                                onClick = { menu = false; vm.save(manual = true) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.reset_view)) },
                                leadingIcon = { Icon(Icons.Filled.ZoomOutMap, null) },
                                onClick = { menu = false; canvasView?.resetView() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mirror_view)) },
                                leadingIcon = { Icon(Icons.Filled.Flip, null) },
                                onClick = { menu = false; canvasView?.mirrorView() },
                            )
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, onClick = { menu = false; engine.selectAll() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.deselect)) }, onClick = { menu = false; engine.deselect() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.invert_selection)) }, onClick = { menu = false; engine.invertSelection() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.paste)) }, onClick = { menu = false; engine.paste() })
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.customize_toolbar)) },
                                onClick = { menu = false; vm.setPanel(EditorPanel.TOOLBAR) },
                            )
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- workspace
            Row(Modifier.weight(1f)) {
                if (!settings.leftHanded) ToolRail(vm, engine, st, settings, gate, simple)
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            CanvasView(ctx, engine).also { canvasView = it }
                        },
                        update = { view ->
                            view.settings = settings
                            view.backdropColor = extras.canvasBackdrop.toArgb()
                            view.onUndoGesture = { engine.undo() }
                            view.onRedoGesture = { engine.redo() }
                            view.onColorPicked = { vm.addRecentColor(it) }
                            view.invalidate()
                        },
                    )
                    if (st.tool.isPainting || st.tool.isShape) {
                        SideSliders(
                            engine = engine,
                            st = st,
                            modifier = Modifier
                                .align(if (settings.leftHanded) Alignment.CenterStart else Alignment.CenterEnd)
                                .padding(8.dp),
                        )
                    }
                    ContextBar(vm, engine, st, Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                    if (st.busy || st.progress != null) {
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val p = st.progress
                            if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.width(160.dp))
                            else CircularProgressIndicator(Modifier.size(28.dp))
                        }
                    }
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                }
                if (settings.leftHanded) ToolRail(vm, engine, st, settings, gate, simple)
                if (wide && panel != EditorPanel.NONE) {
                    Surface(
                        modifier = Modifier
                            .width(380.dp)
                            .fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                    ) {
                        PanelContent(panel, vm, engine, st, settings, gate, simple)
                    }
                }
            }

            if (st.doc.isAnimation) {
                TimelinePanel(vm, engine, st, gate, simple)
            }
            BottomBar(vm, engine, st, panel, simple)
        }
    }

    if (!wide && panel != EditorPanel.NONE) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = panel == EditorPanel.EXPORT || panel == EditorPanel.COLOR)
        ModalBottomSheet(
            onDismissRequest = { vm.setPanel(EditorPanel.NONE) },
            sheetState = sheet,
            scrimColor = Color.Black.copy(alpha = 0.2f),
        ) {
            PanelContent(panel, vm, engine, st, settings, gate, simple)
        }
    }

    lockedDialog?.let { (access, feature) ->
        UpgradeDialog(
            locked = access,
            featureName = feature?.let { stringResource(featureName(it)) },
            onDismiss = { lockedDialog = null },
            onUpgrade = {
                lockedDialog = null
                vm.close(onOpenSubscription)
            },
        )
    }
    textRequest?.let { (x, y) ->
        TextToolDialog(
            onDismiss = { textRequest = null },
            onConfirm = { spec ->
                textRequest = null
                engine.placeText(spec, x, y)
            },
        )
    }
    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.rename_project),
            initial = st.doc.name,
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                vm.renameProject(it)
            },
        )
    }
}

@Composable
private fun PanelContent(
    panel: EditorPanel,
    vm: EditorViewModel,
    engine: DrawingEngine,
    st: com.godraw.app.engine.EngineState,
    settings: AppSettings,
    gate: com.godraw.core.entitlement.FeatureGate,
    simple: Boolean,
) {
    when (panel) {
        EditorPanel.BRUSHES -> BrushPanel(vm, engine, st, settings, gate, simple)
        EditorPanel.COLOR -> ColorPanel(vm, engine, st, settings)
        EditorPanel.LAYERS -> LayersPanel(vm, engine, st, gate, simple)
        EditorPanel.EFFECTS -> EffectsPanel(vm, engine, st, gate)
        EditorPanel.ANIMATE -> AnimatePanel(vm, engine, st, gate, simple)
        EditorPanel.AUDIO -> AudioPanel(vm, engine, st, gate)
        EditorPanel.GUIDES -> GuidesPanel(engine, st, gate)
        EditorPanel.EXPORT -> ExportPanel(vm, engine, st, settings, gate)
        EditorPanel.TOOLBAR -> ToolbarCustomizePanel(vm, settings, gate)
        EditorPanel.NONE -> Unit
    }
}

@Composable
private fun BottomBar(vm: EditorViewModel, engine: DrawingEngine, st: com.godraw.app.engine.EngineState, panel: EditorPanel, simple: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(if (simple) 64.dp else 56.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(Icons.Filled.Brush, R.string.panel_brushes, panel == EditorPanel.BRUSHES, simple) { vm.setPanel(EditorPanel.BRUSHES) }
            // Current color swatch opens the color panel.
            Column(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { vm.setPanel(EditorPanel.COLOR) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(if (simple) 30.dp else 26.dp)
                        .clip(CircleShape)
                        .background(Color(st.color))
                        .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                if (simple) Text(stringResource(R.string.panel_color), style = MaterialTheme.typography.labelSmall)
            }
            BarButton(Icons.Filled.Layers, R.string.panel_layers, panel == EditorPanel.LAYERS, simple) { vm.setPanel(EditorPanel.LAYERS) }
            BarButton(Icons.Filled.AutoAwesome, R.string.panel_effects, panel == EditorPanel.EFFECTS, simple) { vm.setPanel(EditorPanel.EFFECTS) }
            if (st.doc.isAnimation) {
                BarButton(Icons.Filled.Timeline, R.string.panel_animate, panel == EditorPanel.ANIMATE, simple) { vm.setPanel(EditorPanel.ANIMATE) }
                if (!simple) BarButton(Icons.Filled.MusicNote, R.string.panel_audio, panel == EditorPanel.AUDIO, simple) { vm.setPanel(EditorPanel.AUDIO) }
            }
            BarButton(Icons.Filled.GridOn, R.string.panel_guides, panel == EditorPanel.GUIDES, simple) { vm.setPanel(EditorPanel.GUIDES) }
        }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: Int, selected: Boolean, showLabel: Boolean, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = stringResource(label), tint = tint)
        if (showLabel) Text(stringResource(label), style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

/** Tool-specific actions shown above the canvas (selection, transform, effects preview...). */
@Composable
private fun ContextBar(vm: EditorViewModel, engine: DrawingEngine, st: com.godraw.app.engine.EngineState, modifier: Modifier) {
    val items = ArrayList<Pair<Int, () -> Unit>>()
    if (st.floating) {
        items += R.string.flip_horizontal to { engine.flipFloating(true) }
        items += R.string.flip_vertical to { engine.flipFloating(false) }
        items += R.string.rotate_90 to { engine.rotateFloating(90f) }
        items += (if (st.floatingDistort) R.string.distort_off else R.string.distort) to { engine.setDistort(!st.floatingDistort) }
        items += R.string.cancel to { engine.cancelFloating() }
        items += R.string.apply to { engine.commitFloating() }
    } else if (st.tool == ToolType.TRANSFORM) {
        items += R.string.flip_horizontal to { engine.flipFloating(true) }
        items += R.string.flip_vertical to { engine.flipFloating(false) }
        items += R.string.rotate_90 to { engine.rotateFloating(90f) }
    } else if (st.hasSelection) {
        items += R.string.deselect to { engine.deselect() }
        items += R.string.invert_selection to { engine.invertSelection() }
        items += R.string.copy to { engine.copySelection() }
        items += R.string.cut to { engine.copySelection(cut = true) }
        items += R.string.clear_pixels to { engine.clearSelectionPixels() }
        items += R.string.fill_selection to { engine.fillSelection() }
        items += R.string.tool_transform to { engine.setTool(ToolType.TRANSFORM); engine.liftForTransform(); Unit }
    } else if (st.editingMask) {
        items += R.string.done_editing_mask to { engine.setEditingMask(false) }
    }
    if (items.isEmpty()) return
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
        androidx.compose.foundation.lazy.LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(items.size) { i ->
                val (label, action) = items[i]
                TextButton(onClick = action) { Text(stringResource(label), maxLines = 1) }
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}
