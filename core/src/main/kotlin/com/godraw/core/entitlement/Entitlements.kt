package com.godraw.core.entitlement

import com.godraw.core.model.LayerBlendMode

/**
 * Numeric limits for one tier. A `null` limit means "no arbitrary limit"; the app still applies
 * device safety checks (memory, storage) for those.
 */
data class TierLimits(
    val tier: Tier,
    val maxProjects: Int?,
    val maxLayers: Int?,
    val maxFrames: Int?,
    val maxFps: Int,
    /** Maximum length of the shorter export edge, e.g. 1080 for "1080p". */
    val maxExportShortEdge: Int,
    /** Maximum length of the longer export edge (16:9 counterpart of the short edge). */
    val maxExportLongEdge: Int,
    val brushCount: Int?,
    val effectCount: Int?,
    val maxAudioClips: Int?,
    val blendModes: Set<LayerBlendMode>,
)

/**
 * The single, central place where GoDraw's membership plans are configured.
 * Change values here to adjust what each plan offers.
 */
object Entitlements {

    private val basicBlendModes = setOf(
        LayerBlendMode.NORMAL,
        LayerBlendMode.MULTIPLY,
        LayerBlendMode.SCREEN,
        LayerBlendMode.OVERLAY,
    )

    private val proBlendModes = basicBlendModes + setOf(
        LayerBlendMode.DARKEN,
        LayerBlendMode.LIGHTEN,
        LayerBlendMode.COLOR_DODGE,
        LayerBlendMode.COLOR_BURN,
        LayerBlendMode.HARD_LIGHT,
        LayerBlendMode.SOFT_LIGHT,
        LayerBlendMode.DIFFERENCE,
        LayerBlendMode.EXCLUSION,
        LayerBlendMode.ADD,
    )

    val FREE = TierLimits(
        tier = Tier.FREE,
        maxProjects = 20,
        maxLayers = 15,
        maxFrames = 500,
        maxFps = 24,
        maxExportShortEdge = 1080,
        maxExportLongEdge = 1920,
        brushCount = 40,
        effectCount = 5,
        maxAudioClips = 1,
        blendModes = basicBlendModes,
    )

    val PRO = TierLimits(
        tier = Tier.PRO,
        maxProjects = 100,
        maxLayers = 60,
        maxFrames = 2000,
        maxFps = 30,
        maxExportShortEdge = 1440,
        maxExportLongEdge = 2560,
        brushCount = 150,
        effectCount = 25,
        maxAudioClips = 8,
        blendModes = proBlendModes,
    )

    val MAX = TierLimits(
        tier = Tier.MAX,
        maxProjects = null,
        maxLayers = null,
        maxFrames = null,
        maxFps = 60,
        maxExportShortEdge = 2160,
        maxExportLongEdge = 3840,
        brushCount = null,
        effectCount = null,
        maxAudioClips = null,
        blendModes = LayerBlendMode.entries.toSet(),
    )

    /** Minimum tier needed for each boolean feature. */
    val featureMinimumTier: Map<Feature, Tier> = mapOf(
        Feature.CUSTOM_BRUSHES to Tier.PRO,
        Feature.LASSO_SELECTION to Tier.PRO,
        Feature.DISTORT_TRANSFORM to Tier.PRO,
        Feature.PERSPECTIVE_GUIDES to Tier.PRO,
        Feature.RADIAL_SYMMETRY to Tier.PRO,
        Feature.LAYER_GROUPS to Tier.PRO,
        Feature.LAYER_MASKS to Tier.PRO,
        Feature.CLIPPING_MASKS to Tier.PRO,
        Feature.ADVANCED_EASING to Tier.PRO,
        Feature.ADVANCED_TRANSITIONS to Tier.PRO,
        Feature.KEYFRAME_COPY_PASTE to Tier.PRO,
        Feature.TIMELINE_ZOOM to Tier.PRO,
        Feature.MULTI_FRAME_SELECTION to Tier.PRO,
        Feature.MULTIPLE_AUDIO_CLIPS to Tier.PRO,
        Feature.PNG_SEQUENCE_EXPORT to Tier.PRO,
        Feature.GIF_DITHERING to Tier.PRO,
        Feature.TOOLBAR_CUSTOMIZATION to Tier.PRO,
        Feature.CUSTOM_BEZIER_EASING to Tier.MAX,
        Feature.SMOOTH_MOTION_PATHS to Tier.MAX,
        Feature.HIGH_BITRATE_VIDEO to Tier.MAX,
        Feature.CUSTOM_ACCENT_COLOR to Tier.MAX,
    )

    fun limitsFor(tier: Tier): TierLimits = when (tier) {
        Tier.FREE -> FREE
        Tier.PRO -> PRO
        Tier.MAX -> MAX
    }

    fun minimumTier(feature: Feature): Tier = featureMinimumTier[feature] ?: Tier.FREE
}
