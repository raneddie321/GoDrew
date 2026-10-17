package com.godraw.core.brush

import com.godraw.core.model.LayerBlendMode
import kotlinx.serialization.Serializable

@Serializable
enum class BrushFamily {
    PENCIL,
    PEN,
    INK,
    PAINT,
    AIRBRUSH,
    WATERCOLOR,
    MARKER,
    CALLIGRAPHY,
    TEXTURE,
    CHARCOAL,
    CRAYON,
    SPRAY,
    PIXEL,
}

@Serializable
enum class BrushTip {
    /** Radial dab whose edge softness follows hardness. */
    ROUND,
    SQUARE,
    /** Elliptical tip with fixed angle (calligraphy nibs). */
    FLAT,
    /** Round tip modulated by procedural grain (pencil, charcoal, crayon). */
    GRAIN,
    /** Scattered speckles (spray / splatter). */
    SPECKLE,
}

@Serializable
data class BrushPreset(
    val id: String,
    val family: BrushFamily,
    /** 1-based variant number within its family, used for display names. */
    val variant: Int,
    /** User-provided name for custom brushes; null for built-in brushes. */
    val customName: String? = null,
    val tip: BrushTip = BrushTip.ROUND,
    /** Diameter in canvas pixels. */
    val size: Float = 12f,
    val opacity: Float = 1f,
    /** Per-dab flow (how much paint each dab deposits). */
    val flow: Float = 1f,
    val hardness: Float = 0.8f,
    /** Dab spacing as a fraction of the diameter. */
    val spacing: Float = 0.1f,
    val smoothing: Float = 0.2f,
    /** 0..1: how strongly pressure shrinks the size. */
    val pressureSize: Float = 0.6f,
    /** 0..1: how strongly pressure lowers the opacity. */
    val pressureOpacity: Float = 0f,
    /** Tip angle in degrees (FLAT tips). */
    val angle: Float = 45f,
    /** Tip roundness 0.05..1 (FLAT tips). */
    val roundness: Float = 1f,
    /** Random positional jitter as a fraction of the diameter. */
    val scatter: Float = 0f,
    val sizeJitter: Float = 0f,
    val opacityJitter: Float = 0f,
    /** Strength of procedural texture 0..1. */
    val texture: Float = 0f,
    /** Watercolor-style darker edges 0..1. */
    val wetEdges: Float = 0f,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    /** Disables anti-aliasing and snaps dabs to pixels. */
    val pixelPerfect: Boolean = false,
    val isCustom: Boolean = false,
) {
    fun diameterAt(pressure: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        return (size * (1f - pressureSize + pressureSize * p)).coerceAtLeast(0.5f)
    }

    fun opacityAt(pressure: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        return (opacity * (1f - pressureOpacity + pressureOpacity * p)).coerceIn(0f, 1f)
    }
}
