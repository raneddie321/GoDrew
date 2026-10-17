package com.godraw.app.data.settings

import com.godraw.core.color.Palette
import com.godraw.core.brush.BrushPreset

enum class ThemeMode { DARK, LIGHT, COLORFUL }

enum class WorkspaceMode { SIMPLE, ADVANCED }

/** Supported interface languages. [tag] is the BCP-47 tag; empty follows the system. */
enum class AppLanguage(val tag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    HEBREW("iw"),
    ARABIC("ar"),
    SPANISH("es"),
    FRENCH("fr"),
}

enum class StylusButtonAction { ERASER, EYEDROPPER, NONE }

enum class ProjectSort { RECENT, NAME, CREATED, SIZE }

enum class ExportDestination { GALLERY, ASK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val workspaceMode: WorkspaceMode = WorkspaceMode.SIMPLE,
    val autosaveEnabled: Boolean = true,
    val autosaveIntervalSec: Int = 30,
    val pressureEnabled: Boolean = true,
    /** Gamma applied to stylus pressure: < 1 softer, > 1 firmer. */
    val pressureCurve: Float = 1f,
    /** When false, fingers only navigate (pan/zoom) and drawing requires a stylus. */
    val fingerDrawing: Boolean = true,
    /** Ignores touches flagged as palms and finger touches while a stylus is in use. */
    val palmRejection: Boolean = true,
    val twoFingerTapUndo: Boolean = true,
    val threeFingerTapRedo: Boolean = true,
    val longPressEyedropper: Boolean = true,
    val stylusButtonAction: StylusButtonAction = StylusButtonAction.ERASER,
    val defaultStabilization: Float = 0.2f,
    val showBrushCursor: Boolean = true,
    val leftHanded: Boolean = false,
    val checkerboardBackground: Boolean = true,
    val exportQuality: Int = 92,
    val exportIncludeAudio: Boolean = true,
    val exportDestination: ExportDestination = ExportDestination.GALLERY,
    val projectSort: ProjectSort = ProjectSort.RECENT,
    /** Tool ids pinned to the quick toolbar (Pro toolbar customization). Empty = default set. */
    val quickTools: List<String> = emptyList(),
    /** Custom accent color (Max). Null uses the theme default. */
    val accentColor: Int? = null,
    val recentColors: List<Int> = emptyList(),
    val savedPalettes: List<Palette> = emptyList(),
    val customBrushes: List<BrushPreset> = emptyList(),
    val onboardingDone: Boolean = false,
)
