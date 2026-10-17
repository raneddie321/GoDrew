package com.godraw.app.engine

import android.graphics.BlendMode
import com.godraw.core.model.LayerBlendMode

fun LayerBlendMode.toAndroid(): BlendMode = when (this) {
    LayerBlendMode.NORMAL -> BlendMode.SRC_OVER
    LayerBlendMode.MULTIPLY -> BlendMode.MULTIPLY
    LayerBlendMode.SCREEN -> BlendMode.SCREEN
    LayerBlendMode.OVERLAY -> BlendMode.OVERLAY
    LayerBlendMode.DARKEN -> BlendMode.DARKEN
    LayerBlendMode.LIGHTEN -> BlendMode.LIGHTEN
    LayerBlendMode.COLOR_DODGE -> BlendMode.COLOR_DODGE
    LayerBlendMode.COLOR_BURN -> BlendMode.COLOR_BURN
    LayerBlendMode.HARD_LIGHT -> BlendMode.HARD_LIGHT
    LayerBlendMode.SOFT_LIGHT -> BlendMode.SOFT_LIGHT
    LayerBlendMode.DIFFERENCE -> BlendMode.DIFFERENCE
    LayerBlendMode.EXCLUSION -> BlendMode.EXCLUSION
    LayerBlendMode.ADD -> BlendMode.PLUS
    LayerBlendMode.HUE -> BlendMode.HUE
    LayerBlendMode.SATURATION -> BlendMode.SATURATION
    LayerBlendMode.COLOR -> BlendMode.COLOR
    LayerBlendMode.LUMINOSITY -> BlendMode.LUMINOSITY
}
