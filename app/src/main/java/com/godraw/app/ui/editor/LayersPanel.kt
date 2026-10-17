package com.godraw.app.ui.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.engine.addLayer
import com.godraw.app.engine.addMask
import com.godraw.app.engine.clearLayerAnimation
import com.godraw.app.engine.deleteLayer
import com.godraw.app.engine.duplicateLayer
import com.godraw.app.engine.flatten
import com.godraw.app.engine.groupActiveLayer
import com.godraw.app.engine.mergeDown
import com.godraw.app.engine.moveIntoGroup
import com.godraw.app.engine.moveLayer
import com.godraw.app.engine.previewLayerOpacity
import com.godraw.app.engine.removeMask
import com.godraw.app.engine.renameLayer
import com.godraw.app.engine.selectLayer
import com.godraw.app.engine.setBlendMode
import com.godraw.app.engine.setClipping
import com.godraw.app.engine.setLayerLocked
import com.godraw.app.engine.setLayerStatic
import com.godraw.app.engine.setLayerVisible
import com.godraw.app.engine.setMaskEnabled
import com.godraw.app.engine.ungroup
import com.godraw.app.ui.common.Checkerboard
import com.godraw.app.ui.common.LockMarker
import com.godraw.app.ui.home.ConfirmDialog
import com.godraw.app.ui.home.TextInputDialog
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.model.CellKey
import com.godraw.core.model.Layer
import kotlin.math.max
import kotlin.math.roundToInt

/** Rows in display order (top first), with indentation for group children. */
private fun displayRows(layers: List<Layer>): List<Pair<Layer, Boolean>> {
    val rows = ArrayList<Pair<Layer, Boolean>>()
    for (l in layers.filter { it.parentId == null }.asReversed()) {
        rows += l to false
        if (l.isGroup && !l.collapsed) layers.filter { it.parentId == l.id }.asReversed().forEach { rows += it to true }
    }
    return rows
}

fun layerThumbnail(engine: DrawingEngine, layer: Layer, frameIndex: Int, size: Int = 96): Bitmap? {
    val doc = engine.doc
    val frame = doc.frames.getOrNull(frameIndex) ?: return null
    val cell = engine.store.get(CellKey.forLayer(layer, frame.id))
    val scale = size / max(doc.width, doc.height).toFloat()
    val w = max(1, (doc.width * scale).roundToInt())
    val h = max(1, (doc.height * scale).roundToInt())
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.scale(scale, scale)
    if (layer.isBackground && doc.backgroundColor != 0) c.drawColor(doc.backgroundColor)
    if (cell != null) c.drawBitmap(cell, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
    return bmp
}

@Composable
fun LayersPanel(vm: EditorViewModel, engine: DrawingEngine, st: EngineState, gate: FeatureGate, simple: Boolean) {
    val doc = st.doc
    val rows = displayRows(doc.layers)
    val limit = gate.limits.maxLayers
    var renaming by remember { mutableStateOf<Layer?>(null) }
    var confirmFlatten by remember { mutableStateOf(false) }
    val rowHeight = 64.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.panel_layers), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (limit != null) stringResource(R.string.layer_count_limited, doc.rasterLayerCount, limit)
                    else stringResource(R.string.layer_count, doc.rasterLayerCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!simple) {
                val groupAccess = gate.check(Feature.LAYER_GROUPS)
                Box {
                    IconButton(onClick = { engine.groupActiveLayer() }) {
                        Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.group_layer))
                    }
                    if (groupAccess is Access.Locked) LockMarker(groupAccess.requiredTier, Modifier.align(Alignment.TopEnd))
                }
            }
            IconButton(onClick = { engine.addLayer() }) { Icon(Icons.Filled.Add, stringResource(R.string.add_layer)) }
        }

        rows.forEach { (layer, indented) ->
            key(layer.id) {
                LayerRow(
                    engine = engine,
                    st = st,
                    layer = layer,
                    indented = indented,
                    rowPx = rowPx,
                    simple = simple,
                    gate = gate,
                    onRename = { renaming = layer },
                    onLocked = { a, f -> vm.locked(a, f) },
                )
            }
        }

        val active = st.activeLayer
        if (active != null) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(active.name, style = MaterialTheme.typography.titleSmall)
            var opacity by remember(active.id) { mutableFloatStateOf(active.opacity) }
            LabeledSlider(
                R.string.layer_opacity,
                opacity,
                0f..1f,
                "${(opacity * 100).roundToInt()}%",
                onFinished = { engine.endContinuousEdit("opacity") },
            ) {
                opacity = it
                engine.previewLayerOpacity(active.id, it)
            }
            BlendModePicker(active.blendMode, gate, onLocked = { vm.locked(it) }) { engine.setBlendMode(active.id, it) }
            if (!simple) {
                val clipAccess = gate.check(Feature.CLIPPING_MASKS)
                SwitchLine(R.string.clipping_mask, active.clipToBelow, (clipAccess as? Access.Locked)?.requiredTier) {
                    engine.setClipping(active.id, it)
                }
                if (doc.isAnimation) {
                    SwitchLine(R.string.static_layer, active.isStatic) { engine.setLayerStatic(active.id, it) }
                }
                val maskAccess = gate.check(Feature.LAYER_MASKS)
                if (!active.hasMask) {
                    OutlinedButton(onClick = { engine.addMask(active.id) }) {
                        Text(stringResource(R.string.add_mask))
                        if (maskAccess is Access.Locked) Box(Modifier.padding(start = 6.dp)) { LockMarker(maskAccess.requiredTier) }
                    }
                } else {
                    SwitchLine(R.string.edit_mask, st.editingMask) { engine.setEditingMask(it) }
                    SwitchLine(R.string.mask_enabled, active.maskEnabled) { engine.setMaskEnabled(active.id, it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { engine.removeMask(active.id, apply = true) }) { Text(stringResource(R.string.apply_mask)) }
                        OutlinedButton(onClick = { engine.removeMask(active.id, apply = false) }) { Text(stringResource(R.string.delete_mask)) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { engine.mergeDown(active.id) }) { Text(stringResource(R.string.merge_down)) }
                OutlinedButton(onClick = { confirmFlatten = true }) { Text(stringResource(R.string.flatten)) }
            }
        }
    }

    renaming?.let { l ->
        TextInputDialog(
            title = stringResource(R.string.rename_layer),
            initial = l.name,
            onDismiss = { renaming = null },
            onConfirm = { engine.renameLayer(l.id, it); renaming = null },
        )
    }
    if (confirmFlatten) {
        ConfirmDialog(
            title = stringResource(R.string.flatten),
            message = stringResource(R.string.flatten_confirm),
            confirm = stringResource(R.string.flatten),
            onDismiss = { confirmFlatten = false },
            onConfirm = { confirmFlatten = false; engine.flatten() },
        )
    }
}

@Composable
private fun LayerRow(
    engine: DrawingEngine,
    st: EngineState,
    layer: Layer,
    indented: Boolean,
    rowPx: Float,
    simple: Boolean,
    gate: FeatureGate,
    onRename: () -> Unit,
    onLocked: (Access.Locked, Feature?) -> Unit,
) {
    val doc = st.doc
    val active = layer.id == doc.activeLayerId
    var menu by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val frame = doc.activeFrameIndex
    val thumbKey = if (active) st.revision else 0L
    val thumb = remember(layer.id, frame, thumbKey, layer.isGroup) {
        if (layer.isGroup) null else layerThumbnail(engine, layer, frame)?.asImageBitmap()
    }
    Row(
        Modifier
            .fillMaxWidth()
            .offset { IntOffset(0, dragY.roundToInt()) }
            .padding(start = if (indented) 20.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable { engine.selectLayer(layer.id) }
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = stringResource(R.string.reorder),
            modifier = Modifier
                .size(24.dp)
                .pointerInput(layer.id) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = {
                            val delta = (dragY / rowPx).roundToInt()
                            dragY = 0f
                            if (delta != 0) {
                                val siblings = doc.layers.filter { it.parentId == layer.parentId }
                                val siblingIndex = siblings.indexOfFirst { it.id == layer.id }
                                // Display is top-first, sibling indices count from the bottom.
                                engine.moveLayer(layer.id, siblingIndex - delta)
                            }
                        },
                        onDragCancel = { dragY = 0f },
                    ) { change, amount ->
                        change.consume()
                        dragY += amount.y
                    }
                },
        )
        if (indented) Icon(Icons.Filled.SubdirectoryArrowRight, null, Modifier.size(16.dp))
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            if (layer.isGroup) {
                Icon(Icons.Filled.Folder, null, tint = MaterialTheme.colorScheme.primary)
            } else {
                Checkerboard(Modifier.size(48.dp), cell = 6f)
                if (thumb != null) Image(thumb, null, Modifier.size(48.dp))
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                layer.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(layer.opacity * 100).roundToInt()}% · ${stringResource(blendName(layer.blendMode))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (layer.clipToBelow) Icon(Icons.Filled.SubdirectoryArrowRight, stringResource(R.string.clipping_mask), Modifier.size(14.dp))
                if (layer.hasMask) Icon(Icons.Filled.TheaterComedy, stringResource(R.string.mask), Modifier.size(14.dp))
                if (layer.isStatic) Icon(Icons.Filled.PushPin, stringResource(R.string.static_layer), Modifier.size(14.dp))
            }
        }
        IconButton(onClick = { engine.setLayerVisible(layer.id, !layer.visible) }) {
            Icon(
                if (layer.visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                stringResource(if (layer.visible) R.string.hide_layer else R.string.show_layer),
            )
        }
        if (!simple) {
            IconButton(onClick = { engine.setLayerLocked(layer.id, !layer.locked) }) {
                Icon(
                    if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    stringResource(if (layer.locked) R.string.unlock_layer else R.string.lock_layer),
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.layer_options)) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = { menu = false; onRename() })
                if (!layer.isGroup) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = { menu = false; engine.duplicateLayer(layer.id) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.merge_down)) }, onClick = { menu = false; engine.mergeDown(layer.id) })
                }
                if (layer.isGroup) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.ungroup)) }, onClick = { menu = false; engine.ungroup(layer.id) })
                } else if (!simple) {
                    if (layer.parentId != null) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_group)) }, onClick = { menu = false; engine.moveIntoGroup(layer.id, null) })
                    }
                    doc.layers.filter { it.isGroup && it.id != layer.parentId }.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.move_into_group, g.name)) },
                            onClick = { menu = false; engine.moveIntoGroup(layer.id, g.id) },
                        )
                    }
                }
                if (layer.animation.isAnimated) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.clear_animation)) }, onClick = { menu = false; engine.clearLayerAnimation(layer.id) })
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; engine.deleteLayer(layer.id) },
                )
            }
        }
    }
}
