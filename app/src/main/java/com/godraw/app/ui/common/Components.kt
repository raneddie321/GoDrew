package com.godraw.app.ui.common

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.godraw.app.R
import com.godraw.app.ui.theme.GoDrawColors
import com.godraw.app.ui.theme.WordmarkStyle
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.LockReason
import com.godraw.core.entitlement.Tier

/** The GoDraw wordmark drawn with the logo's gradients so it adapts to every theme. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun GoDrawWordmark(modifier: Modifier = Modifier, style: TextStyle = WordmarkStyle) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("G", style = style.copy(brush = GoDrawColors.gBrush))
        Text("o", style = style.copy(color = MaterialTheme.colorScheme.onBackground))
        Text("Draw", style = style.copy(brush = GoDrawColors.drawBrush))
    }
}

fun tierColor(tier: Tier): Color = when (tier) {
    Tier.FREE -> GoDrawColors.Free
    Tier.PRO -> GoDrawColors.Pro
    Tier.MAX -> GoDrawColors.Max
}

@StringRes
fun tierName(tier: Tier): Int = when (tier) {
    Tier.FREE -> R.string.tier_free
    Tier.PRO -> R.string.tier_pro
    Tier.MAX -> R.string.tier_max
}

@Composable
fun TierBadge(tier: Tier, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(tierColor(tier).copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tier != Tier.FREE) {
            Icon(Icons.Filled.WorkspacePremium, null, tint = tierColor(tier), modifier = Modifier.size(14.dp))
            Spacer(Modifier.size(4.dp))
        }
        Text(stringResource(tierName(tier)), color = tierColor(tier), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Small lock marker shown on items that need a higher plan. */
@Composable
fun LockMarker(requiredTier: Tier, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .clip(RoundedCornerShape(50))
            .background(tierColor(requiredTier)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Lock, null, tint = Color.White, modifier = Modifier.size(11.dp))
    }
}

@StringRes
fun lockReasonText(reason: LockReason): Int = when (reason) {
    LockReason.FEATURE -> R.string.lock_reason_feature
    LockReason.PROJECT_LIMIT -> R.string.lock_reason_projects
    LockReason.LAYER_LIMIT -> R.string.lock_reason_layers
    LockReason.FRAME_LIMIT -> R.string.lock_reason_frames
    LockReason.FPS_LIMIT -> R.string.lock_reason_fps
    LockReason.EXPORT_SIZE -> R.string.lock_reason_export
    LockReason.BRUSH -> R.string.lock_reason_brush
    LockReason.EFFECT -> R.string.lock_reason_effect
    LockReason.BLEND_MODE -> R.string.lock_reason_blend
    LockReason.AUDIO_LIMIT -> R.string.lock_reason_audio
}

/**
 * Explains a locked feature and offers the upgrade. Shown only when the user taps a locked
 * item — never automatically while drawing.
 */
@Composable
fun UpgradeDialog(
    locked: Access.Locked,
    featureName: String?,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val plan = stringResource(tierName(locked.requiredTier))
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.WorkspacePremium, null, tint = tierColor(locked.requiredTier)) },
        title = { Text(featureName ?: stringResource(R.string.upgrade_title_generic, plan)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(lockReasonText(locked.reason), plan))
                Text(stringResource(R.string.upgrade_work_is_safe), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onUpgrade) { Text(stringResource(R.string.upgrade_see_plans, plan)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

/** Transparent-canvas checkerboard used behind artwork. */
@Composable
fun Checkerboard(modifier: Modifier = Modifier, cell: Float = 24f) {
    val light = Color(0xFFE9E9EC)
    val dark = Color(0xFFCFCFD4)
    Canvas(modifier) {
        drawRect(light)
        var y = 0f
        var row = 0
        while (y < size.height) {
            var x = if (row % 2 == 0) 0f else cell
            while (x < size.width) {
                drawRect(dark, Offset(x, y), Size(cell, cell))
                x += cell * 2
            }
            y += cell
            row++
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}
