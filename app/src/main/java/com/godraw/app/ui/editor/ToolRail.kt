package com.godraw.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.engine.TextFont
import com.godraw.app.engine.TextSpec
import com.godraw.app.engine.ToolType
import com.godraw.app.ui.common.LockMarker
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

fun railTools(settings: AppSettings, gate: FeatureGate, simple: Boolean): List<ToolType> {
    val custom = settings.quickTools.mapNotNull { ToolType.byId(it) }
    if (custom.isNotEmpty() && gate.check(Feature.TOOLBAR_CUSTOMIZATION).isGranted) return custom
    return if (simple) ToolType.simpleModeTools else ToolType.advancedModeTools
}

@Composable
fun ToolRail(
    vm: EditorViewModel,
    engine: DrawingEngine,
    st: EngineState,
    settings: AppSettings,
    gate: FeatureGate,
    simple: Boolean,
) {
    val tools = railTools(settings, gate, simple).let { list ->
        if (!st.doc.isAnimation) list.filter { it != ToolType.ANIMATE } else list
    }
    val buttonSize = if (simple) 56.dp else 46.dp
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, modifier = Modifier.fillMaxHeight()) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tools.forEach { tool ->
                val selected = st.tool == tool
                val lockedTier = tool.requiredFeature?.let { (gate.check(it) as? Access.Locked)?.requiredTier }
                Box(
                    Modifier
                        .size(buttonSize)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable {
                            if (selected && (tool.isPainting || tool.isShape || tool == ToolType.FILL)) {
                                vm.setPanel(EditorPanel.BRUSHES)
                            } else {
                                engine.setTool(tool)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            toolIcon(tool),
                            contentDescription = stringResource(toolName(tool)),
                            tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(if (simple) 26.dp else 22.dp),
                        )
                        if (simple) {
                            Text(
                                stringResource(toolName(tool)),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (lockedTier != null) {
                        LockMarker(lockedTier, Modifier.align(Alignment.TopEnd).padding(2.dp))
                    }
                }
            }
        }
    }
}

/** Vertical size and opacity sliders next to the canvas, like a physical brush rest. */
@Composable
fun SideSliders(engine: DrawingEngine, st: EngineState, modifier: Modifier = Modifier) {
    val tool = if (st.tool.isPainting) st.tool else ToolType.BRUSH
    val brush = st.brushes[tool] ?: com.godraw.app.engine.defaultBrushFor(tool)
    Surface(modifier = modifier, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), tonalElevation = 4.dp) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${brush.size.roundToInt()}", style = MaterialTheme.typography.labelSmall)
            VerticalSlider(
                value = sizeToSlider(brush.size),
                onChange = { engine.setBrush(tool, brush.copy(size = sliderToSize(it))) },
                modifier = Modifier.height(150.dp),
            )
            Box(Modifier.height(10.dp))
            VerticalSlider(
                value = brush.opacity,
                onChange = { engine.setBrush(tool, brush.copy(opacity = it.coerceIn(0.01f, 1f))) },
                modifier = Modifier.height(110.dp),
            )
            Text("${(brush.opacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private const val MIN_SIZE = 1f
private const val MAX_SIZE = 500f
fun sizeToSlider(size: Float): Float = (ln(size.coerceIn(MIN_SIZE, MAX_SIZE) / MIN_SIZE) / ln(MAX_SIZE / MIN_SIZE)).toFloat()
fun sliderToSize(v: Float): Float = (MIN_SIZE * exp(v.coerceIn(0f, 1f) * ln(MAX_SIZE / MIN_SIZE))).toFloat()

@Composable
fun VerticalSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    var heightPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val thumb = 18.dp
    val thumbPx = with(density) { thumb.toPx() }
    Box(
        modifier
            .width(32.dp)
            .onSizeChanged { heightPx = it.height.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { o -> onChange(1f - (o.y / heightPx).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ -> onChange(1f - (change.position.y / heightPx).coerceIn(0f, 1f)) }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .width(6.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        val y = ((1f - value.coerceIn(0f, 1f)) * (heightPx - thumbPx)).roundToInt()
        Box(
            Modifier
                .offset { IntOffset(0, y) }
                .size(thumb)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

@Composable
fun TextToolDialog(onDismiss: () -> Unit, onConfirm: (TextSpec) -> Unit) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("96") }
    var bold by remember { mutableStateOf(false) }
    var italic by remember { mutableStateOf(false) }
    var font by remember { mutableStateOf(TextFont.SANS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tool_text)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) text = it },
                    label = { Text(stringResource(R.string.text_content)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                OutlinedTextField(
                    value = size,
                    onValueChange = { v -> if (v.length <= 4 && v.all(Char::isDigit)) size = v },
                    label = { Text(stringResource(R.string.text_size)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextFont.entries.forEach { f ->
                        FilterChip(
                            selected = font == f,
                            onClick = { font = f },
                            label = {
                                Text(
                                    stringResource(
                                        when (f) {
                                            TextFont.SANS -> R.string.font_sans
                                            TextFont.SERIF -> R.string.font_serif
                                            TextFont.MONO -> R.string.font_mono
                                            TextFont.HANDWRITING -> R.string.font_handwriting
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = bold, onCheckedChange = { bold = it })
                    Text(stringResource(R.string.text_bold))
                    Checkbox(checked = italic, onCheckedChange = { italic = it })
                    Text(stringResource(R.string.text_italic))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(TextSpec(text, (size.toIntOrNull() ?: 96).toFloat(), bold, italic, font)) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.place_text)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Pro: choose which tools appear on the quick toolbar. */
@Composable
fun ToolbarCustomizePanel(vm: EditorViewModel, settings: AppSettings, gate: FeatureGate) {
    val allowed = gate.check(Feature.TOOLBAR_CUSTOMIZATION)
    val current = settings.quickTools.mapNotNull { ToolType.byId(it) }.ifEmpty { ToolType.advancedModeTools }
    var selection by remember(settings.quickTools) { mutableStateOf(current) }
    Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.customize_toolbar), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (allowed is Access.Locked) LockMarker(allowed.requiredTier)
        }
        Text(stringResource(R.string.customize_toolbar_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ToolType.entries.forEach { tool ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        selection = if (tool in selection) selection - tool else selection + tool
                    }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = tool in selection, onCheckedChange = { selection = if (it) selection + tool else selection - tool })
                Icon(toolIcon(tool), null, Modifier.size(20.dp))
                Text(stringResource(toolName(tool)), Modifier.padding(start = 8.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { vm.setQuickTools(emptyList()) }) { Text(stringResource(R.string.reset)) }
            TextButton(onClick = { vm.setQuickTools(ToolType.entries.filter { it in selection }) }, enabled = selection.isNotEmpty()) {
                Text(stringResource(R.string.save))
            }
        }
    }
}

