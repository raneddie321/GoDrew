package com.godraw.core.model

import com.godraw.core.animation.EasingType
import kotlinx.serialization.Serializable

@Serializable
enum class ProjectType { DRAWING, ANIMATION }

@Serializable
enum class LayerBlendMode {
    NORMAL,
    MULTIPLY,
    SCREEN,
    OVERLAY,
    DARKEN,
    LIGHTEN,
    COLOR_DODGE,
    COLOR_BURN,
    HARD_LIGHT,
    SOFT_LIGHT,
    DIFFERENCE,
    EXCLUSION,
    ADD,
    HUE,
    SATURATION,
    COLOR,
    LUMINOSITY,
}

@Serializable
enum class LayerKind { RASTER, GROUP }

/** Animatable transform properties of a layer. */
@Serializable
enum class TransformProperty(val defaultValue: Float) {
    POSITION_X(0f),
    POSITION_Y(0f),
    ROTATION(0f),
    SCALE(1f),
    OPACITY(1f),
}

@Serializable
data class Keyframe(
    /** Timeline frame index (0-based). */
    val frame: Int,
    val value: Float,
    /** Easing used from this keyframe to the next one. */
    val easing: EasingType = EasingType.LINEAR,
    /** Control points x1, y1, x2, y2 when [easing] is [EasingType.CUSTOM_BEZIER]. */
    val bezier: List<Float>? = null,
)

@Serializable
enum class MotionPathMode { LINEAR, SMOOTH }

@Serializable
data class LayerAnimation(
    val tracks: Map<TransformProperty, List<Keyframe>> = emptyMap(),
    val pathMode: MotionPathMode = MotionPathMode.LINEAR,
) {
    val isAnimated: Boolean get() = tracks.values.any { it.isNotEmpty() }

    fun track(property: TransformProperty): List<Keyframe> = tracks[property].orEmpty()

    fun withTrack(property: TransformProperty, keyframes: List<Keyframe>): LayerAnimation {
        val sorted = keyframes.sortedBy { it.frame }
        return copy(tracks = if (sorted.isEmpty()) tracks - property else tracks + (property to sorted))
    }
}

@Serializable
data class Layer(
    val id: String,
    val name: String,
    val kind: LayerKind = LayerKind.RASTER,
    /** Id of the containing group, or null for top-level layers. Groups are not nested. */
    val parentId: String? = null,
    val visible: Boolean = true,
    val locked: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: LayerBlendMode = LayerBlendMode.NORMAL,
    /** Clipping mask: this layer is only visible where the layer below it has pixels. */
    val clipToBelow: Boolean = false,
    val hasMask: Boolean = false,
    val maskEnabled: Boolean = true,
    val isBackground: Boolean = false,
    /** A static layer has one drawing shared by every animation frame (useful for backgrounds). */
    val isStatic: Boolean = false,
    val collapsed: Boolean = false,
    val animation: LayerAnimation = LayerAnimation(),
) {
    val isGroup: Boolean get() = kind == LayerKind.GROUP
}

@Serializable
data class Frame(
    val id: String,
    /** How many timeline ticks this drawing is held for (1 = a single frame). */
    val hold: Int = 1,
)

@Serializable
data class AudioClip(
    val id: String,
    /** File name inside the project's `audio/` directory. */
    val fileName: String,
    val displayName: String,
    val durationMs: Long,
    /** Position on the timeline where the clip starts playing. */
    val startMs: Long = 0,
    val trimStartMs: Long = 0,
    /** End of the used region inside the source; null plays to the end of the source. */
    val trimEndMs: Long? = null,
    val volume: Float = 1f,
    val muted: Boolean = false,
) {
    val effectiveEndMs: Long get() = (trimEndMs ?: durationMs).coerceIn(trimStartMs, durationMs)
    val playedLengthMs: Long get() = (effectiveEndMs - trimStartMs).coerceAtLeast(0)
}

@Serializable
data class OnionSkinSettings(
    val enabled: Boolean = false,
    val framesBefore: Int = 1,
    val framesAfter: Int = 1,
    val opacity: Float = 0.35f,
    val beforeColor: Int = 0xFFE53935.toInt(),
    val afterColor: Int = 0xFF1E88E5.toInt(),
    val tint: Boolean = true,
)

@Serializable
enum class SymmetryMode { NONE, VERTICAL, HORIZONTAL, QUADRANT, RADIAL }

@Serializable
data class GuideSettings(
    val symmetry: SymmetryMode = SymmetryMode.NONE,
    val radialSegments: Int = 6,
    val showGrid: Boolean = false,
    val gridSize: Int = 64,
    val rulerEnabled: Boolean = false,
    /** Ruler line endpoints in canvas coordinates. */
    val rulerX1: Float = 0f,
    val rulerY1: Float = 0f,
    val rulerX2: Float = 0f,
    val rulerY2: Float = 0f,
    val perspectiveEnabled: Boolean = false,
    /** Vanishing points in canvas coordinates; 1 or 2 points are supported. */
    val vanishingPoints: List<Float> = emptyList(),
    val horizonY: Float? = null,
)

@Serializable
data class ProjectDocument(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val id: String,
    val name: String,
    val type: ProjectType,
    val width: Int,
    val height: Int,
    /** ARGB background; 0 means transparent. */
    val backgroundColor: Int,
    val fps: Int = 12,
    val loop: Boolean = true,
    /** Layers from bottom (index 0) to top. */
    val layers: List<Layer>,
    val frames: List<Frame>,
    val activeLayerId: String,
    val activeFrameIndex: Int = 0,
    val audioClips: List<AudioClip> = emptyList(),
    val onionSkin: OnionSkinSettings = OnionSkinSettings(),
    val guides: GuideSettings = GuideSettings(),
    val createdAt: Long,
    val modifiedAt: Long,
) {
    val isAnimation: Boolean get() = type == ProjectType.ANIMATION
    val rasterLayers: List<Layer> get() = layers.filter { !it.isGroup }
    val rasterLayerCount: Int get() = layers.count { !it.isGroup }

    fun layer(id: String): Layer? = layers.firstOrNull { it.id == id }
    fun layerIndex(id: String): Int = layers.indexOfFirst { it.id == id }
    fun children(groupId: String): List<Layer> = layers.filter { it.parentId == groupId }

    /** Total timeline length in ticks (each tick is 1/fps seconds). */
    val totalTicks: Int get() = frames.sumOf { it.hold.coerceAtLeast(1) }

    companion object {
        const val CURRENT_FORMAT_VERSION = 1
        const val STATIC_FRAME_ID = "static"
    }
}

/** Identifies the bitmap of one layer on one frame (or the layer's mask). */
data class CellKey(val frameId: String, val layerId: String, val isMask: Boolean = false) {
    val fileName: String get() = (if (isMask) "mask_" else "cell_") + frameId + "_" + layerId + ".png"

    companion object {
        fun forLayer(layer: Layer, frameId: String, isMask: Boolean = false): CellKey =
            CellKey(if (layer.isStatic) ProjectDocument.STATIC_FRAME_ID else frameId, layer.id, isMask)
    }
}
