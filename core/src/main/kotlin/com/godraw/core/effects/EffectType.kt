package com.godraw.core.effects

/** Describes one adjustable parameter of an effect. */
data class EffectParam(val key: String, val min: Float, val max: Float, val default: Float)

/**
 * The effect library in unlock order: Free gets the first 5, Pro the first 25, Max all.
 * Order matters — see [com.godraw.core.entitlement.FeatureGate.canUseEffect].
 */
enum class EffectType(vararg val params: EffectParam) {
    // Free
    BLUR(EffectParam("radius", 1f, 50f, 6f)),
    GLOW(EffectParam("radius", 2f, 60f, 12f), EffectParam("strength", 0f, 2f, 1f)),
    DROP_SHADOW(EffectParam("distance", 0f, 100f, 12f), EffectParam("blur", 0f, 40f, 8f)),
    BRIGHTNESS_CONTRAST(EffectParam("brightness", -100f, 100f, 0f), EffectParam("contrast", -100f, 100f, 10f)),
    HUE_SATURATION(EffectParam("hue", -180f, 180f, 0f), EffectParam("saturation", -100f, 100f, 20f)),

    // Pro
    GRAYSCALE,
    SEPIA(EffectParam("amount", 0f, 1f, 1f)),
    INVERT,
    POSTERIZE(EffectParam("levels", 2f, 16f, 4f)),
    THRESHOLD(EffectParam("level", 0f, 255f, 128f)),
    PIXELATE(EffectParam("size", 2f, 64f, 8f)),
    SHARPEN(EffectParam("amount", 0f, 3f, 1f)),
    EMBOSS(EffectParam("strength", 0.5f, 4f, 1.5f)),
    EDGE_DETECT,
    VIGNETTE(EffectParam("amount", 0f, 1f, 0.6f)),
    NOISE(EffectParam("amount", 0f, 100f, 20f)),
    MOTION_BLUR(EffectParam("distance", 2f, 80f, 16f), EffectParam("angle", 0f, 360f, 0f)),
    OUTLINE(EffectParam("width", 1f, 20f, 3f)),
    INNER_SHADOW(EffectParam("distance", 0f, 50f, 6f), EffectParam("blur", 0f, 30f, 6f)),
    COLOR_BALANCE(EffectParam("red", -100f, 100f, 15f), EffectParam("blue", -100f, 100f, -10f)),
    EXPOSURE(EffectParam("stops", -3f, 3f, 0.5f)),
    TEMPERATURE(EffectParam("warmth", -100f, 100f, 25f)),
    DUOTONE(EffectParam("hue", 0f, 360f, 210f)),
    GRADIENT_MAP(EffectParam("hue", 0f, 360f, 280f)),
    CHROMATIC_ABERRATION(EffectParam("offset", 1f, 30f, 4f)),

    // Max
    HALFTONE(EffectParam("cell", 4f, 40f, 10f)),
    OIL_PAINT(EffectParam("radius", 1f, 8f, 3f)),
    SPIN_BLUR(EffectParam("angle", 1f, 45f, 10f)),
    ZOOM_BLUR(EffectParam("amount", 0.01f, 0.5f, 0.15f)),
    LONG_SHADOW(EffectParam("length", 4f, 300f, 60f), EffectParam("angle", 0f, 360f, 45f)),
    NEON_EDGES(EffectParam("radius", 2f, 30f, 8f)),
    SOLARIZE(EffectParam("level", 0f, 255f, 128f)),
    GLITCH(EffectParam("amount", 1f, 60f, 16f)),
    BLOOM(EffectParam("threshold", 0f, 255f, 180f), EffectParam("radius", 2f, 60f, 16f)),
    CROSSHATCH(EffectParam("spacing", 3f, 20f, 6f)),
    ;

    fun defaults(): Map<String, Float> = params.associate { it.key to it.default }
}
