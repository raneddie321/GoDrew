package com.godraw.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object GoDrawColors {
    val Cyan = Color(0xFF00C6FF)
    val Blue = Color(0xFF1F6BFF)
    val Purple = Color(0xFF7B2FF7)
    val Magenta = Color(0xFFE91E8C)
    val Coral = Color(0xFFFF4D6D)
    val Orange = Color(0xFFFF8A00)
    val Gold = Color(0xFFFFC300)
    val Navy = Color(0xFF1B2133)

    val Charcoal = Color(0xFF12141A)
    val CharcoalSurface = Color(0xFF1B1E26)
    val CharcoalHigh = Color(0xFF252935)
    val CharcoalOutline = Color(0xFF3A3F4D)

    val Paper = Color(0xFFF6F7F9)

    val Violet = Color(0xFF190F2E)
    val VioletSurface = Color(0xFF24183F)
    val VioletHigh = Color(0xFF33235A)

    /** The brand gradient taken from the GoDraw logo. */
    val brandColors = listOf(Cyan, Blue, Purple, Magenta, Coral, Orange, Gold)
    val brandBrush: Brush get() = Brush.linearGradient(brandColors)
    val drawBrush: Brush get() = Brush.horizontalGradient(listOf(Blue, Purple, Magenta, Coral, Orange, Gold))
    val gBrush: Brush get() = Brush.linearGradient(listOf(Cyan, Blue, Purple, Magenta, Orange))

    // Tier accents
    val Free = Color(0xFF4CAF50)
    val Pro = Color(0xFF1F6BFF)
    val Max = Color(0xFFB44CFF)
}
