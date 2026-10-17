package com.godraw.core.entitlement

import com.godraw.core.model.LayerBlendMode
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Result of an entitlement check. */
sealed interface Access {
    data object Granted : Access

    /** The action is not available on the current plan; [requiredTier] unlocks it. */
    data class Locked(val requiredTier: Tier, val reason: LockReason) : Access

    val isGranted: Boolean get() = this is Granted
}

enum class LockReason {
    FEATURE,
    PROJECT_LIMIT,
    LAYER_LIMIT,
    FRAME_LIMIT,
    FPS_LIMIT,
    EXPORT_SIZE,
    BRUSH,
    EFFECT,
    BLEND_MODE,
    AUDIO_LIMIT,
}

/**
 * Answers every "may the user do X?" question for a given tier. All UI paths (toolbars, menus,
 * gestures, shortcuts, import) must go through this gate so locked features cannot be reached
 * through an alternative route. Existing content above the limits is never removed: the gate
 * only blocks *adding* more.
 */
class FeatureGate(val tier: Tier) {

    val limits: TierLimits = Entitlements.limitsFor(tier)

    fun check(feature: Feature): Access {
        val required = Entitlements.minimumTier(feature)
        return if (tier.atLeast(required)) Access.Granted else Access.Locked(required, LockReason.FEATURE)
    }

    fun canCreateProject(existingProjects: Int): Access =
        withinLimit(existingProjects + 1, limits.maxProjects, LockReason.PROJECT_LIMIT) { it.maxProjects }

    fun canAddLayers(existingLayers: Int, adding: Int = 1): Access =
        withinLimit(existingLayers + adding, limits.maxLayers, LockReason.LAYER_LIMIT) { it.maxLayers }

    fun canAddFrames(existingFrames: Int, adding: Int = 1): Access =
        withinLimit(existingFrames + adding, limits.maxFrames, LockReason.FRAME_LIMIT) { it.maxFrames }

    fun canAddAudioClip(existingClips: Int): Access =
        withinLimit(existingClips + 1, limits.maxAudioClips, LockReason.AUDIO_LIMIT) { it.maxAudioClips }

    fun canUseFps(fps: Int): Access =
        if (fps <= limits.maxFps) {
            Access.Granted
        } else {
            Access.Locked(firstTier { fps <= it.maxFps }, LockReason.FPS_LIMIT)
        }

    fun clampFps(fps: Int): Int = fps.coerceIn(1, limits.maxFps)

    /** Brushes are unlocked in catalog order: the first [TierLimits.brushCount] entries. */
    fun canUseBrush(catalogIndex: Int, isCustom: Boolean = false): Access {
        if (isCustom) return check(Feature.CUSTOM_BRUSHES)
        val count = limits.brushCount ?: return Access.Granted
        return if (catalogIndex < count) {
            Access.Granted
        } else {
            Access.Locked(firstTier { it.brushCount == null || catalogIndex < it.brushCount }, LockReason.BRUSH)
        }
    }

    /** Effects are unlocked in catalog order: the first [TierLimits.effectCount] entries. */
    fun canUseEffect(catalogIndex: Int): Access {
        val count = limits.effectCount ?: return Access.Granted
        return if (catalogIndex < count) {
            Access.Granted
        } else {
            Access.Locked(firstTier { it.effectCount == null || catalogIndex < it.effectCount }, LockReason.EFFECT)
        }
    }

    fun canUseBlendMode(mode: LayerBlendMode): Access =
        if (mode in limits.blendModes) {
            Access.Granted
        } else {
            Access.Locked(firstTier { mode in it.blendModes }, LockReason.BLEND_MODE)
        }

    fun canExportSize(width: Int, height: Int): Access {
        val short = min(width, height)
        val long = max(width, height)
        return if (short <= limits.maxExportShortEdge && long <= limits.maxExportLongEdge) {
            Access.Granted
        } else {
            Access.Locked(
                firstTier { short <= it.maxExportShortEdge && long <= it.maxExportLongEdge },
                LockReason.EXPORT_SIZE,
            )
        }
    }

    /**
     * Largest export size with the canvas aspect ratio that fits the tier, never upscaling
     * beyond [requestedScale] × canvas size. Dimensions are kept even (required by video encoders).
     */
    fun fitExportSize(canvasWidth: Int, canvasHeight: Int, requestedScale: Float = 1f): Pair<Int, Int> {
        var w = canvasWidth * requestedScale
        var h = canvasHeight * requestedScale
        val shortEdge = min(w, h)
        val longEdge = max(w, h)
        val factor = min(
            1f,
            min(limits.maxExportShortEdge / shortEdge, limits.maxExportLongEdge / longEdge),
        )
        w *= factor
        h *= factor
        val ew = max(2, (w.roundToInt() / 2) * 2)
        val eh = max(2, (h.roundToInt() / 2) * 2)
        return ew to eh
    }

    private inline fun withinLimit(
        requested: Int,
        limit: Int?,
        reason: LockReason,
        crossinline selector: (TierLimits) -> Int?,
    ): Access {
        if (limit == null || requested <= limit) return Access.Granted
        return Access.Locked(firstTier { val l = selector(it); l == null || requested <= l }, reason)
    }

    private inline fun firstTier(predicate: (TierLimits) -> Boolean): Tier =
        Tier.entries.firstOrNull { it.ordinal > tier.ordinal && predicate(Entitlements.limitsFor(it)) } ?: Tier.MAX
}
