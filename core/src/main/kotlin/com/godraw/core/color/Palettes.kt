package com.godraw.core.color

import kotlinx.serialization.Serializable

@Serializable
data class Palette(val id: String, val name: String, val colors: List<Int>)

/** Built-in palettes. Names are resolved to localized strings by the app via [id]. */
object Palettes {
    private fun c(hex: Long) = hex.toInt()

    val DEFAULT = Palette(
        "default", "default",
        listOf(
            c(0xFF000000), c(0xFF424242), c(0xFF9E9E9E), c(0xFFFFFFFF), c(0xFFE53935), c(0xFFFF7043),
            c(0xFFFFB300), c(0xFFFDD835), c(0xFF7CB342), c(0xFF00897B), c(0xFF00ACC1), c(0xFF1E88E5),
            c(0xFF3949AB), c(0xFF8E24AA), c(0xFFD81B60), c(0xFF6D4C41),
        ),
    )
    val SKIN = Palette(
        "skin", "skin",
        listOf(c(0xFFFFE0BD), c(0xFFFFCD94), c(0xFFEAC086), c(0xFFFFAD60), c(0xFFC68642), c(0xFF8D5524), c(0xFF5C3A1E), c(0xFF3B2219)),
    )
    val PASTEL = Palette(
        "pastel", "pastel",
        listOf(c(0xFFFFB3BA), c(0xFFFFDFBA), c(0xFFFFFFBA), c(0xFFBAFFC9), c(0xFFBAE1FF), c(0xFFD7BAFF), c(0xFFFFC6E5), c(0xFFE2F0CB)),
    )
    val GODRAW = Palette(
        "godraw", "godraw",
        listOf(c(0xFF00C6FF), c(0xFF1F6BFF), c(0xFF7B2FF7), c(0xFFE91E8C), c(0xFFFF4D6D), c(0xFFFF8A00), c(0xFFFFC300), c(0xFF1B2133)),
    )
    val GRAYSCALE = Palette(
        "grayscale", "grayscale",
        (0..10).map { i -> val v = i * 255 / 10; ColorUtils.argb(255, v, v, v) },
    )

    val builtIn: List<Palette> = listOf(DEFAULT, GODRAW, SKIN, PASTEL, GRAYSCALE)
}
