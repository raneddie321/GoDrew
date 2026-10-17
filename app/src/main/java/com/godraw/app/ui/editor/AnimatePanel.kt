package com.godraw.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.engine.ToolType
import com.godraw.app.engine.applyTransition
import com.godraw.app.engine.clearLayerAnimation
import com.godraw.app.engine.copyAnimation
import com.godraw.app.engine.keyAllProperties
import com.godraw.app.engine.pasteAnimation
import com.godraw.app.engine.removeKeyframe
import com.godraw.app.engine.setActiveFrame
import com.godraw.app.engine.setKeyframe
import com.godraw.app.engine.setKeyframeEasing
import com.godraw.app.engine.setMotionPathMode
import com.godraw.app.ui.common.LockMarker
import com.godraw.core.animation.Easing
import com.godraw.core.animation.EasingType
import com.godraw.core.animation.KeyframeSampler
import com.godraw.core.animation.TransitionPreset
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.model.MotionPathMode
import com.godraw.core.model.TransformProperty
import kotlin.math.roundToInt

private fun propertyRange(p: TransformProperty, w: Int, h: Int): ClosedFloatingPointRange<Float> = when (p) {
    TransformProperty.POSITION_X -> -w.toFloat()..w.toFloat()
    TransformProperty.POSITION_Y -> -h.toFloat()..h.toFloat()
    TransformProperty.ROTATION -> -720f..720f
    TransformProperty.SCALE -> 0.05f..5f
    TransformProperty.OPACITY -> 0f..1f
}

private fun formatValue(p: TransformProperty, v: Float): String = when (p) {
    TransformProperty.ROTATION -> "${v.roundToInt()}°"
    TransformProperty.SCALE -> "${(v * 100).roundToInt()}%"
    TransformProperty.OPACITY -> "${(v * 100).roundToInt()}%"
    else -> "${v.roundToInt()} px"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimatePanel(vm: EditorViewModel, engine: DrawingEngine, st: EngineState, gate: FeatureGate, simple: Boolean) {
    val doc = st.doc
    val layer = st.activeLayer ?: return
    val frame = doc.activeFrameIndex
    val t = KeyframeSampler.sample(layer.animation, frame.toFloat())
    var transitionLength by remember { mutableIntStateOf((doc.fps / 2).coerceAtLeast(2)) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.panel_animate), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.animate_layer_hint, layer.name, frame + 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { engine.setTool(ToolType.ANIMATE); vm.setPanel(EditorPanel.NONE) }) {
                Text(stringResource(R.string.animate_on_canvas))
            }
            OutlinedButton(onClick = { engine.keyAllProperties(layer.id) }) { Text(stringResource(R.string.key_all)) }
        }

        TransformProperty.entries.forEach { p ->
            val value = when (p) {
                TransformProperty.POSITION_X -> t.x
                TransformProperty.POSITION_Y -> t.y
                TransformProperty.ROTATION -> t.rotation
                TransformProperty.SCALE -> t.scale
                TransformProperty.OPACITY -> t.opacity
            }
            val keyHere = layer.animation.track(p).firstOrNull { it.frame == frame }
            var local by remember(layer.id, p, frame, value) { mutableFloatStateOf(value) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Diamond,
                    null,
                    tint = if (keyHere != null) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(14.dp),
                )
                Text(stringResource(propertyName(p)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp).weight(1f))
                Text(formatValue(p, local), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (keyHere != null) {
                    IconButton(onClick = { engine.removeKeyframe(layer.id, p, frame) }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.remove_keyframe))
                    }
                }
            }
            androidx.compose.material3.Slider(
                value = local.coerceIn(propertyRange(p, doc.width, doc.height)),
                onValueChange = { local = it },
                onValueChangeFinished = { engine.setKeyframe(layer.id, p, local) },
                valueRange = propertyRange(p, doc.width, doc.height),
            )
            if (keyHere != null && !simple) {
                EasingPicker(keyHere.easing, gate, onLocked = { a, f -> vm.locked(a, f) }) { easing ->
                    val bezier = if (easing == EasingType.CUSTOM_BEZIER) keyHere.bezier ?: listOf(0.42f, 0f, 0.58f, 1f) else null
                    engine.setKeyframeEasing(layer.id, p, frame, easing, bezier)
                }
                if (keyHere.easing == EasingType.CUSTOM_BEZIER) {
                    BezierEditor(keyHere.bezier ?: listOf(0.42f, 0f, 0.58f, 1f)) { b ->
                        engine.setKeyframeEasing(layer.id, p, frame, EasingType.CUSTOM_BEZIER, b)
                    }
                }
            }
            val frames = layer.animation.track(p).map { it.frame }
            if (frames.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    frames.forEach { f ->
                        AssistChip(onClick = { engine.setActiveFrame(f) }, label = { Text(stringResource(R.string.frame_short, f + 1)) })
                    }
                }
            }
        }

        if (!simple) {
            HorizontalDivider()
            Text(stringResource(R.string.motion_path), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = layer.animation.pathMode == MotionPathMode.LINEAR,
                    onClick = { engine.setMotionPathMode(layer.id, MotionPathMode.LINEAR) },
                    label = { Text(stringResource(R.string.path_linear)) },
                )
                FilterChip(
                    selected = layer.animation.pathMode == MotionPathMode.SMOOTH,
                    onClick = { engine.setMotionPathMode(layer.id, MotionPathMode.SMOOTH) },
                    label = { Text(stringResource(R.string.path_smooth)) },
                )
                (gate.check(Feature.SMOOTH_MOTION_PATHS) as? Access.Locked)?.let { LockMarker(it.requiredTier) }
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.transitions), style = MaterialTheme.typography.titleSmall)
        LabeledSlider(R.string.transition_length, transitionLength.toFloat(), 2f..60f, stringResource(R.string.frames_value, transitionLength)) {
            transitionLength = it.roundToInt()
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TransitionPreset.entries.forEach { preset ->
                val locked = preset.requiredFeature?.let { gate.check(it) as? Access.Locked }
                AssistChip(
                    onClick = { engine.applyTransition(layer.id, preset, transitionLength) },
                    label = { Text(stringResource(transitionName(preset))) },
                    trailingIcon = if (locked != null) { { LockMarker(locked.requiredTier) } } else null,
                )
            }
        }

        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!simple) {
                val copyAccess = gate.check(Feature.KEYFRAME_COPY_PASTE)
                OutlinedButton(onClick = { vm.copyAnimation(engine.copyAnimation(layer.id)) }) {
                    Text(stringResource(R.string.copy_keyframes))
                    if (copyAccess is Access.Locked) Box(Modifier.padding(start = 6.dp)) { LockMarker(copyAccess.requiredTier) }
                }
                OutlinedButton(
                    onClick = { vm.animationClipboardValue?.let { engine.pasteAnimation(layer.id, it) } },
                    enabled = vm.animationClipboardValue != null,
                ) { Text(stringResource(R.string.paste_keyframes)) }
            }
            if (layer.animation.isAnimated) {
                TextButton(onClick = { engine.clearLayerAnimation(layer.id) }) { Text(stringResource(R.string.clear_animation)) }
            }
        }
    }
}

@Composable
private fun EasingPicker(current: EasingType, gate: FeatureGate, onLocked: (Access.Locked, Feature?) -> Unit, onSelect: (EasingType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.easing), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Box {
            TextButton(onClick = { open = true }) { Text(stringResource(easingName(current))) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                EasingType.entries.forEach { e ->
                    val access = e.requiredFeature?.let { gate.check(it) as? Access.Locked }
                    DropdownMenuItem(
                        text = { Text(stringResource(easingName(e))) },
                        leadingIcon = { EasingCurve(e) },
                        trailingIcon = { if (access != null) LockMarker(access.requiredTier) },
                        onClick = {
                            open = false
                            if (access != null) onLocked(access, e.requiredFeature) else onSelect(e)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EasingCurve(type: EasingType, bezier: List<Float>? = null) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(28.dp)) {
        val path = Path()
        val steps = 24
        for (i in 0..steps) {
            val x = i / steps.toFloat()
            val y = Easing.apply(type, x, bezier ?: listOf(0.42f, 0f, 0.58f, 1f))
            val px = x * size.width
            val py = size.height - y.coerceIn(-0.3f, 1.3f) / 1.6f * size.height - size.height * 0.19f
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 2.5f))
    }
}

@Composable
private fun BezierEditor(bezier: List<Float>, onChange: (List<Float>) -> Unit) {
    var b by remember(bezier) { mutableStateOf(bezier) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(64.dp)) {
                val path = Path()
                for (i in 0..32) {
                    val x = i / 32f
                    val y = Easing.cubicBezier(b[0], b[1], b[2], b[3], x)
                    val p = Offset(x * size.width, size.height - y.coerceIn(-0.5f, 1.5f) / 2f * size.height - size.height * 0.25f)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                drawPath(path, androidx.compose.ui.graphics.Color(0xFF7B2FF7), style = Stroke(width = 3f))
            }
            Text(
                "(%.2f, %.2f, %.2f, %.2f)".format(b[0], b[1], b[2], b[3]),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        listOf("x1", "y1", "x2", "y2").forEachIndexed { i, label ->
            val range = if (i % 2 == 0) 0f..1f else -1f..2f
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 6.dp))
                androidx.compose.material3.Slider(
                    value = b[i],
                    onValueChange = { v -> b = b.toMutableList().also { it[i] = v } },
                    onValueChangeFinished = { onChange(b) },
                    valueRange = range,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
