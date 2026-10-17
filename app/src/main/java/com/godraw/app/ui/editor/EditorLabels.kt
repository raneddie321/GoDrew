package com.godraw.app.ui.editor

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Pentagon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.godraw.app.R
import com.godraw.app.engine.EngineStrings
import com.godraw.app.engine.MessageKind
import com.godraw.app.engine.ToolType
import com.godraw.core.animation.EasingType
import com.godraw.core.animation.TransitionPreset
import com.godraw.core.brush.BrushFamily
import com.godraw.core.brush.BrushTip
import com.godraw.core.effects.EffectType
import com.godraw.core.entitlement.Feature
import com.godraw.core.model.LayerBlendMode
import com.godraw.core.model.SymmetryMode
import com.godraw.core.model.TransformProperty

/** Localized names used by the engine when it creates layers. */
class ResourceEngineStrings(private val context: Context) : EngineStrings {
    override fun layerName(number: Int) = context.getString(R.string.layer_default_name, number)
    override fun groupName(number: Int) = context.getString(R.string.group_default_name, number)
    override fun copyOf(name: String) = context.getString(R.string.layer_copy_name, name)
    override val mergedName: String get() = context.getString(R.string.layer_merged)
    override val flattenedName: String get() = context.getString(R.string.layer_flattened)
    override val textLayerName: String get() = context.getString(R.string.tool_text)
}

object GoDrawIcons {
    val Eraser: ImageVector = ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(14.1f, 3.3f)
            lineTo(20.7f, 9.9f)
            lineTo(11.6f, 19f)
            horizontalLineTo(7.4f)
            lineTo(3.3f, 14.9f)
            close()
            moveTo(13f, 19f)
            horizontalLineTo(21f)
            verticalLineTo(21f)
            horizontalLineTo(13f)
            close()
        }
    }.build()

    val Line: ImageVector = ImageVector.Builder("Line", 24.dp, 24.dp, 24f, 24f).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 18.6f)
            lineTo(18.6f, 4f)
            lineTo(20f, 5.4f)
            lineTo(5.4f, 20f)
            close()
        }
    }.build()
}

fun toolIcon(tool: ToolType): ImageVector = when (tool) {
    ToolType.BRUSH -> Icons.Filled.Brush
    ToolType.PENCIL -> Icons.Filled.Edit
    ToolType.PEN -> Icons.Filled.HistoryEdu
    ToolType.ERASER -> GoDrawIcons.Eraser
    ToolType.FILL -> Icons.Filled.FormatColorFill
    ToolType.EYEDROPPER -> Icons.Filled.Colorize
    ToolType.LINE -> GoDrawIcons.Line
    ToolType.RECTANGLE -> Icons.Outlined.CropSquare
    ToolType.ELLIPSE -> Icons.Outlined.Circle
    ToolType.POLYGON -> Icons.Outlined.Pentagon
    ToolType.TEXT -> Icons.Filled.TextFields
    ToolType.SELECT_RECT -> Icons.Filled.HighlightAlt
    ToolType.SELECT_LASSO -> Icons.Filled.Gesture
    ToolType.TRANSFORM -> Icons.Filled.OpenWith
    ToolType.ANIMATE -> Icons.Filled.Animation
    ToolType.HAND -> Icons.Filled.PanTool
}

@StringRes
fun toolName(tool: ToolType): Int = when (tool) {
    ToolType.BRUSH -> R.string.tool_brush
    ToolType.PENCIL -> R.string.tool_pencil
    ToolType.PEN -> R.string.tool_pen
    ToolType.ERASER -> R.string.tool_eraser
    ToolType.FILL -> R.string.tool_fill
    ToolType.EYEDROPPER -> R.string.tool_eyedropper
    ToolType.LINE -> R.string.tool_line
    ToolType.RECTANGLE -> R.string.tool_rectangle
    ToolType.ELLIPSE -> R.string.tool_ellipse
    ToolType.POLYGON -> R.string.tool_polygon
    ToolType.TEXT -> R.string.tool_text
    ToolType.SELECT_RECT -> R.string.tool_select_rect
    ToolType.SELECT_LASSO -> R.string.tool_select_lasso
    ToolType.TRANSFORM -> R.string.tool_transform
    ToolType.ANIMATE -> R.string.tool_animate
    ToolType.HAND -> R.string.tool_hand
}

@StringRes
fun familyName(family: BrushFamily): Int = when (family) {
    BrushFamily.PENCIL -> R.string.family_pencil
    BrushFamily.PEN -> R.string.family_pen
    BrushFamily.INK -> R.string.family_ink
    BrushFamily.PAINT -> R.string.family_paint
    BrushFamily.AIRBRUSH -> R.string.family_airbrush
    BrushFamily.WATERCOLOR -> R.string.family_watercolor
    BrushFamily.MARKER -> R.string.family_marker
    BrushFamily.CALLIGRAPHY -> R.string.family_calligraphy
    BrushFamily.TEXTURE -> R.string.family_texture
    BrushFamily.CHARCOAL -> R.string.family_charcoal
    BrushFamily.CRAYON -> R.string.family_crayon
    BrushFamily.SPRAY -> R.string.family_spray
    BrushFamily.PIXEL -> R.string.family_pixel
}

@StringRes
fun tipName(tip: BrushTip): Int = when (tip) {
    BrushTip.ROUND -> R.string.tip_round
    BrushTip.SQUARE -> R.string.tip_square
    BrushTip.FLAT -> R.string.tip_flat
    BrushTip.GRAIN -> R.string.tip_grain
    BrushTip.SPECKLE -> R.string.tip_speckle
}

@StringRes
fun blendName(mode: LayerBlendMode): Int = when (mode) {
    LayerBlendMode.NORMAL -> R.string.blend_normal
    LayerBlendMode.MULTIPLY -> R.string.blend_multiply
    LayerBlendMode.SCREEN -> R.string.blend_screen
    LayerBlendMode.OVERLAY -> R.string.blend_overlay
    LayerBlendMode.DARKEN -> R.string.blend_darken
    LayerBlendMode.LIGHTEN -> R.string.blend_lighten
    LayerBlendMode.COLOR_DODGE -> R.string.blend_color_dodge
    LayerBlendMode.COLOR_BURN -> R.string.blend_color_burn
    LayerBlendMode.HARD_LIGHT -> R.string.blend_hard_light
    LayerBlendMode.SOFT_LIGHT -> R.string.blend_soft_light
    LayerBlendMode.DIFFERENCE -> R.string.blend_difference
    LayerBlendMode.EXCLUSION -> R.string.blend_exclusion
    LayerBlendMode.ADD -> R.string.blend_add
    LayerBlendMode.HUE -> R.string.blend_hue
    LayerBlendMode.SATURATION -> R.string.blend_saturation
    LayerBlendMode.COLOR -> R.string.blend_color
    LayerBlendMode.LUMINOSITY -> R.string.blend_luminosity
}

@StringRes
fun effectName(type: EffectType): Int = when (type) {
    EffectType.BLUR -> R.string.fx_blur
    EffectType.GLOW -> R.string.fx_glow
    EffectType.DROP_SHADOW -> R.string.fx_drop_shadow
    EffectType.BRIGHTNESS_CONTRAST -> R.string.fx_brightness_contrast
    EffectType.HUE_SATURATION -> R.string.fx_hue_saturation
    EffectType.GRAYSCALE -> R.string.fx_grayscale
    EffectType.SEPIA -> R.string.fx_sepia
    EffectType.INVERT -> R.string.fx_invert
    EffectType.POSTERIZE -> R.string.fx_posterize
    EffectType.THRESHOLD -> R.string.fx_threshold
    EffectType.PIXELATE -> R.string.fx_pixelate
    EffectType.SHARPEN -> R.string.fx_sharpen
    EffectType.EMBOSS -> R.string.fx_emboss
    EffectType.EDGE_DETECT -> R.string.fx_edge_detect
    EffectType.VIGNETTE -> R.string.fx_vignette
    EffectType.NOISE -> R.string.fx_noise
    EffectType.MOTION_BLUR -> R.string.fx_motion_blur
    EffectType.OUTLINE -> R.string.fx_outline
    EffectType.INNER_SHADOW -> R.string.fx_inner_shadow
    EffectType.COLOR_BALANCE -> R.string.fx_color_balance
    EffectType.EXPOSURE -> R.string.fx_exposure
    EffectType.TEMPERATURE -> R.string.fx_temperature
    EffectType.DUOTONE -> R.string.fx_duotone
    EffectType.GRADIENT_MAP -> R.string.fx_gradient_map
    EffectType.CHROMATIC_ABERRATION -> R.string.fx_chromatic
    EffectType.HALFTONE -> R.string.fx_halftone
    EffectType.OIL_PAINT -> R.string.fx_oil_paint
    EffectType.SPIN_BLUR -> R.string.fx_spin_blur
    EffectType.ZOOM_BLUR -> R.string.fx_zoom_blur
    EffectType.LONG_SHADOW -> R.string.fx_long_shadow
    EffectType.NEON_EDGES -> R.string.fx_neon
    EffectType.SOLARIZE -> R.string.fx_solarize
    EffectType.GLITCH -> R.string.fx_glitch
    EffectType.BLOOM -> R.string.fx_bloom
    EffectType.CROSSHATCH -> R.string.fx_crosshatch
}

@StringRes
fun effectParamName(key: String): Int = when (key) {
    "radius" -> R.string.param_radius
    "strength" -> R.string.param_strength
    "distance" -> R.string.param_distance
    "blur" -> R.string.param_blur
    "brightness" -> R.string.param_brightness
    "contrast" -> R.string.param_contrast
    "hue" -> R.string.param_hue
    "saturation" -> R.string.param_saturation
    "amount" -> R.string.param_amount
    "levels" -> R.string.param_levels
    "level" -> R.string.param_level
    "size" -> R.string.param_size
    "angle" -> R.string.param_angle
    "width" -> R.string.param_width
    "red" -> R.string.param_red
    "blue" -> R.string.param_blue
    "stops" -> R.string.param_stops
    "warmth" -> R.string.param_warmth
    "offset" -> R.string.param_offset
    "cell" -> R.string.param_cell
    "length" -> R.string.param_length
    "threshold" -> R.string.param_threshold
    "spacing" -> R.string.param_spacing
    else -> R.string.param_amount
}

@StringRes
fun easingName(easing: EasingType): Int = when (easing) {
    EasingType.LINEAR -> R.string.ease_linear
    EasingType.EASE_IN -> R.string.ease_in
    EasingType.EASE_OUT -> R.string.ease_out
    EasingType.EASE_IN_OUT -> R.string.ease_in_out
    EasingType.STEP -> R.string.ease_step
    EasingType.EASE_IN_CUBIC -> R.string.ease_in_cubic
    EasingType.EASE_OUT_CUBIC -> R.string.ease_out_cubic
    EasingType.EASE_IN_BACK -> R.string.ease_in_back
    EasingType.EASE_OUT_BACK -> R.string.ease_out_back
    EasingType.ELASTIC -> R.string.ease_elastic
    EasingType.BOUNCE -> R.string.ease_bounce
    EasingType.CUSTOM_BEZIER -> R.string.ease_bezier
}

@StringRes
fun transitionName(t: TransitionPreset): Int = when (t) {
    TransitionPreset.FADE_IN -> R.string.tr_fade_in
    TransitionPreset.FADE_OUT -> R.string.tr_fade_out
    TransitionPreset.SLIDE_IN_LEFT -> R.string.tr_slide_left
    TransitionPreset.SLIDE_IN_RIGHT -> R.string.tr_slide_right
    TransitionPreset.SLIDE_IN_TOP -> R.string.tr_slide_top
    TransitionPreset.SLIDE_IN_BOTTOM -> R.string.tr_slide_bottom
    TransitionPreset.ZOOM_IN -> R.string.tr_zoom_in
    TransitionPreset.ZOOM_OUT -> R.string.tr_zoom_out
    TransitionPreset.SPIN_IN -> R.string.tr_spin_in
}

@StringRes
fun propertyName(p: TransformProperty): Int = when (p) {
    TransformProperty.POSITION_X -> R.string.prop_x
    TransformProperty.POSITION_Y -> R.string.prop_y
    TransformProperty.ROTATION -> R.string.prop_rotation
    TransformProperty.SCALE -> R.string.prop_scale
    TransformProperty.OPACITY -> R.string.prop_opacity
}

@StringRes
fun symmetryName(m: SymmetryMode): Int = when (m) {
    SymmetryMode.NONE -> R.string.none
    SymmetryMode.VERTICAL -> R.string.sym_vertical
    SymmetryMode.HORIZONTAL -> R.string.sym_horizontal
    SymmetryMode.QUADRANT -> R.string.sym_quadrant
    SymmetryMode.RADIAL -> R.string.sym_radial
}

@StringRes
fun featureName(f: Feature): Int = when (f) {
    Feature.CUSTOM_BRUSHES -> R.string.feat_custom_brushes
    Feature.LASSO_SELECTION -> R.string.feat_lasso
    Feature.DISTORT_TRANSFORM -> R.string.feat_distort
    Feature.PERSPECTIVE_GUIDES -> R.string.feat_perspective
    Feature.RADIAL_SYMMETRY -> R.string.feat_radial
    Feature.LAYER_GROUPS -> R.string.feat_groups
    Feature.LAYER_MASKS -> R.string.feat_masks
    Feature.CLIPPING_MASKS -> R.string.feat_clipping
    Feature.ADVANCED_EASING -> R.string.feat_easing
    Feature.ADVANCED_TRANSITIONS -> R.string.feat_transitions
    Feature.KEYFRAME_COPY_PASTE -> R.string.feat_key_copy
    Feature.TIMELINE_ZOOM -> R.string.feat_timeline_zoom
    Feature.MULTI_FRAME_SELECTION -> R.string.feat_multi_frame
    Feature.CUSTOM_BEZIER_EASING -> R.string.feat_bezier
    Feature.SMOOTH_MOTION_PATHS -> R.string.feat_motion_paths
    Feature.MULTIPLE_AUDIO_CLIPS -> R.string.feat_multi_audio
    Feature.PNG_SEQUENCE_EXPORT -> R.string.feat_png_sequence
    Feature.GIF_DITHERING -> R.string.feat_gif_dither
    Feature.HIGH_BITRATE_VIDEO -> R.string.feat_bitrate
    Feature.TOOLBAR_CUSTOMIZATION -> R.string.feat_toolbar
    Feature.CUSTOM_ACCENT_COLOR -> R.string.feat_accent
}

@StringRes
fun messageText(kind: MessageKind): Int = when (kind) {
    MessageKind.LAYER_LOCKED -> R.string.msg_layer_locked
    MessageKind.LAYER_HIDDEN -> R.string.msg_layer_hidden
    MessageKind.NOT_A_RASTER_LAYER -> R.string.msg_not_raster
    MessageKind.NOTHING_SELECTED -> R.string.msg_nothing_selected
    MessageKind.CANNOT_DELETE_LAST_LAYER -> R.string.msg_last_layer
    MessageKind.MERGE_NOT_POSSIBLE -> R.string.msg_merge_impossible
    MessageKind.CLIPBOARD_EMPTY -> R.string.msg_clipboard_empty
    MessageKind.OPERATION_FAILED -> R.string.error_generic
}
