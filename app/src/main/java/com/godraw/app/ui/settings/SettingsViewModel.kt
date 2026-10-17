package com.godraw.app.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.godraw.app.AppContainer
import com.godraw.app.R
import com.godraw.app.data.settings.AppLanguage
import com.godraw.app.data.settings.AppSettings
import com.godraw.app.data.settings.ExportDestination
import com.godraw.app.data.settings.StylusButtonAction
import com.godraw.app.data.settings.ThemeMode
import com.godraw.app.data.settings.WorkspaceMode
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Feature
import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream

sealed interface SettingsEvent {
    data class Message(val text: Int, val count: Int? = null) : SettingsEvent
    data class Locked(val access: Access.Locked) : SettingsEvent
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.settings

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val tier: StateFlow<Tier> = container.entitlements.tier
    val simulator = container.entitlements.simulator

    private val events = Channel<SettingsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    fun currentLanguage(): AppLanguage {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tag.isBlank()) return AppLanguage.SYSTEM
        val lang = tag.substringBefore(',').substringBefore('-')
        val normalized = if (lang == "he") "iw" else lang
        return AppLanguage.entries.firstOrNull { it.tag == normalized } ?: AppLanguage.SYSTEM
    }

    fun setLanguage(language: AppLanguage) {
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(language.tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    fun setTheme(mode: ThemeMode) = launch { repo.setTheme(mode) }
    fun setWorkspace(mode: WorkspaceMode) = launch { repo.setWorkspaceMode(mode) }
    fun setAutosave(enabled: Boolean) = launch { repo.setAutosave(enabled) }
    fun setAutosaveInterval(seconds: Int) = launch { repo.setAutosaveInterval(seconds) }
    fun setPressure(enabled: Boolean) = launch { repo.setPressureEnabled(enabled) }
    fun setPressureCurve(value: Float) = launch { repo.setPressureCurve(value) }
    fun setFingerDrawing(enabled: Boolean) = launch { repo.setFingerDrawing(enabled) }
    fun setPalmRejection(enabled: Boolean) = launch { repo.setPalmRejection(enabled) }
    fun setTwoFingerUndo(enabled: Boolean) = launch { repo.setTwoFingerTapUndo(enabled) }
    fun setThreeFingerRedo(enabled: Boolean) = launch { repo.setThreeFingerTapRedo(enabled) }
    fun setLongPressEyedropper(enabled: Boolean) = launch { repo.setLongPressEyedropper(enabled) }
    fun setStylusButton(action: StylusButtonAction) = launch { repo.setStylusButtonAction(action) }
    fun setStabilization(value: Float) = launch { repo.setDefaultStabilization(value) }
    fun setBrushCursor(enabled: Boolean) = launch { repo.setShowBrushCursor(enabled) }
    fun setLeftHanded(enabled: Boolean) = launch { repo.setLeftHanded(enabled) }
    fun setCheckerboard(enabled: Boolean) = launch { repo.setCheckerboard(enabled) }
    fun setExportQuality(value: Int) = launch { repo.setExportQuality(value) }
    fun setExportAudio(enabled: Boolean) = launch { repo.setExportIncludeAudio(enabled) }
    fun setExportDestination(dest: ExportDestination) = launch { repo.setExportDestination(dest) }

    fun setAccentColor(color: Int?) = launch {
        val access = container.entitlements.currentGate.check(Feature.CUSTOM_ACCENT_COLOR)
        if (color != null && access is Access.Locked) {
            events.send(SettingsEvent.Locked(access))
        } else {
            repo.setAccentColor(color)
        }
    }

    fun setSimulatedTier(tier: Tier?) = simulator.set(tier)

    fun backup(output: () -> OutputStream?) = launch {
        val result = runCatching {
            val out = output() ?: error("No output")
            out.use { container.projects.backupAll(it) }
        }
        result.onSuccess { events.send(SettingsEvent.Message(R.string.backup_done, it)) }
            .onFailure { events.send(SettingsEvent.Message(R.string.backup_failed)) }
    }

    fun restore(input: () -> InputStream?) = launch {
        val result = runCatching {
            val inp = input() ?: error("No input")
            inp.use { container.projects.restoreBackup(it) }
        }
        result.onSuccess { events.send(SettingsEvent.Message(R.string.restore_done, it)) }
            .onFailure { events.send(SettingsEvent.Message(R.string.restore_failed)) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
