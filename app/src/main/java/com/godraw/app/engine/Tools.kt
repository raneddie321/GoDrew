package com.godraw.app.engine

import com.godraw.core.brush.BrushCatalog
import com.godraw.core.brush.BrushPreset
import com.godraw.core.entitlement.Feature

enum class ToolType(val id: String, val requiredFeature: Feature? = null) {
    BRUSH("brush"),
    PENCIL("pencil"),
    PEN("pen"),
    ERASER("eraser"),
    FILL("fill"),
    EYEDROPPER("eyedropper"),
    LINE("line"),
    RECTANGLE("rectangle"),
    ELLIPSE("ellipse"),
    POLYGON("polygon"),
    TEXT("text"),
    SELECT_RECT("select_rect"),
    SELECT_LASSO("select_lasso", Feature.LASSO_SELECTION),
    TRANSFORM("transform"),
    ANIMATE("animate"),
    HAND("hand"),
    ;

    val isPainting: Boolean get() = this == BRUSH || this == PENCIL || this == PEN || this == ERASER
    val isShape: Boolean get() = this == LINE || this == RECTANGLE || this == ELLIPSE || this == POLYGON
    val isSelection: Boolean get() = this == SELECT_RECT || this == SELECT_LASSO
    /** Tools that modify pixels of the active layer. */
    val editsPixels: Boolean get() = isPainting || isShape || this == FILL || this == TEXT || this == TRANSFORM

    companion object {
        fun byId(id: String): ToolType? = entries.firstOrNull { it.id == id }

        val simpleModeTools = listOf(BRUSH, PENCIL, ERASER, FILL, EYEDROPPER, RECTANGLE, SELECT_RECT, TRANSFORM, TEXT)
        val advancedModeTools = listOf(
            BRUSH, PENCIL, PEN, ERASER, FILL, EYEDROPPER, LINE, RECTANGLE, ELLIPSE, POLYGON, TEXT,
            SELECT_RECT, SELECT_LASSO, TRANSFORM, ANIMATE, HAND,
        )
    }
}

data class FillSettings(
    val tolerance: Int = 32,
    val sampleAllLayers: Boolean = true,
    val expand: Int = 1,
)

data class ShapeSettings(
    val filled: Boolean = false,
    val sides: Int = 5,
    val star: Boolean = false,
)

data class TextSpec(
    val text: String,
    val sizePx: Float,
    val bold: Boolean,
    val italic: Boolean,
    val font: TextFont,
)

enum class TextFont { SANS, SERIF, MONO, HANDWRITING }

/** Default brushes for each painting tool. */
fun defaultBrushFor(tool: ToolType): BrushPreset = when (tool) {
    ToolType.PENCIL -> BrushCatalog.defaultPencil
    ToolType.PEN -> BrushCatalog.defaultPen
    ToolType.ERASER -> BrushCatalog.defaultEraser
    else -> BrushCatalog.defaultBrush
}
