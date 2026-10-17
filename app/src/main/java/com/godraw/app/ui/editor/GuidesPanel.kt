package com.godraw.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.engine.DrawingEngine
import com.godraw.app.engine.EngineState
import com.godraw.app.engine.setGuides
import com.godraw.app.ui.common.LockMarker
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.FeatureGate
import com.godraw.core.model.SymmetryMode
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidesPanel(engine: DrawingEngine, st: EngineState, gate: FeatureGate) {
    val doc = st.doc
    val g = doc.guides
    val w = doc.width.toFloat()
    val h = doc.height.toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.panel_guides), style = MaterialTheme.typography.titleMedium)

        Text(stringResource(R.string.symmetry), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SymmetryMode.entries.forEach { m ->
                val locked = if (m == SymmetryMode.RADIAL) gate.check(Feature.RADIAL_SYMMETRY) as? Access.Locked else null
                FilterChip(
                    selected = g.symmetry == m,
                    onClick = { engine.setGuides(g.copy(symmetry = m)) },
                    label = { Text(stringResource(symmetryName(m))) },
                    trailingIcon = if (locked != null) { { LockMarker(locked.requiredTier) } } else null,
                )
            }
        }
        if (g.symmetry == SymmetryMode.RADIAL) {
            LabeledSlider(R.string.radial_segments, g.radialSegments.toFloat(), 2f..24f, "${g.radialSegments}", steps = 21) {
                engine.setGuides(g.copy(radialSegments = it.roundToInt()))
            }
        }
        HorizontalDivider()
        SwitchLine(R.string.show_grid, g.showGrid) { engine.setGuides(g.copy(showGrid = it)) }
        if (g.showGrid) {
            LabeledSlider(R.string.grid_size, g.gridSize.toFloat(), 2f..256f, "${g.gridSize} px") {
                engine.setGuides(g.copy(gridSize = it.roundToInt().coerceAtLeast(2)))
            }
        }
        HorizontalDivider()
        val rulerAngle = Math.toDegrees(kotlin.math.atan2((g.rulerY2 - g.rulerY1).toDouble(), (g.rulerX2 - g.rulerX1).toDouble())).toFloat()
        SwitchLine(R.string.ruler, g.rulerEnabled) { on ->
            if (on && g.rulerX1 == g.rulerX2 && g.rulerY1 == g.rulerY2) {
                engine.setGuides(g.copy(rulerEnabled = true, rulerX1 = 0f, rulerY1 = h / 2, rulerX2 = w, rulerY2 = h / 2))
            } else {
                engine.setGuides(g.copy(rulerEnabled = on))
            }
        }
        if (g.rulerEnabled) {
            Text(stringResource(R.string.ruler_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LabeledSlider(R.string.ruler_angle, rulerAngle, -90f..90f, "${rulerAngle.roundToInt()}°") { deg ->
                val cx = (g.rulerX1 + g.rulerX2) / 2
                val cy = (g.rulerY1 + g.rulerY2) / 2
                val r = Math.toRadians(deg.toDouble())
                val len = maxOf(w, h)
                engine.setGuides(
                    g.copy(
                        rulerX1 = cx - (cos(r) * len / 2).toFloat(), rulerY1 = cy - (sin(r) * len / 2).toFloat(),
                        rulerX2 = cx + (cos(r) * len / 2).toFloat(), rulerY2 = cy + (sin(r) * len / 2).toFloat(),
                    ),
                )
            }
            LabeledSlider(R.string.ruler_offset, (g.rulerY1 + g.rulerY2) / 2, 0f..h, "${((g.rulerY1 + g.rulerY2) / 2).roundToInt()}") { y ->
                val dy = y - (g.rulerY1 + g.rulerY2) / 2
                engine.setGuides(g.copy(rulerY1 = g.rulerY1 + dy, rulerY2 = g.rulerY2 + dy))
            }
        }
        HorizontalDivider()
        val perspectiveLocked = gate.check(Feature.PERSPECTIVE_GUIDES) as? Access.Locked
        SwitchLine(R.string.perspective, g.perspectiveEnabled, perspectiveLocked?.requiredTier) { on ->
            if (on && g.vanishingPoints.size < 4) {
                val horizon = h * 0.4f
                engine.setGuides(g.copy(perspectiveEnabled = true, horizonY = horizon, vanishingPoints = listOf(-w * 0.2f, horizon, w * 1.2f, horizon)))
            } else {
                engine.setGuides(g.copy(perspectiveEnabled = on))
            }
        }
        if (g.perspectiveEnabled && g.vanishingPoints.size >= 4) {
            Text(stringResource(R.string.perspective_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val horizon = g.horizonY ?: g.vanishingPoints[1]
            LabeledSlider(R.string.horizon, horizon, 0f..h, "${horizon.roundToInt()}") { y ->
                engine.setGuides(g.copy(horizonY = y, vanishingPoints = listOf(g.vanishingPoints[0], y, g.vanishingPoints[2], y)))
            }
            LabeledSlider(R.string.vanishing_left, g.vanishingPoints[0], -w..w, "${g.vanishingPoints[0].roundToInt()}") { x ->
                engine.setGuides(g.copy(vanishingPoints = listOf(x, g.vanishingPoints[1], g.vanishingPoints[2], g.vanishingPoints[3])))
            }
            LabeledSlider(R.string.vanishing_right, g.vanishingPoints[2], 0f..w * 2, "${g.vanishingPoints[2].roundToInt()}") { x ->
                engine.setGuides(g.copy(vanishingPoints = listOf(g.vanishingPoints[0], g.vanishingPoints[1], x, g.vanishingPoints[3])))
            }
        }
    }
}
