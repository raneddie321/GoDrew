package com.godraw.core.brush

import com.godraw.core.model.LayerBlendMode

/**
 * The built-in brush library. Brushes are generated deterministically from family recipes with
 * varied sizes, hardness, texture and dynamics. The list is ordered round-robin across families,
 * so every tier (which unlocks the first N entries) gets every kind of brush.
 */
object BrushCatalog {

    const val VARIANTS_PER_FAMILY = 16

    val all: List<BrushPreset> by lazy { build() }

    val totalCount: Int get() = all.size

    fun byId(id: String): BrushPreset? = all.firstOrNull { it.id == id }

    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }

    /** Default brushes for the basic tools. */
    val defaultPencil: BrushPreset get() = all.first { it.family == BrushFamily.PENCIL }
    val defaultPen: BrushPreset get() = all.first { it.family == BrushFamily.PEN }
    val defaultBrush: BrushPreset get() = all.first { it.family == BrushFamily.PAINT }
    val defaultEraser: BrushPreset get() = all.first { it.family == BrushFamily.AIRBRUSH }.copy(hardness = 0.7f, size = 30f)

    private val sizes = floatArrayOf(4f, 8f, 14f, 24f, 40f, 2f, 64f, 6f, 18f, 30f, 10f, 50f, 3f, 80f, 12f, 120f)

    private fun build(): List<BrushPreset> {
        val families = BrushFamily.entries
        val result = ArrayList<BrushPreset>(families.size * VARIANTS_PER_FAMILY)
        for (v in 0 until VARIANTS_PER_FAMILY) {
            for (family in families) {
                result += recipe(family, v)
            }
        }
        return result
    }

    private fun recipe(family: BrushFamily, v: Int): BrushPreset {
        val id = "${family.name.lowercase()}_${v + 1}"
        val base = BrushPreset(id = id, family = family, variant = v + 1)
        val s = sizes[v]
        val alt = v % 4 // varies dynamics between variants
        return when (family) {
            BrushFamily.PENCIL -> base.copy(
                tip = BrushTip.GRAIN, size = (s * 0.35f).coerceIn(1.5f, 20f), opacity = 0.85f - alt * 0.1f,
                hardness = 0.7f, spacing = 0.08f, texture = 0.45f + alt * 0.1f, pressureSize = 0.4f,
                pressureOpacity = 0.7f, smoothing = 0.1f,
            )
            BrushFamily.PEN -> base.copy(
                tip = BrushTip.ROUND, size = (s * 0.4f).coerceIn(1f, 30f), hardness = 1f, spacing = 0.05f,
                pressureSize = if (alt == 0) 0f else 0.3f, smoothing = 0.3f + alt * 0.1f,
            )
            BrushFamily.INK -> base.copy(
                tip = BrushTip.ROUND, size = (s * 0.6f).coerceAtLeast(2f), hardness = 0.95f, spacing = 0.04f,
                pressureSize = 0.85f + alt * 0.05f, smoothing = 0.45f + alt * 0.1f,
            )
            BrushFamily.PAINT -> base.copy(
                tip = BrushTip.ROUND, size = s, hardness = 0.55f + alt * 0.1f, spacing = 0.08f, flow = 0.8f,
                pressureSize = 0.5f, pressureOpacity = 0.3f, texture = if (alt == 3) 0.3f else 0f,
            )
            BrushFamily.AIRBRUSH -> base.copy(
                tip = BrushTip.ROUND, size = s * 2f, hardness = 0.05f, spacing = 0.06f, flow = 0.08f + alt * 0.04f,
                opacity = 1f, pressureSize = 0.1f, pressureOpacity = 0.8f, smoothing = 0.2f,
            )
            BrushFamily.WATERCOLOR -> base.copy(
                tip = BrushTip.ROUND, size = s * 1.5f, hardness = 0.25f, spacing = 0.08f, flow = 0.12f + alt * 0.04f,
                opacity = 0.75f, wetEdges = 0.5f + alt * 0.1f, texture = 0.25f, pressureSize = 0.3f,
                pressureOpacity = 0.5f, sizeJitter = 0.1f, blendMode = LayerBlendMode.MULTIPLY,
            )
            BrushFamily.MARKER -> base.copy(
                tip = if (alt % 2 == 0) BrushTip.SQUARE else BrushTip.ROUND, size = s * 1.2f, hardness = 0.9f,
                spacing = 0.05f, opacity = 0.55f + alt * 0.08f, pressureSize = 0.15f, blendMode = LayerBlendMode.MULTIPLY,
            )
            BrushFamily.CALLIGRAPHY -> base.copy(
                tip = BrushTip.FLAT, size = s * 1.1f, hardness = 0.95f, spacing = 0.03f, roundness = 0.15f + alt * 0.05f,
                angle = 30f + alt * 15f, pressureSize = 0.5f, smoothing = 0.5f,
            )
            BrushFamily.TEXTURE -> base.copy(
                tip = BrushTip.GRAIN, size = s * 1.4f, hardness = 0.6f, spacing = 0.12f, texture = 0.7f + alt * 0.07f,
                opacity = 0.9f, sizeJitter = 0.2f, opacityJitter = 0.3f, pressureOpacity = 0.4f,
            )
            BrushFamily.CHARCOAL -> base.copy(
                tip = BrushTip.GRAIN, size = s * 1.2f, hardness = 0.45f, spacing = 0.07f, texture = 0.8f,
                opacity = 0.8f, pressureOpacity = 0.8f, pressureSize = 0.3f, scatter = 0.05f,
            )
            BrushFamily.CRAYON -> base.copy(
                tip = BrushTip.GRAIN, size = (s * 0.8f).coerceAtLeast(4f), hardness = 0.85f, spacing = 0.06f,
                texture = 0.6f + alt * 0.05f, pressureSize = 0.2f, pressureOpacity = 0.4f,
            )
            BrushFamily.SPRAY -> base.copy(
                tip = BrushTip.SPECKLE, size = s * 2.5f, hardness = 1f, spacing = 0.15f, flow = 0.5f,
                scatter = 0.1f + alt * 0.05f, pressureSize = 0.3f, pressureOpacity = 0.5f,
            )
            BrushFamily.PIXEL -> base.copy(
                tip = BrushTip.SQUARE, size = (v % 8 + 1).toFloat(), hardness = 1f, spacing = 0.5f,
                pressureSize = 0f, smoothing = 0f, pixelPerfect = true,
            )
        }
    }
}
