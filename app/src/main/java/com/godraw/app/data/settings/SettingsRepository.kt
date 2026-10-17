package com.godraw.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.godraw.core.brush.BrushPreset
import com.godraw.core.color.Palette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "godraw_settings")

/** Persists [AppSettings] with Jetpack DataStore. */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true }

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val workspace = stringPreferencesKey("workspace")
        val autosave = booleanPreferencesKey("autosave")
        val autosaveInterval = intPreferencesKey("autosave_interval")
        val pressure = booleanPreferencesKey("pressure")
        val pressureCurve = floatPreferencesKey("pressure_curve")
        val fingerDrawing = booleanPreferencesKey("finger_drawing")
        val palmRejection = booleanPreferencesKey("palm_rejection")
        val twoFingerUndo = booleanPreferencesKey("two_finger_undo")
        val threeFingerRedo = booleanPreferencesKey("three_finger_redo")
        val longPressEyedropper = booleanPreferencesKey("long_press_eyedropper")
        val stylusButton = stringPreferencesKey("stylus_button")
        val stabilization = floatPreferencesKey("stabilization")
        val brushCursor = booleanPreferencesKey("brush_cursor")
        val leftHanded = booleanPreferencesKey("left_handed")
        val checkerboard = booleanPreferencesKey("checkerboard")
        val exportQuality = intPreferencesKey("export_quality")
        val exportAudio = booleanPreferencesKey("export_audio")
        val exportDestination = stringPreferencesKey("export_destination")
        val projectSort = stringPreferencesKey("project_sort")
        val quickTools = stringPreferencesKey("quick_tools")
        val accentColor = intPreferencesKey("accent_color")
        val recentColors = stringPreferencesKey("recent_colors")
        val savedPalettes = stringPreferencesKey("saved_palettes")
        val customBrushes = stringPreferencesKey("custom_brushes")
        val onboardingDone = booleanPreferencesKey("onboarding_done")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p -> read(p) }

    suspend fun current(): AppSettings = settings.first()

    private inline fun <reified T : Enum<T>> enumOf(value: String?, default: T): T =
        value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = enumOf(p[Keys.theme], d.themeMode),
            workspaceMode = enumOf(p[Keys.workspace], d.workspaceMode),
            autosaveEnabled = p[Keys.autosave] ?: d.autosaveEnabled,
            autosaveIntervalSec = p[Keys.autosaveInterval] ?: d.autosaveIntervalSec,
            pressureEnabled = p[Keys.pressure] ?: d.pressureEnabled,
            pressureCurve = p[Keys.pressureCurve] ?: d.pressureCurve,
            fingerDrawing = p[Keys.fingerDrawing] ?: d.fingerDrawing,
            palmRejection = p[Keys.palmRejection] ?: d.palmRejection,
            twoFingerTapUndo = p[Keys.twoFingerUndo] ?: d.twoFingerTapUndo,
            threeFingerTapRedo = p[Keys.threeFingerRedo] ?: d.threeFingerTapRedo,
            longPressEyedropper = p[Keys.longPressEyedropper] ?: d.longPressEyedropper,
            stylusButtonAction = enumOf(p[Keys.stylusButton], d.stylusButtonAction),
            defaultStabilization = p[Keys.stabilization] ?: d.defaultStabilization,
            showBrushCursor = p[Keys.brushCursor] ?: d.showBrushCursor,
            leftHanded = p[Keys.leftHanded] ?: d.leftHanded,
            checkerboardBackground = p[Keys.checkerboard] ?: d.checkerboardBackground,
            exportQuality = p[Keys.exportQuality] ?: d.exportQuality,
            exportIncludeAudio = p[Keys.exportAudio] ?: d.exportIncludeAudio,
            exportDestination = enumOf(p[Keys.exportDestination], d.exportDestination),
            projectSort = enumOf(p[Keys.projectSort], d.projectSort),
            quickTools = p[Keys.quickTools]?.split(',')?.filter { it.isNotBlank() } ?: d.quickTools,
            accentColor = p[Keys.accentColor],
            recentColors = p[Keys.recentColors]?.split(',')?.mapNotNull { it.toIntOrNull() } ?: d.recentColors,
            savedPalettes = decodeList(p[Keys.savedPalettes], Palette.serializer()) ?: d.savedPalettes,
            customBrushes = decodeList(p[Keys.customBrushes], BrushPreset.serializer()) ?: d.customBrushes,
            onboardingDone = p[Keys.onboardingDone] ?: d.onboardingDone,
        )
    }

    private fun <T> decodeList(text: String?, serializer: kotlinx.serialization.KSerializer<T>): List<T>? =
        text?.let { runCatching { json.decodeFromString(ListSerializer(serializer), it) }.getOrNull() }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setWorkspaceMode(mode: WorkspaceMode) = store.edit { it[Keys.workspace] = mode.name }
    suspend fun setAutosave(enabled: Boolean) = store.edit { it[Keys.autosave] = enabled }
    suspend fun setAutosaveInterval(seconds: Int) = store.edit { it[Keys.autosaveInterval] = seconds.coerceIn(10, 600) }
    suspend fun setPressureEnabled(enabled: Boolean) = store.edit { it[Keys.pressure] = enabled }
    suspend fun setPressureCurve(gamma: Float) = store.edit { it[Keys.pressureCurve] = gamma.coerceIn(0.3f, 3f) }
    suspend fun setFingerDrawing(enabled: Boolean) = store.edit { it[Keys.fingerDrawing] = enabled }
    suspend fun setPalmRejection(enabled: Boolean) = store.edit { it[Keys.palmRejection] = enabled }
    suspend fun setTwoFingerTapUndo(enabled: Boolean) = store.edit { it[Keys.twoFingerUndo] = enabled }
    suspend fun setThreeFingerTapRedo(enabled: Boolean) = store.edit { it[Keys.threeFingerRedo] = enabled }
    suspend fun setLongPressEyedropper(enabled: Boolean) = store.edit { it[Keys.longPressEyedropper] = enabled }
    suspend fun setStylusButtonAction(action: StylusButtonAction) = store.edit { it[Keys.stylusButton] = action.name }
    suspend fun setDefaultStabilization(value: Float) = store.edit { it[Keys.stabilization] = value.coerceIn(0f, 1f) }
    suspend fun setShowBrushCursor(enabled: Boolean) = store.edit { it[Keys.brushCursor] = enabled }
    suspend fun setLeftHanded(enabled: Boolean) = store.edit { it[Keys.leftHanded] = enabled }
    suspend fun setCheckerboard(enabled: Boolean) = store.edit { it[Keys.checkerboard] = enabled }
    suspend fun setExportQuality(quality: Int) = store.edit { it[Keys.exportQuality] = quality.coerceIn(10, 100) }
    suspend fun setExportIncludeAudio(enabled: Boolean) = store.edit { it[Keys.exportAudio] = enabled }
    suspend fun setExportDestination(dest: ExportDestination) = store.edit { it[Keys.exportDestination] = dest.name }
    suspend fun setProjectSort(sort: ProjectSort) = store.edit { it[Keys.projectSort] = sort.name }
    suspend fun setQuickTools(ids: List<String>) = store.edit { it[Keys.quickTools] = ids.joinToString(",") }
    suspend fun setAccentColor(color: Int?) = store.edit {
        if (color == null) it.remove(Keys.accentColor) else it[Keys.accentColor] = color
    }
    suspend fun setOnboardingDone() = store.edit { it[Keys.onboardingDone] = true }

    suspend fun addRecentColor(color: Int) = store.edit { p ->
        val current = p[Keys.recentColors]?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        p[Keys.recentColors] = (listOf(color) + current.filter { it != color }).take(MAX_RECENT_COLORS).joinToString(",")
    }

    suspend fun setSavedPalettes(palettes: List<Palette>) = store.edit {
        it[Keys.savedPalettes] = json.encodeToString(ListSerializer(Palette.serializer()), palettes)
    }

    suspend fun setCustomBrushes(brushes: List<BrushPreset>) = store.edit {
        it[Keys.customBrushes] = json.encodeToString(ListSerializer(BrushPreset.serializer()), brushes)
    }

    companion object {
        const val MAX_RECENT_COLORS = 24
    }
}
