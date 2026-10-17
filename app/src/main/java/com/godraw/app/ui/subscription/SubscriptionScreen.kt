package com.godraw.app.ui.subscription

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.godraw.app.AppContainer
import com.godraw.app.R
import com.godraw.app.billing.BillingAvailability
import com.godraw.app.billing.BillingConfig
import com.godraw.app.billing.BillingMessage
import com.godraw.app.billing.BillingPeriod
import com.godraw.app.billing.PlanOffer
import com.godraw.app.billing.UnavailableReason
import com.godraw.app.ui.common.TierBadge
import com.godraw.app.ui.common.tierColor
import com.godraw.app.ui.common.tierName
import com.godraw.app.ui.theme.GoDrawColors
import com.godraw.core.brush.BrushCatalog
import com.godraw.core.effects.EffectType
import com.godraw.core.entitlement.Entitlements
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.Tier
import com.godraw.core.entitlement.TierLimits
import kotlinx.coroutines.launch

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(container: AppContainer, onBack: () -> Unit) {
    val billing by container.billing.state.collectAsStateWithLifecycle()
    val tier by container.entitlements.tier.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { container.billing.start() }
    LaunchedEffect(billing.message) {
        val msg = billing.message ?: return@LaunchedEffect
        val text = context.getString(
            when (msg) {
                BillingMessage.PURCHASE_COMPLETE -> R.string.billing_purchase_complete
                BillingMessage.PURCHASE_PENDING -> R.string.billing_purchase_pending
                BillingMessage.PURCHASE_CANCELED -> R.string.billing_purchase_canceled
                BillingMessage.PURCHASE_FAILED -> R.string.billing_purchase_failed
                BillingMessage.VERIFICATION_FAILED -> R.string.billing_verification_failed
                BillingMessage.RESTORED -> R.string.billing_restored
                BillingMessage.NOTHING_TO_RESTORE -> R.string.billing_nothing_to_restore
            },
        )
        container.billing.consumeMessage()
        snackbar.showSnackbar(text)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.plans_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(tier, billing.pendingPurchase, simulated = container.entitlements.simulator.override.value != null)

            val availability = billing.availability
            if (availability is BillingAvailability.Unavailable) {
                UnavailableCard(availability.reason)
            }

            Tier.entries.forEach { plan ->
                PlanCard(
                    plan = plan,
                    currentTier = tier,
                    offers = billing.offers.filter { it.product.tier == plan },
                    purchasable = availability == BillingAvailability.Ready,
                    activeProductId = billing.activeProductId,
                    onBuy = { offer ->
                        val activity = context.findActivity() ?: return@PlanCard
                        container.billing.launchPurchase(activity, offer)
                    },
                )
            }

            ComparisonTable()

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { container.billing.refreshPurchases(userInitiated = true) } },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.restore_purchases)) }
                OutlinedButton(
                    onClick = {
                        val sku = billing.activeProductId
                        val url = BillingConfig.MANAGE_SUBSCRIPTIONS_URL +
                            (if (sku != null) "?sku=$sku&package=${context.packageName}" else "")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.manage_subscription)) }
            }
            Text(
                stringResource(R.string.plans_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(tier: Tier, pending: Boolean, simulated: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(GoDrawColors.brandBrush)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.current_plan), color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
            Text(stringResource(tierName(tier)), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(
                    when (tier) {
                        Tier.FREE -> R.string.plan_free_tagline
                        Tier.PRO -> R.string.plan_pro_tagline
                        Tier.MAX -> R.string.plan_max_tagline
                    },
                ),
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (pending) {
                Text(stringResource(R.string.billing_pending_notice), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
            if (simulated) {
                Text(stringResource(R.string.debug_simulator_active), color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun UnavailableCard(reason: UnavailableReason) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.billing_unavailable_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(
                        when (reason) {
                            UnavailableReason.BILLING_UNSUPPORTED -> R.string.billing_unavailable_unsupported
                            UnavailableReason.PLAY_UNAVAILABLE -> R.string.billing_unavailable_play
                            UnavailableReason.PRODUCTS_NOT_CONFIGURED -> R.string.billing_unavailable_products
                            UnavailableReason.VERIFICATION_NOT_CONFIGURED -> R.string.billing_unavailable_verification
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: Tier,
    currentTier: Tier,
    offers: List<PlanOffer>,
    purchasable: Boolean,
    activeProductId: String?,
    onBuy: (PlanOffer) -> Unit,
) {
    val color = tierColor(plan)
    val isCurrent = plan == currentTier
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .border(if (isCurrent) 2.dp else 1.dp, if (isCurrent) color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TierBadge(plan)
                Spacer(Modifier.weight(1f))
                if (isCurrent) {
                    Text(stringResource(R.string.your_plan), color = color, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                stringResource(
                    when (plan) {
                        Tier.FREE -> R.string.plan_free_tagline
                        Tier.PRO -> R.string.plan_pro_tagline
                        Tier.MAX -> R.string.plan_max_tagline
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            planHighlights(plan).forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Check, null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(line, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (plan == Tier.FREE) {
                Text(stringResource(R.string.plan_free_price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            } else {
                BillingPeriod.entries.forEach { period ->
                    val offer = offers.firstOrNull { it.product.period == period }
                    val label = stringResource(if (period == BillingPeriod.MONTHLY) R.string.period_monthly else R.string.period_yearly)
                    val owned = offer != null && offer.product.productId == activeProductId
                    Button(
                        onClick = { offer?.let(onBuy) },
                        enabled = purchasable && offer != null && !owned,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                    ) {
                        Text(
                            when {
                                owned -> stringResource(R.string.plan_owned, label)
                                offer != null -> stringResource(R.string.plan_price, label, offer.formattedPrice) +
                                    (if (offer.hasFreeTrial) " · " + stringResource(R.string.free_trial) else "")
                                else -> stringResource(R.string.plan_not_available, label)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun planHighlights(plan: Tier): List<String> {
    val l = Entitlements.limitsFor(plan)
    val unlimited = stringResource(R.string.unlimited)
    fun n(v: Int?) = v?.toString() ?: unlimited
    return when (plan) {
        Tier.FREE -> listOf(
            stringResource(R.string.hl_projects, n(l.maxProjects)),
            stringResource(R.string.hl_layers, n(l.maxLayers)),
            stringResource(R.string.hl_frames, n(l.maxFrames)),
            stringResource(R.string.hl_fps_export, l.maxFps, l.maxExportShortEdge),
            stringResource(R.string.hl_brushes_effects, n(l.brushCount), n(l.effectCount)),
            stringResource(R.string.hl_free_extras),
        )
        Tier.PRO -> listOf(
            stringResource(R.string.hl_everything_in, stringResource(R.string.tier_free)),
            stringResource(R.string.hl_projects, n(l.maxProjects)),
            stringResource(R.string.hl_layers, n(l.maxLayers)),
            stringResource(R.string.hl_frames, n(l.maxFrames)),
            stringResource(R.string.hl_fps_export, l.maxFps, l.maxExportShortEdge),
            stringResource(R.string.hl_brushes_effects, n(l.brushCount), n(l.effectCount)),
            stringResource(R.string.hl_pro_extras),
        )
        Tier.MAX -> listOf(
            stringResource(R.string.hl_everything_in, stringResource(R.string.tier_pro)),
            stringResource(R.string.hl_max_unlimited),
            stringResource(R.string.hl_fps_export, l.maxFps, l.maxExportShortEdge),
            stringResource(R.string.hl_brushes_effects, BrushCatalog.totalCount.toString(), EffectType.entries.size.toString()),
            stringResource(R.string.hl_max_extras),
        )
    }
}

private data class Row3(val label: Int, val values: (TierLimits) -> String)

@Composable
private fun ComparisonTable() {
    val unlimited = stringResource(R.string.unlimited)
    val yes = "✓"
    val no = "—"
    fun n(v: Int?) = v?.toString() ?: unlimited
    fun feature(f: Feature): (TierLimits) -> String = { l -> if (l.tier.atLeast(Entitlements.minimumTier(f))) yes else no }
    val rows = listOf(
        Row3(R.string.cmp_projects) { n(it.maxProjects) },
        Row3(R.string.cmp_layers) { n(it.maxLayers) },
        Row3(R.string.cmp_frames) { n(it.maxFrames) },
        Row3(R.string.cmp_fps) { it.maxFps.toString() },
        Row3(R.string.cmp_export) { "${it.maxExportShortEdge}p" },
        Row3(R.string.cmp_brushes) { n(it.brushCount ?: BrushCatalog.totalCount) },
        Row3(R.string.cmp_effects) { n(it.effectCount ?: EffectType.entries.size) },
        Row3(R.string.cmp_blend_modes) { it.blendModes.size.toString() },
        Row3(R.string.cmp_audio) { n(it.maxAudioClips) },
        Row3(R.string.cmp_custom_brushes, feature(Feature.CUSTOM_BRUSHES)),
        Row3(R.string.cmp_masks, feature(Feature.LAYER_MASKS)),
        Row3(R.string.cmp_groups, feature(Feature.LAYER_GROUPS)),
        Row3(R.string.cmp_lasso, feature(Feature.LASSO_SELECTION)),
        Row3(R.string.cmp_easing, feature(Feature.ADVANCED_EASING)),
        Row3(R.string.cmp_png_sequence, feature(Feature.PNG_SEQUENCE_EXPORT)),
        Row3(R.string.cmp_bezier, feature(Feature.CUSTOM_BEZIER_EASING)),
        Row3(R.string.cmp_motion_paths, feature(Feature.SMOOTH_MOTION_PATHS)),
        Row3(R.string.cmp_bitrate, feature(Feature.HIGH_BITRATE_VIDEO)),
        Row3(R.string.cmp_accent, feature(Feature.CUSTOM_ACCENT_COLOR)),
        Row3(R.string.cmp_no_watermark) { yes },
    )
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.compare_plans),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Spacer(Modifier.weight(1.6f))
                Tier.entries.forEach { t ->
                    Text(
                        stringResource(tierName(t)),
                        color = tierColor(t),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            rows.forEachIndexed { i, r ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (i % 2 == 0) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(r.label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1.6f))
                    Tier.entries.forEach { t ->
                        Text(
                            r.values(Entitlements.limitsFor(t)),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
