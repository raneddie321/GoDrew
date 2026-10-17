package com.godraw.app.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.engine.ToolType
import com.godraw.app.ui.common.Checkerboard
import com.godraw.app.ui.home.TextInputDialog
import com.godraw.core.color.ColorUtils
import com.godraw.core.color.Palettes
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun paletteName(id: String, fallback: String): String = when (id) {
    "default" -> stringResource(R.string.palette_default)
    "godraw" -> stringResource(R.string.palette_godraw)
    "skin" -> stringResource(R.string.palette_skin)
    "pastel" -> stringResource(R.string.palette_pastel)
    "grayscale" -> stringResource(R.string.palette_grayscale)
    else -> fallback
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPanel(vm: EditorViewModel, engine: DrawingEngine, st: EngineState, settings: AppSettings) {
    val color = st.color
    val currentColor by rememberUpdatedState(color)
    var hex by remember(color) { mutableStateOf(ColorUtils.toHex(color)) }
    var savingPalette by remember { mutableStateOf(false) }

    // Remember the color the user ended up with when the panel closes.
    DisposableEffect(Unit) { onDispose { vm.addRecentColor(currentColor) } }

    fun set(c: Int) = engine.setColor(c)

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.panel_color), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { engine.setTool(ToolType.EYEDROPPER); vm.setPanel(EditorPanel.NONE) }) {
                Icon(Icons.Filled.Colorize, stringResource(R.string.tool_eyedropper))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HsvWheel(color, onChange = ::set, modifier = Modifier.size(220.dp))
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))) {
                    Checkerboard(Modifier.size(64.dp), cell = 8f)
                    Box(Modifier.size(64.dp).background(Color(color)))
                }
                Text(ColorUtils.toHex(color), style = MaterialTheme.typography.labelLarge)
            }
        }
        val a = ColorUtils.alpha(color)
        val r = ColorUtils.red(color)
        val g = ColorUtils.green(color)
        val b = ColorUtils.blue(color)
        LabeledSlider(R.string.color_red, r.toFloat(), 0f..255f, "$r") { set(ColorUtils.argb(a, it.roundToInt(), g, b)) }
        LabeledSlider(R.string.color_green, g.toFloat(), 0f..255f, "$g") { set(ColorUtils.argb(a, r, it.roundToInt(), b)) }
        LabeledSlider(R.string.color_blue, b.toFloat(), 0f..255f, "$b") { set(ColorUtils.argb(a, r, g, it.roundToInt())) }
        LabeledSlider(R.string.color_alpha, a.toFloat(), 0f..255f, "${(a * 100 / 255)}%") { set(ColorUtils.withAlpha(color, it.roundToInt())) }
        OutlinedTextField(
            value = hex,
            onValueChange = { v ->
                hex = v.take(9)
                ColorUtils.parseHex(v)?.let { parsed -> set(parsed) }
            },
            label = { Text(stringResource(R.string.color_hex)) },
            singleLine = true,
            isError = ColorUtils.parseHex(hex) == null,
            modifier = Modifier.fillMaxWidth(),
        )

        if (settings.recentColors.isNotEmpty()) {
            Text(stringResource(R.string.recent_colors), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                settings.recentColors.forEach { c -> Swatch(c, c == color) { set(c) } }
            }
            TextButton(onClick = { savingPalette = true }) { Text(stringResource(R.string.save_recent_as_palette)) }
        }

        Text(stringResource(R.string.palettes), style = MaterialTheme.typography.titleSmall)
        Palettes.builtIn.forEach { p ->
            Text(paletteName(p.id, p.name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.colors.forEach { c -> Swatch(c, c == color) { set(c) } }
            }
        }
        settings.savedPalettes.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.addColorToPalette(p.id, color) }) { Icon(Icons.Filled.Add, stringResource(R.string.add_current_color)) }
                IconButton(onClick = { vm.deletePalette(p.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                p.colors.forEach { c -> Swatch(c, c == color) { set(c) } }
            }
        }
    }
    if (savingPalette) {
        TextInputDialog(
            title = stringResource(R.string.palette_name),
            initial = stringResource(R.string.my_palette),
            onDismiss = { savingPalette = false },
            onConfirm = {
                savingPalette = false
                vm.savePalette(it, settings.recentColors)
            },
        )
    }
}

@Composable
fun Swatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(onClick = onClick),
    ) {
        Checkerboard(Modifier.size(32.dp), cell = 6f)
        Box(Modifier.size(32.dp).background(Color(color)))
    }
}

/** Hue ring with a saturation/value square inside. */
@Composable
fun HsvWheel(color: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val hsv = remember(color) { ColorUtils.toHsv(color) }
    var grayHue by remember { mutableStateOf(hsv[0]) }
    // Hue is undefined for grays; keep the last chosen hue so the square doesn't jump.
    val hue = if (hsv[1] > 0.01f && hsv[2] > 0.01f) hsv[0] else grayHue
    val alpha = ColorUtils.alpha(color)
    val latest by rememberUpdatedState(Triple(hue, hsv[1], hsv[2]))
    var dragTarget by remember { mutableStateOf(0) }

    fun handle(pos: Offset, size: Size, start: Boolean) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = min(cx, cy)
        val ring = outer * 0.18f
        val inner = outer - ring
        val half = inner * 0.70f
        val dx = pos.x - cx
        val dy = pos.y - cy
        if (start) dragTarget = if (hypot(dx, dy) > inner) 1 else 2
        val (h, s, v) = latest
        if (dragTarget == 1) {
            var deg = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
            if (deg < 0) deg += 360f
            grayHue = deg
            onChange(ColorUtils.fromHsv(deg, s, v, alpha))
        } else {
            val ns = ((dx + half) / (2 * half)).coerceIn(0f, 1f)
            val nv = 1f - ((dy + half) / (2 * half)).coerceIn(0f, 1f)
            onChange(ColorUtils.fromHsv(h, ns, nv, alpha))
        }
    }

    Canvas(
        modifier
            .pointerInput(Unit) {
                detectTapGestures { o -> handle(o, Size(size.width.toFloat(), size.height.toFloat()), true) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o -> handle(o, Size(size.width.toFloat(), size.height.toFloat()), true) },
                ) { change, _ -> handle(change.position, Size(size.width.toFloat(), size.height.toFloat()), false) }
            },
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outer = min(cx, cy)
        val ring = outer * 0.18f
        val inner = outer - ring
        val hues = (0..12).map { Color(ColorUtils.fromHsv(it * 30f, 1f, 1f)) }
        drawCircle(Brush.sweepGradient(hues, Offset(cx, cy)), radius = outer - ring / 2, style = Stroke(width = ring))
        val half = inner * 0.70f
        val topLeft = Offset(cx - half, cy - half)
        val sq = Size(half * 2, half * 2)
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color(ColorUtils.fromHsv(hue, 1f, 1f))), topLeft.x, topLeft.x + sq.width), topLeft, sq)
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black), topLeft.y, topLeft.y + sq.height), topLeft, sq)
        // Markers
        val ha = Math.toRadians(hue.toDouble())
        val hr = outer - ring / 2
        val hm = Offset(cx + (cos(ha) * hr).toFloat(), cy + (sin(ha) * hr).toFloat())
        drawCircle(Color.White, ring * 0.42f, hm, style = Stroke(width = 3f))
        val sm = Offset(topLeft.x + hsv[1] * sq.width, topLeft.y + (1f - hsv[2]) * sq.height)
        drawCircle(Color.White, 9f, sm, style = Stroke(width = 3f))
        drawCircle(Color.Black, 11f, sm, style = Stroke(width = 1.5f))
    }
}
