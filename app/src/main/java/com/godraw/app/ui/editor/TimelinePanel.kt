package com.godraw.app.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godraw.app.R
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.engine.addFrame
import com.godraw.app.engine.deleteFrames
import com.godraw.app.engine.duplicateFrames
import com.godraw.app.engine.moveFrames
import com.godraw.app.engine.setActiveFrame
import com.godraw.app.engine.setFps
import com.godraw.app.engine.setFrameHold
import com.godraw.app.engine.setLoop
import com.godraw.app.engine.setOnionSkin
import com.godraw.app.ui.common.Checkerboard
import com.godraw.app.ui.common.LockMarker
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.model.CellKey
import kotlin.math.roundToInt

@Composable
private fun rememberFrameThumb(engine: DrawingEngine, index: Int, frameId: String, version: Long): ImageBitmap? {
    val state by produceState<ImageBitmap?>(null, frameId, version) {
        val doc = engine.doc
        val keys = doc.layers.filter { !it.isGroup && it.visible }.map { CellKey.forLayer(it, frameId) }
        engine.store.prefetch(keys)
        val i = engine.doc.frames.indexOfFirst { it.id == frameId }.takeIf { it >= 0 } ?: index
        value = engine.renderThumbnail(120, i).asImageBitmap()
    }
    return state
}

@Composable
fun TimelinePanel(vm: EditorViewModel, engine: DrawingEngine, st: EngineState, gate: FeatureGate, simple: Boolean) {
    val doc = st.doc
    val expanded by vm.timelineExpanded.collectAsStateWithLifecycle()
    val selected by vm.selectedFrames.collectAsStateWithLifecycle()
    var selectMode by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var fpsDialog by remember { mutableStateOf(false) }
    var onionDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val shownFrame = if (st.playing) st.displayFrame else doc.activeFrameIndex
    val activeLayer = st.activeLayer

    LaunchedEffect(shownFrame) {
        if (shownFrame !in listState.layoutInfo.visibleItemsInfo.map { it.index }) {
            listState.animateScrollToItem(shownFrame.coerceAtLeast(0))
        }
    }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.playback?.toggle() }) {
                    Icon(
                        if (st.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (st.playing) R.string.pause else R.string.play),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    stringResource(R.string.frame_position, shownFrame + 1, doc.frames.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                TextButton(onClick = { fpsDialog = true }) { Text(stringResource(R.string.fps_value, doc.fps)) }
                IconToggleButton(checked = doc.onionSkin.enabled, onCheckedChange = { engine.setOnionSkin(doc.onionSkin.copy(enabled = it)) }) {
                    Icon(
                        Icons.Filled.Layers,
                        stringResource(R.string.onion_skin),
                        tint = if (doc.onionSkin.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!simple) TextButton(onClick = { onionDialog = true }) { Text(stringResource(R.string.onion_settings)) }
                IconToggleButton(checked = doc.loop, onCheckedChange = { engine.setLoop(it) }) {
                    Icon(
                        Icons.Filled.Repeat,
                        stringResource(R.string.loop),
                        tint = if (doc.loop) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { engine.addFrame() }) { Icon(Icons.Filled.Add, stringResource(R.string.add_frame)) }
                IconButton(onClick = { engine.duplicateFrames(vm.targetFrames()); vm.clearFrameSelection() }) {
                    Icon(Icons.Filled.LibraryAdd, stringResource(R.string.duplicate_frame))
                }
                IconButton(onClick = { engine.deleteFrames(vm.targetFrames()); vm.clearFrameSelection() }) {
                    Icon(Icons.Filled.Delete, stringResource(R.string.delete_frame))
                }
                if (!simple) {
                    IconButton(onClick = { vm.copyFrames() }) { Icon(Icons.Filled.ContentCopy, stringResource(R.string.copy_frames)) }
                    IconButton(onClick = { vm.pasteFrames() }, enabled = vm.hasFrameClipboard) {
                        Icon(Icons.Filled.ContentPaste, stringResource(R.string.paste_frames))
                    }
                    val hold = doc.frames.getOrNull(doc.activeFrameIndex)?.hold ?: 1
                    IconButton(onClick = { engine.setFrameHold(doc.activeFrameIndex, hold - 1) }, enabled = hold > 1) {
                        Icon(Icons.Filled.Remove, stringResource(R.string.hold_shorter))
                    }
                    Text(stringResource(R.string.hold_value, hold), style = MaterialTheme.typography.labelMedium)
                    IconButton(onClick = { engine.setFrameHold(doc.activeFrameIndex, hold + 1) }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.hold_longer))
                    }
                    val multi = gate.check(Feature.MULTI_FRAME_SELECTION)
                    Box {
                        IconToggleButton(checked = selectMode, onCheckedChange = {
                            if (multi is Access.Locked) vm.locked(multi, Feature.MULTI_FRAME_SELECTION) else {
                                selectMode = it
                                if (!it) vm.clearFrameSelection()
                            }
                        }) {
                            Icon(Icons.Filled.Checklist, stringResource(R.string.select_frames), tint = if (selectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (multi is Access.Locked) LockMarker(multi.requiredTier, Modifier.align(Alignment.TopEnd))
                    }
                    val zoomAccess = gate.check(Feature.TIMELINE_ZOOM)
                    IconButton(onClick = {
                        if (zoomAccess is Access.Locked) vm.locked(zoomAccess, Feature.TIMELINE_ZOOM) else zoom = (zoom / 1.25f).coerceAtLeast(0.5f)
                    }) { Icon(Icons.Filled.ZoomOut, stringResource(R.string.zoom_out)) }
                    IconButton(onClick = {
                        if (zoomAccess is Access.Locked) vm.locked(zoomAccess, Feature.TIMELINE_ZOOM) else zoom = (zoom * 1.25f).coerceAtMost(2.5f)
                    }) { Icon(Icons.Filled.ZoomIn, stringResource(R.string.zoom_in)) }
                }
                IconButton(onClick = { vm.timelineExpanded.value = !expanded }) {
                    Icon(if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess, stringResource(R.string.toggle_timeline))
                }
            }
            if (doc.frames.size > 1) {
                Slider(
                    value = shownFrame.toFloat(),
                    onValueChange = { v -> if (!st.playing) engine.setActiveFrame(v.roundToInt()) },
                    valueRange = 0f..(doc.frames.size - 1).toFloat(),
                    steps = (doc.frames.size - 2).coerceIn(0, 1000),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 12.dp),
                )
            }
            if (expanded) {
                val baseWidth = (if (simple) 76.dp else 64.dp) * zoom
                val itemPx = with(LocalDensity.current) { (baseWidth + 6.dp).toPx() }
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(doc.frames, key = { _, f -> f.id }) { index, frame ->
                        val isActive = index == shownFrame
                        val isSelected = index in selected
                        val version = if (index == doc.activeFrameIndex) st.revision else 0L
                        val thumb = rememberFrameThumb(engine, index, frame.id, version)
                        var dragX by remember { mutableFloatStateOf(0f) }
                        val width = if (simple) baseWidth else baseWidth * (1f + (frame.hold - 1) * 0.35f)
                        val hasKey = activeLayer?.animation?.tracks?.values?.any { t -> t.any { it.frame == index } } == true
                        Column(
                            Modifier
                                .width(width)
                                .offset { IntOffset(dragX.roundToInt(), 0) }
                                .pointerInput(frame.id, selectMode) {
                                    detectTapGestures(onTap = {
                                        // Resolve the index at tap time; frames may have moved since composition.
                                        val current = engine.doc.frames.indexOfFirst { it.id == frame.id }
                                        if (current >= 0) {
                                            if (selectMode) vm.toggleFrameSelection(current) else engine.setActiveFrame(current)
                                        }
                                    })
                                }
                                .pointerInput(frame.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragEnd = {
                                            val delta = (dragX / itemPx).roundToInt()
                                            dragX = 0f
                                            val current = engine.doc.frames.indexOfFirst { it.id == frame.id }
                                            if (delta != 0 && current >= 0) engine.moveFrames(listOf(current), current + delta)
                                        },
                                        onDragCancel = { dragX = 0f },
                                    ) { change, amount ->
                                        change.consume()
                                        dragX += amount.x
                                    }
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(baseWidth * 0.75f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = if (isActive || isSelected) 2.dp else 1.dp,
                                        color = when {
                                            isActive -> MaterialTheme.colorScheme.primary
                                            isSelected -> MaterialTheme.colorScheme.tertiary
                                            else -> MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                    ),
                            ) {
                                Checkerboard(Modifier.fillMaxWidth().height(baseWidth * 0.75f), cell = 8f)
                                if (thumb != null) Image(thumb, null, Modifier.fillMaxWidth().height(baseWidth * 0.75f))
                                if (frame.hold > 1) {
                                    Text(
                                        "×${frame.hold}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 3.dp),
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (hasKey) {
                                    Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary))
                                }
                                Text("${index + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 2.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (fpsDialog) {
        var fps by remember { mutableIntStateOf(doc.fps) }
        val maxFps = gate.limits.maxFps
        AlertDialog(
            onDismissRequest = { fpsDialog = false },
            title = { Text(stringResource(R.string.frame_rate)) },
            text = {
                Column {
                    Text(stringResource(R.string.fps_value, fps), style = MaterialTheme.typography.headlineSmall)
                    Slider(value = fps.toFloat(), onValueChange = { fps = it.roundToInt() }, valueRange = 1f..60f, steps = 58)
                    if (fps > maxFps) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val access = gate.canUseFps(fps)
                            if (access is Access.Locked) LockMarker(access.requiredTier)
                            Text(stringResource(R.string.fps_limit_hint, maxFps), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(8, 12, 15, 24, 30, 60).forEach { p ->
                            TextButton(onClick = { fps = p }) { Text("$p") }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { fpsDialog = false; engine.setFps(fps) }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { fpsDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (onionDialog) {
        val o = doc.onionSkin
        AlertDialog(
            onDismissRequest = { onionDialog = false },
            title = { Text(stringResource(R.string.onion_skin)) },
            text = {
                Column {
                    LabeledSlider(R.string.onion_before, o.framesBefore.toFloat(), 0f..5f, "${o.framesBefore}", steps = 4) {
                        engine.setOnionSkin(o.copy(framesBefore = it.roundToInt()))
                    }
                    LabeledSlider(R.string.onion_after, o.framesAfter.toFloat(), 0f..5f, "${o.framesAfter}", steps = 4) {
                        engine.setOnionSkin(o.copy(framesAfter = it.roundToInt()))
                    }
                    LabeledSlider(R.string.onion_opacity, o.opacity, 0.05f..0.9f, "${(o.opacity * 100).roundToInt()}%") {
                        engine.setOnionSkin(o.copy(opacity = it))
                    }
                    SwitchLine(R.string.onion_tint, o.tint) { engine.setOnionSkin(o.copy(tint = it)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(16.dp).clip(CircleShape).background(Color(o.beforeColor)))
                        Text(stringResource(R.string.onion_previous), style = MaterialTheme.typography.bodySmall)
                        Box(Modifier.size(16.dp).clip(CircleShape).background(Color(o.afterColor)))
                        Text(stringResource(R.string.onion_next), style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onionDialog = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
