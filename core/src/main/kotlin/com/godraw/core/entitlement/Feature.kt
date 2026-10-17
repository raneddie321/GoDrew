package com.godraw.core.entitlement

/**
 * Every gated capability in GoDraw. Numeric limits (projects, layers, frames, FPS, export size,
 * brush and effect counts) live in [TierLimits]; boolean capabilities are listed here and mapped
 * to the minimum tier in [Entitlements.featureMinimumTier].
 */
enum class Feature {
    // Drawing
    CUSTOM_BRUSHES,
    LASSO_SELECTION,
    DISTORT_TRANSFORM,
    PERSPECTIVE_GUIDES,
    RADIAL_SYMMETRY,

    // Layers
    LAYER_GROUPS,
    LAYER_MASKS,
    CLIPPING_MASKS,

    // Animation
    ADVANCED_EASING,
    ADVANCED_TRANSITIONS,
    KEYFRAME_COPY_PASTE,
    TIMELINE_ZOOM,
    MULTI_FRAME_SELECTION,
    CUSTOM_BEZIER_EASING,
    SMOOTH_MOTION_PATHS,
    MULTIPLE_AUDIO_CLIPS,

    // Export
    PNG_SEQUENCE_EXPORT,
    GIF_DITHERING,
    HIGH_BITRATE_VIDEO,

    // Workspace
    TOOLBAR_CUSTOMIZATION,
    CUSTOM_ACCENT_COLOR,
}
