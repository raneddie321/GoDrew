package com.godraw.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.engine.removeAudioClip
import com.godraw.app.engine.updateAudioClip
import com.godraw.app.ui.common.LockMarker
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.model.AudioClip
import com.godraw.core.timeline.PlaybackClock
import kotlin.math.max
import kotlin.math.roundToLong

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d.%d".format(s / 60, s % 60, (ms % 1000) / 100)
}

@Composable
fun AudioPanel(vm: EditorViewModel, engine: DrawingEngine, st: EngineState, gate: FeatureGate) {
    val doc = st.doc
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importAudio(uri) }
    val durationMs = PlaybackClock(doc.frames, doc.fps).totalDurationMs
    val multiLocked = gate.check(Feature.MULTIPLE_AUDIO_CLIPS) as? Access.Locked

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.panel_audio), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.audio_hint, formatMs(durationMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { launcher.launch(arrayOf("audio/*")) }) {
                Icon(Icons.Filled.MusicNote, null)
                Text(stringResource(R.string.import_audio), Modifier.padding(start = 6.dp))
            }
            if (doc.audioClips.isNotEmpty() && multiLocked != null) {
                LockMarker(multiLocked.requiredTier, Modifier.padding(start = 8.dp))
            }
        }
        doc.audioClips.forEach { clip -> ClipEditor(engine, clip, max(durationMs, clip.durationMs)) }
    }
}

@Composable
private fun ClipEditor(engine: DrawingEngine, clip: AudioClip, timelineMs: Long) {
    var start by remember(clip) { mutableFloatStateOf(clip.startMs.toFloat()) }
    var trim by remember(clip) { mutableStateOf(clip.trimStartMs.toFloat()..clip.effectiveEndMs.toFloat()) }
    var volume by remember(clip) { mutableFloatStateOf(clip.volume) }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, null)
                Text(
                    clip.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                )
                IconButton(onClick = { engine.updateAudioClip(clip.copy(muted = !clip.muted)) }) {
                    Icon(
                        if (clip.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        stringResource(if (clip.muted) R.string.unmute else R.string.mute),
                    )
                }
                IconButton(onClick = { engine.removeAudioClip(clip.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
            LabeledSlider(
                R.string.audio_start,
                start,
                0f..timelineMs.toFloat().coerceAtLeast(1f),
                formatMs(start.roundToLong()),
                onFinished = { engine.updateAudioClip(clip.copy(startMs = start.roundToLong())) },
            ) { start = it }
            Text(stringResource(R.string.audio_trim, formatMs(trim.start.roundToLong()), formatMs(trim.endInclusive.roundToLong())), style = MaterialTheme.typography.bodyMedium)
            RangeSlider(
                value = trim,
                onValueChange = { trim = it },
                valueRange = 0f..clip.durationMs.toFloat().coerceAtLeast(1f),
                onValueChangeFinished = {
                    engine.updateAudioClip(clip.copy(trimStartMs = trim.start.roundToLong(), trimEndMs = trim.endInclusive.roundToLong()))
                },
            )
            LabeledSlider(
                R.string.audio_volume,
                volume,
                0f..1f,
                "${(volume * 100).toInt()}%",
                onFinished = { engine.updateAudioClip(clip.copy(volume = volume)) },
            ) { volume = it }
        }
    }
}
