package com.godraw.app.ui.editor

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godraw.app.R
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.data.settings.ExportDestination
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.export.ExportError
import com.godraw.app.export.ExportFormat
import com.godraw.app.export.ExportOptions
import com.godraw.app.export.ExportState
import com.godraw.app.ui.common.LockMarker
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import kotlin.math.roundToInt

fun formatName(f: ExportFormat): Int = when (f) {
    ExportFormat.PNG -> R.string.format_png
    ExportFormat.PNG_TRANSPARENT -> R.string.format_png_transparent
    ExportFormat.JPG -> R.string.format_jpg
    ExportFormat.GIF -> R.string.format_gif
    ExportFormat.MP4 -> R.string.format_mp4
    ExportFormat.PNG_SEQUENCE -> R.string.format_png_sequence
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportPanel(vm: EditorViewModel, engine: DrawingEngine, st: EngineState, settings: AppSettings, gate: FeatureGate) {
    val doc = st.doc
    val context = LocalContext.current
    val exportState by vm.exportState.collectAsStateWithLifecycle()
    val formats = if (doc.isAnimation) ExportFormat.entries.toList() else listOf(ExportFormat.PNG, ExportFormat.PNG_TRANSPARENT, ExportFormat.JPG)
    var format by remember { mutableStateOf(if (doc.isAnimation) ExportFormat.MP4 else ExportFormat.PNG) }
    var scale by remember { mutableFloatStateOf(1f) }
    var quality by remember { mutableIntStateOf(settings.exportQuality) }
    var fps by remember { mutableIntStateOf(gate.clampFps(doc.fps)) }
    var includeAudio by remember { mutableStateOf(settings.exportIncludeAudio) }
    var dither by remember { mutableStateOf(false) }
    var highBitrate by remember { mutableStateOf(false) }

    fun options() = ExportOptions(
        format = format,
        scale = scale,
        quality = quality,
        fps = fps,
        includeAudio = includeAudio,
        dither = dither,
        highBitrate = highBitrate,
        frameIndex = doc.activeFrameIndex,
    )

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(format.mime)) { uri ->
        if (uri != null) vm.export(options(), uri)
    }

    DisposableEffect(Unit) { onDispose { vm.resetExport() } }

    val (outW, outH) = vm.exporter.outputSize(doc, options(), gate)
    val requestedW = (doc.width * scale).roundToInt()
    val limited = outW < requestedW - 2

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.export), style = MaterialTheme.typography.titleMedium)

        when (val s = exportState) {
            is ExportState.Running -> {
                Text(stringResource(R.string.exporting_progress, (s.progress * 100).roundToInt()))
                LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { vm.cancelExport() }) { Text(stringResource(R.string.cancel)) }
                return@Column
            }
            is ExportState.Done -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.export_done, s.displayName), Modifier.padding(start = 8.dp))
                }
                Text(stringResource(R.string.export_size, s.sizeBytes / 1024), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        val intent = Intent.createChooser(vm.exporter.shareIntent(s), context.getString(R.string.share))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.Share, null)
                        Text(stringResource(R.string.share), Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(onClick = { vm.resetExport() }) { Text(stringResource(R.string.export_another)) }
                }
                return@Column
            }
            is ExportState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        stringResource(
                            when (s.error) {
                                ExportError.NOTHING_TO_EXPORT -> R.string.export_error_nothing
                                ExportError.ENCODER_UNAVAILABLE -> R.string.export_error_encoder
                                ExportError.STORAGE -> R.string.export_error_storage
                                ExportError.FEATURE_LOCKED -> R.string.export_error_locked
                                ExportError.UNKNOWN -> R.string.export_error_unknown
                            },
                        ),
                        Modifier.padding(start = 8.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                OutlinedButton(onClick = { vm.resetExport() }) { Text(stringResource(R.string.try_again)) }
                return@Column
            }
            ExportState.Idle -> Unit
        }

        Text(stringResource(R.string.export_format), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            formats.forEach { f ->
                val locked = f.feature?.let { gate.check(it) as? Access.Locked }
                FilterChip(
                    selected = format == f,
                    onClick = { if (locked != null) vm.locked(locked, f.feature) else format = f },
                    label = { Text(stringResource(formatName(f))) },
                    trailingIcon = if (locked != null) { { LockMarker(locked.requiredTier) } } else null,
                )
            }
        }
        if (doc.isAnimation && !format.animated) {
            Text(stringResource(R.string.export_current_frame, doc.activeFrameIndex + 1), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text(stringResource(R.string.export_resolution), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0.25f, 0.5f, 1f, 2f).forEach { s ->
                FilterChip(selected = scale == s, onClick = { scale = s }, label = { Text("${(s * 100).roundToInt()}%") })
            }
        }
        Text(stringResource(R.string.export_output_size, outW, outH), style = MaterialTheme.typography.bodyMedium)
        if (limited) {
            Text(
                stringResource(R.string.export_limited, gate.limits.maxExportShortEdge),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        if (format == ExportFormat.JPG) {
            LabeledSlider(R.string.export_quality, quality.toFloat(), 10f..100f, "$quality%") { quality = it.roundToInt() }
        }
        if (format.animated) {
            LabeledSlider(R.string.frame_rate, fps.toFloat(), 1f..gate.limits.maxFps.toFloat(), stringResource(R.string.fps_value, fps)) {
                fps = it.roundToInt()
            }
        }
        if (format == ExportFormat.MP4) {
            if (doc.audioClips.isNotEmpty()) SwitchLine(R.string.export_include_audio, includeAudio) { includeAudio = it }
            val bitrateLocked = gate.check(Feature.HIGH_BITRATE_VIDEO) as? Access.Locked
            SwitchLine(R.string.export_high_bitrate, highBitrate, bitrateLocked?.requiredTier) {
                if (bitrateLocked != null) vm.locked(bitrateLocked, Feature.HIGH_BITRATE_VIDEO) else highBitrate = it
            }
        }
        if (format == ExportFormat.GIF) {
            val ditherLocked = gate.check(Feature.GIF_DITHERING) as? Access.Locked
            SwitchLine(R.string.export_dither, dither, ditherLocked?.requiredTier) {
                if (ditherLocked != null) vm.locked(ditherLocked, Feature.GIF_DITHERING) else dither = it
            }
        }

        Text(stringResource(R.string.no_watermark_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (settings.exportDestination == ExportDestination.ASK) {
                        saveAs.launch(sanitizeFileName(doc.name) + "." + format.extension)
                    } else {
                        vm.export(options(), null)
                    }
                },
                modifier = Modifier.height(48.dp),
            ) {
                Text(stringResource(if (settings.exportDestination == ExportDestination.ASK) R.string.export_choose_location else R.string.export_to_gallery))
            }
            OutlinedButton(
                onClick = { saveAs.launch(sanitizeFileName(doc.name) + "." + format.extension) },
                modifier = Modifier.height(48.dp),
            ) { Text(stringResource(R.string.export_save_as)) }
        }
    }
}

private fun sanitizeFileName(name: String): String =
    name.replace(Regex("[^\\p{L}\\p{N}_ -]"), "").trim().ifEmpty { "GoDraw" }.take(60)
