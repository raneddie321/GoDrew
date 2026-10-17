package com.godraw.app.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.ui.common.LockMarker
import com.godraw.core.effects.EffectType
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.FeatureGate
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EffectsPanel(vm: EditorViewModel, engine: DrawingEngine, st: EngineState, gate: FeatureGate) {
    val effects = vm.effects ?: return
    var selected by remember { mutableStateOf<EffectType?>(null) }
    val params = remember { mutableStateMapOf<String, Float>() }
    var allFrames by remember { mutableStateOf(false) }
    val layer = st.activeLayer
    val limit = gate.limits.effectCount

    DisposableEffect(Unit) { onDispose { if (engine.effectPreview != null) effects.cancel() } }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.panel_effects), style = MaterialTheme.typography.titleMedium)
        Text(
            if (limit != null) stringResource(R.string.effects_available, limit, EffectType.entries.size)
            else stringResource(R.string.effects_all_available, EffectType.entries.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (layer == null || layer.isGroup) {
            Text(stringResource(R.string.msg_not_raster), style = MaterialTheme.typography.bodySmall)
            return@Column
        }
        if (st.hasSelection) {
            Text(stringResource(R.string.effects_selection_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EffectType.entries.forEach { type ->
                val access = gate.canUseEffect(type.ordinal)
                val isSel = selected == type
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(if (isSel) 2.dp else 0.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isSel) 1f else 0f), RoundedCornerShape(12.dp))
                        .clickable {
                            if (access is Access.Locked) {
                                vm.locked(access)
                            } else {
                                selected = type
                                params.clear()
                                params.putAll(type.defaults())
                                effects.preview(type, params.toMap())
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(effectName(type)), style = MaterialTheme.typography.labelLarge)
                        if (access is Access.Locked) Box(Modifier.padding(start = 6.dp)) { LockMarker(access.requiredTier) }
                    }
                }
            }
        }
        val type = selected
        if (type != null) {
            Text(stringResource(effectName(type)), style = MaterialTheme.typography.titleSmall)
            type.params.forEach { p ->
                val v = params[p.key] ?: p.default
                LabeledSlider(
                    effectParamName(p.key),
                    v,
                    p.min..p.max,
                    if (p.max - p.min > 10) "${v.roundToInt()}" else "%.2f".format(v),
                    onFinished = { effects.preview(type, params.toMap()) },
                ) { params[p.key] = it }
            }
            if (st.doc.isAnimation && !layer.isStatic) {
                SwitchLine(R.string.apply_all_frames, allFrames) { allFrames = it }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { selected = null; effects.cancel() }) { Text(stringResource(R.string.cancel)) }
                Button(onClick = {
                    effects.apply(allFrames)
                    selected = null
                }, enabled = !st.busy) { Text(stringResource(R.string.apply)) }
            }
        }
    }
}
