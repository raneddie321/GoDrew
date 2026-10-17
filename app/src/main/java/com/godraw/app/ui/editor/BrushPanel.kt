package com.godraw.app.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.engine.ToolType
import com.godraw.app.ui.common.LockMarker
import com.godraw.app.ui.home.TextInputDialog
import com.godraw.core.brush.BrushCatalog
import com.godraw.core.brush.BrushFamily
import com.godraw.core.brush.BrushPreset
import com.godraw.core.brush.BrushTip
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.model.LayerBlendMode
import kotlin.math.roundToInt

@Composable
fun BrushPanel(
    vm: EditorViewModel,
    engine: DrawingEngine,
    st: EngineState,
    settings: AppSettings,
    gate: FeatureGate,
    simple: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(toolName(st.tool)), style = MaterialTheme.typography.titleMedium)
        when {
            st.tool.isPainting -> PaintingOptions(vm, engine, st, settings, gate, simple)
            st.tool.isShape -> ShapeOptions(engine, st)
            st.tool == ToolType.FILL -> FillOptions(engine, st)
            else -> Text(
                stringResource(
                    when (st.tool) {
                        ToolType.TEXT -> R.string.hint_text_tool
                        ToolType.SELECT_RECT, ToolType.SELECT_LASSO -> R.string.hint_select_tool
                        ToolType.TRANSFORM -> R.string.hint_transform_tool
                        ToolType.ANIMATE -> R.string.hint_animate_tool
                        ToolType.EYEDROPPER -> R.string.hint_eyedropper_tool
                        else -> R.string.hint_hand_tool
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaintingOptions(
    vm: EditorViewModel,
    engine: DrawingEngine,
    st: EngineState,
    settings: AppSettings,
    gate: FeatureGate,
    simple: Boolean,
) {
    val tool = st.tool
    val brush = st.brushes[tool] ?: com.godraw.app.engine.defaultBrushFor(tool)
    var family by remember { mutableStateOf<BrushFamily?>(brush.family.takeIf { !brush.isCustom }) }
    var showCustom by remember { mutableStateOf(brush.isCustom) }
    var expanded by remember { mutableStateOf(!simple) }
    var savingBrush by remember { mutableStateOf(false) }
    val previewColor = if (tool == ToolType.ERASER) 0xFF555A66.toInt() else st.color

    fun choose(preset: BrushPreset, catalogIndex: Int) {
        val access = gate.canUseBrush(catalogIndex, preset.isCustom)
        if (access is Access.Locked) {
            vm.locked(access, if (preset.isCustom) Feature.CUSTOM_BRUSHES else null)
        } else {
            engine.setBrush(tool, preset)
        }
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        item {
            FilterChip(selected = family == null && !showCustom, onClick = { family = null; showCustom = false }, label = { Text(stringResource(R.string.all)) })
        }
        items(BrushFamily.entries) { f ->
            FilterChip(selected = family == f && !showCustom, onClick = { family = f; showCustom = false }, label = { Text(stringResource(familyName(f))) })
        }
        item {
            FilterChip(selected = showCustom, onClick = { showCustom = true }, label = { Text(stringResource(R.string.custom_brushes)) })
        }
    }

    val entries: List<Pair<BrushPreset, Int>> = if (showCustom) {
        settings.customBrushes.map { it to -1 }
    } else {
        BrushCatalog.all.mapIndexed { i, b -> b to i }.filter { family == null || it.first.family == family }
    }
    if (showCustom && entries.isEmpty()) {
        Text(stringResource(R.string.no_custom_brushes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(entries, key = { _, e -> e.first.id }) { _, (preset, index) ->
                val selected = preset.id == brush.id
                val lockedTier = (gate.canUseBrush(index.coerceAtLeast(0), preset.isCustom) as? Access.Locked)?.requiredTier
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            if (selected) 2.dp else 0.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { choose(preset, index) }
                        .padding(6.dp),
                ) {
                    Column {
                        val bmp = remember(preset, previewColor) { BrushPreviews.preview(preset, previewColor).asImageBitmap() }
                        Image(bmp, contentDescription = null, modifier = Modifier.fillMaxWidth().height(40.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                preset.customName ?: (stringResource(familyName(preset.family)) + " " + preset.variant),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (preset.isCustom) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    modifier = Modifier.size(16.dp).clickable { vm.deleteCustomBrush(preset.id) },
                                )
                            }
                        }
                    }
                    if (lockedTier != null) LockMarker(lockedTier, Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { expanded = !expanded }) {
        Text(stringResource(R.string.brush_settings), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
    }
    LabeledSlider(R.string.brush_size, sizeToSlider(brush.size), 0f..1f, "${brush.size.roundToInt()} px") {
        engine.setBrush(tool, brush.copy(size = sliderToSize(it)))
    }
    LabeledSlider(R.string.brush_opacity, brush.opacity, 0.01f..1f, "${(brush.opacity * 100).roundToInt()}%") {
        engine.setBrush(tool, brush.copy(opacity = it))
    }
    LabeledSlider(R.string.stabilization, st.stabilization, 0f..1f, "${(st.stabilization * 100).roundToInt()}%") {
        engine.setStabilization(it)
    }
    if (expanded) {
        LabeledSlider(R.string.brush_flow, brush.flow, 0.01f..1f, "${(brush.flow * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(flow = it)) }
        LabeledSlider(R.string.brush_hardness, brush.hardness, 0f..1f, "${(brush.hardness * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(hardness = it)) }
        LabeledSlider(R.string.brush_spacing, brush.spacing, 0.02f..1f, "${(brush.spacing * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(spacing = it)) }
        LabeledSlider(R.string.pressure_size, brush.pressureSize, 0f..1f, "${(brush.pressureSize * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(pressureSize = it)) }
        LabeledSlider(R.string.pressure_opacity, brush.pressureOpacity, 0f..1f, "${(brush.pressureOpacity * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(pressureOpacity = it)) }
        Text(stringResource(R.string.brush_tip), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BrushTip.entries.forEach { tip ->
                FilterChip(selected = brush.tip == tip, onClick = { engine.setBrush(tool, brush.copy(tip = tip)) }, label = { Text(stringResource(tipName(tip))) })
            }
        }
        if (brush.tip == BrushTip.FLAT) {
            LabeledSlider(R.string.brush_angle, brush.angle, 0f..180f, "${brush.angle.roundToInt()}°") { engine.setBrush(tool, brush.copy(angle = it)) }
            LabeledSlider(R.string.brush_roundness, brush.roundness, 0.05f..1f, "${(brush.roundness * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(roundness = it)) }
        }
        LabeledSlider(R.string.brush_texture, brush.texture, 0f..1f, "${(brush.texture * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(texture = it)) }
        LabeledSlider(R.string.brush_scatter, brush.scatter, 0f..1f, "${(brush.scatter * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(scatter = it)) }
        LabeledSlider(R.string.brush_wet_edges, brush.wetEdges, 0f..1f, "${(brush.wetEdges * 100).roundToInt()}%") { engine.setBrush(tool, brush.copy(wetEdges = it)) }
        if (tool != ToolType.ERASER) {
            BlendModePicker(brush.blendMode, gate, onLocked = { vm.locked(it) }) { engine.setBrush(tool, brush.copy(blendMode = it)) }
        }
        val customAccess = gate.check(Feature.CUSTOM_BRUSHES)
        OutlinedButton(onClick = {
            if (customAccess is Access.Locked) vm.locked(customAccess, Feature.CUSTOM_BRUSHES) else savingBrush = true
        }) {
            Text(stringResource(R.string.save_custom_brush))
            if (customAccess is Access.Locked) {
                Box(Modifier.padding(start = 6.dp)) { LockMarker(customAccess.requiredTier) }
            }
        }
    }
    if (savingBrush) {
        TextInputDialog(
            title = stringResource(R.string.save_custom_brush),
            initial = stringResource(familyName(brush.family)),
            onDismiss = { savingBrush = false },
            onConfirm = { name ->
                savingBrush = false
                vm.saveCustomBrush(brush, name)
            },
        )
    }
}

@Composable
private fun ShapeOptions(engine: DrawingEngine, st: EngineState) {
    val shape = st.shape
    val brush = st.brushes[ToolType.BRUSH] ?: com.godraw.app.engine.defaultBrushFor(ToolType.BRUSH)
    LabeledSlider(R.string.stroke_width, sizeToSlider(brush.size), 0f..1f, "${brush.size.roundToInt()} px") {
        engine.setBrush(ToolType.BRUSH, brush.copy(size = sliderToSize(it)))
    }
    if (st.tool != ToolType.LINE) {
        SwitchLine(R.string.shape_filled, shape.filled) { engine.setShapeSettings(shape.copy(filled = it)) }
    }
    if (st.tool == ToolType.POLYGON) {
        LabeledSlider(R.string.shape_sides, shape.sides.toFloat(), 3f..12f, "${shape.sides}", steps = 8) {
            engine.setShapeSettings(shape.copy(sides = it.roundToInt()))
        }
        SwitchLine(R.string.shape_star, shape.star) { engine.setShapeSettings(shape.copy(star = it)) }
    }
}

@Composable
private fun FillOptions(engine: DrawingEngine, st: EngineState) {
    val f = st.fill
    LabeledSlider(R.string.fill_tolerance, f.tolerance.toFloat(), 0f..255f, "${f.tolerance}") {
        engine.setFillSettings(f.copy(tolerance = it.roundToInt()))
    }
    LabeledSlider(R.string.fill_expand, f.expand.toFloat(), 0f..6f, "${f.expand} px", steps = 5) {
        engine.setFillSettings(f.copy(expand = it.roundToInt()))
    }
    SwitchLine(R.string.fill_sample_all, f.sampleAllLayers) { engine.setFillSettings(f.copy(sampleAllLayers = it)) }
}

@Composable
fun LabeledSlider(
    label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    valueText: String,
    steps: Int = 0,
    onFinished: (() -> Unit)? = null,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            onValueChangeFinished = onFinished,
        )
    }
}

@Composable
fun SwitchLine(label: Int, checked: Boolean, enabledLockTier: com.godraw.core.entitlement.Tier? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (enabledLockTier != null) Box(Modifier.padding(end = 8.dp)) { LockMarker(enabledLockTier) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun BlendModePicker(current: LayerBlendMode, gate: FeatureGate, onLocked: (Access.Locked) -> Unit, onSelect: (LayerBlendMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.blend_mode), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(stringResource(blendName(current))) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                LayerBlendMode.entries.forEach { mode ->
                    val access = gate.canUseBlendMode(mode)
                    DropdownMenuItem(
                        text = { Text(stringResource(blendName(mode))) },
                        trailingIcon = { if (access is Access.Locked) LockMarker(access.requiredTier) },
                        onClick = {
                            open = false
                            if (access is Access.Locked) onLocked(access) else onSelect(mode)
                        },
                    )
                }
            }
        }
    }
}

@Suppress("unused")
@Composable
private fun DeleteIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
}
