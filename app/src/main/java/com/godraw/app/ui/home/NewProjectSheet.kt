package com.godraw.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.ui.common.Checkerboard
import com.godraw.core.model.CanvasPreset
import com.godraw.core.model.ProjectFactory
import com.godraw.core.model.ProjectTemplate
import com.godraw.core.model.ProjectType
import kotlin.math.roundToInt

/** Canvases above this many pixels per layer are refused to protect device memory. */
private const val MAX_PIXELS = 4096L * 4096L

fun presetName(preset: CanvasPreset): Int = when (preset) {
    CanvasPreset.HD_LANDSCAPE -> R.string.preset_hd_landscape
    CanvasPreset.HD_PORTRAIT -> R.string.preset_hd_portrait
    CanvasPreset.SQUARE -> R.string.preset_square
    CanvasPreset.SOCIAL_POST -> R.string.preset_social_post
    CanvasPreset.A4_PORTRAIT -> R.string.preset_a4
    CanvasPreset.COMIC_PAGE -> R.string.preset_comic
    CanvasPreset.WALLPAPER -> R.string.preset_wallpaper
    CanvasPreset.ANIMATION_SD -> R.string.preset_animation_sd
    CanvasPreset.STICKER -> R.string.preset_sticker
    CanvasPreset.PIXEL_ART -> R.string.preset_pixel_art
    CanvasPreset.UHD_LANDSCAPE -> R.string.preset_uhd
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewProjectSheet(
    initialType: ProjectType,
    template: ProjectTemplate?,
    maxFps: Int,
    onDismiss: () -> Unit,
    onCreate: (NewProjectSpec) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = with(density) { config.screenWidthDp.dp.toPx() }.roundToInt()
    val screenH = with(density) { config.screenHeightDp.dp.toPx() }.roundToInt()

    var type by remember { mutableStateOf(initialType) }
    var name by remember { mutableStateOf("") }
    var width by remember { mutableStateOf((template?.width ?: if (initialType == ProjectType.ANIMATION) 1280 else 2048).toString()) }
    var height by remember { mutableStateOf((template?.height ?: if (initialType == ProjectType.ANIMATION) 720 else 2048).toString()) }
    var background by remember { mutableIntStateOf(template?.background ?: 0xFFFFFFFF.toInt()) }
    var fps by remember { mutableIntStateOf((template?.fps ?: 12).coerceAtMost(maxFps)) }

    val w = width.toIntOrNull()
    val h = height.toIntOrNull()
    val sizeValid = w != null && h != null &&
        w in ProjectFactory.MIN_CANVAS..ProjectFactory.MAX_CANVAS &&
        h in ProjectFactory.MIN_CANVAS..ProjectFactory.MAX_CANVAS &&
        w.toLong() * h <= MAX_PIXELS

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(
                    when {
                        template != null -> templateInfo(template).first
                        type == ProjectType.ANIMATION -> R.string.new_animation
                        else -> R.string.new_drawing
                    },
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text(stringResource(R.string.project_name)) },
                placeholder = { Text(stringResource(R.string.untitled_project)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (template == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ProjectType.entries.forEachIndexed { i, t ->
                        SegmentedButton(
                            selected = type == t,
                            onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(i, ProjectType.entries.size),
                        ) {
                            Text(stringResource(if (t == ProjectType.DRAWING) R.string.type_drawing else R.string.type_animation))
                        }
                    }
                }
                Text(stringResource(R.string.canvas_presets), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = w == screenW && h == screenH,
                        onClick = { width = screenW.toString(); height = screenH.toString() },
                        label = { Text(stringResource(R.string.preset_screen)) },
                    )
                    CanvasPreset.entries.forEach { p ->
                        FilterChip(
                            selected = w == p.width && h == p.height,
                            onClick = { width = p.width.toString(); height = p.height.toString() },
                            label = { Text(stringResource(presetName(p)) + " · ${p.width}×${p.height}") },
                        )
                    }
                }
            }
            Text(stringResource(R.string.canvas_size), style = MaterialTheme.typography.titleSmall)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = width,
                    onValueChange = { v -> if (v.length <= 5 && v.all(Char::isDigit)) width = v },
                    label = { Text(stringResource(R.string.width_px)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { val t = width; width = height; height = t }) {
                    Icon(Icons.Filled.ScreenRotation, contentDescription = stringResource(R.string.swap_orientation))
                }
                OutlinedTextField(
                    value = height,
                    onValueChange = { v -> if (v.length <= 5 && v.all(Char::isDigit)) height = v },
                    label = { Text(stringResource(R.string.height_px)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            if (!sizeValid) {
                Text(
                    stringResource(R.string.canvas_size_invalid, ProjectFactory.MIN_CANVAS, ProjectFactory.MAX_CANVAS),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                val megabytes = (w!!.toLong() * h!! * 4 / (1024 * 1024)).coerceAtLeast(1)
                Text(
                    stringResource(R.string.canvas_memory_hint, megabytes),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(stringResource(R.string.background), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val choices = listOf(0xFFFFFFFF.toInt(), 0xFFF4EBDD.toInt(), 0xFF1B1E26.toInt(), 0xFF000000.toInt(), 0)
                choices.forEach { c ->
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (background == c) 3.dp else 1.dp,
                                color = if (background == c) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            )
                            .clickable { background = c },
                    ) {
                        if (c == 0) {
                            Checkerboard(Modifier.size(40.dp), cell = 10f)
                        } else {
                            Box(Modifier.size(40.dp).background(Color(c)))
                        }
                    }
                }
            }
            Text(
                stringResource(if (background == 0) R.string.background_transparent else R.string.background_color),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (type == ProjectType.ANIMATION) {
                Text(stringResource(R.string.frame_rate_value, fps), style = MaterialTheme.typography.titleSmall)
                Slider(
                    value = fps.toFloat(),
                    onValueChange = { fps = it.roundToInt().coerceIn(1, maxFps) },
                    valueRange = 1f..maxFps.toFloat(),
                    steps = (maxFps - 2).coerceAtLeast(0),
                )
                if (maxFps < 60) {
                    Text(
                        stringResource(R.string.fps_limit_hint, maxFps),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = {
                    onCreate(NewProjectSpec(name, type, w!!, h!!, background, fps, template))
                },
                enabled = sizeValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.create))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
