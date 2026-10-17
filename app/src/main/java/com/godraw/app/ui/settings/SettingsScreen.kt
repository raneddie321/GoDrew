package com.godraw.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.godraw.app.AppContainer
import com.godraw.app.R
import com.godraw.app.data.settings.AppLanguage
import com.godraw.app.data.settings.ExportDestination
import com.godraw.app.data.settings.StylusButtonAction
import com.godraw.app.data.settings.ThemeMode
import com.godraw.app.data.settings.WorkspaceMode
import com.godraw.app.ui.common.LockMarker
import com.godraw.app.ui.common.TierBadge
import com.godraw.app.ui.common.UpgradeDialog
import com.godraw.app.ui.common.tierName
import com.godraw.app.ui.theme.GoDrawColors
import com.godraw.core.entitlement.Access
import com.godraw.core.entitlement.Tier
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun languageName(language: AppLanguage): Int = when (language) {
    AppLanguage.SYSTEM -> R.string.language_system
    AppLanguage.ENGLISH -> R.string.language_english
    AppLanguage.HEBREW -> R.string.language_hebrew
    AppLanguage.ARABIC -> R.string.language_arabic
    AppLanguage.SPANISH -> R.string.language_spanish
    AppLanguage.FRENCH -> R.string.language_french
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onOpenSubscription: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
    val s by vm.settings.collectAsStateWithLifecycle()
    val tier by vm.tier.collectAsStateWithLifecycle()
    val simulated by vm.simulator.override.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var languageDialog by remember { mutableStateOf(false) }
    var privacyDialog by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf<Access.Locked?>(null) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backup { context.contentResolver.openOutputStream(uri) }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restore { context.contentResolver.openInputStream(uri) }
    }

    LaunchedEffect(vm) {
        vm.eventFlow.collect { e ->
            when (e) {
                is SettingsEvent.Message -> scope.launch {
                    val text = if (e.count != null) context.getString(e.text, e.count) else context.getString(e.text)
                    snackbar.showSnackbar(text)
                }
                is SettingsEvent.Locked -> locked = e.access
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Group(stringResource(R.string.settings_subscription)) {
                NavRow(
                    title = stringResource(R.string.current_plan),
                    trailing = { TierBadge(tier) },
                    onClick = onOpenSubscription,
                )
            }
            Group(stringResource(R.string.settings_appearance)) {
                NavRow(
                    title = stringResource(R.string.language),
                    subtitle = stringResource(languageName(vm.currentLanguage())),
                    onClick = { languageDialog = true },
                )
                LabeledSegments(
                    stringResource(R.string.theme),
                    ThemeMode.entries,
                    s.themeMode,
                    {
                        stringResource(
                            when (it) {
                                ThemeMode.DARK -> R.string.theme_dark
                                ThemeMode.LIGHT -> R.string.theme_light
                                ThemeMode.COLORFUL -> R.string.theme_colorful
                            },
                        )
                    },
                    vm::setTheme,
                )
                LabeledSegments(
                    stringResource(R.string.workspace_mode),
                    WorkspaceMode.entries,
                    s.workspaceMode,
                    { stringResource(if (it == WorkspaceMode.SIMPLE) R.string.mode_simple else R.string.mode_advanced) },
                    vm::setWorkspace,
                )
                AccentColorRow(s.accentColor, tier, vm::setAccentColor)
                SwitchRow(stringResource(R.string.left_handed), stringResource(R.string.left_handed_desc), s.leftHanded, vm::setLeftHanded)
            }
            Group(stringResource(R.string.settings_gestures)) {
                SwitchRow(stringResource(R.string.two_finger_undo), null, s.twoFingerTapUndo, vm::setTwoFingerUndo)
                SwitchRow(stringResource(R.string.three_finger_redo), null, s.threeFingerTapRedo, vm::setThreeFingerRedo)
                SwitchRow(stringResource(R.string.long_press_eyedropper), null, s.longPressEyedropper, vm::setLongPressEyedropper)
            }
            Group(stringResource(R.string.settings_stylus)) {
                SwitchRow(stringResource(R.string.pressure_sensitivity), stringResource(R.string.pressure_sensitivity_desc), s.pressureEnabled, vm::setPressure)
                SliderRow(
                    stringResource(R.string.pressure_curve),
                    s.pressureCurve,
                    0.3f..3f,
                    { "%.1f".format(it) },
                    vm::setPressureCurve,
                )
                SwitchRow(stringResource(R.string.finger_drawing), stringResource(R.string.finger_drawing_desc), s.fingerDrawing, vm::setFingerDrawing)
                SwitchRow(stringResource(R.string.palm_rejection), stringResource(R.string.palm_rejection_desc), s.palmRejection, vm::setPalmRejection)
                LabeledSegments(
                    stringResource(R.string.stylus_button),
                    StylusButtonAction.entries,
                    s.stylusButtonAction,
                    {
                        stringResource(
                            when (it) {
                                StylusButtonAction.ERASER -> R.string.tool_eraser
                                StylusButtonAction.EYEDROPPER -> R.string.tool_eyedropper
                                StylusButtonAction.NONE -> R.string.none
                            },
                        )
                    },
                    vm::setStylusButton,
                )
            }
            Group(stringResource(R.string.settings_brushes)) {
                SliderRow(
                    stringResource(R.string.default_stabilization),
                    s.defaultStabilization,
                    0f..1f,
                    { "${(it * 100).roundToInt()}%" },
                    vm::setStabilization,
                )
                SwitchRow(stringResource(R.string.brush_cursor), stringResource(R.string.brush_cursor_desc), s.showBrushCursor, vm::setBrushCursor)
            }
            Group(stringResource(R.string.settings_canvas)) {
                SwitchRow(stringResource(R.string.checkerboard), stringResource(R.string.checkerboard_desc), s.checkerboardBackground, vm::setCheckerboard)
            }
            Group(stringResource(R.string.settings_autosave)) {
                SwitchRow(stringResource(R.string.autosave), stringResource(R.string.autosave_desc), s.autosaveEnabled, vm::setAutosave)
                if (s.autosaveEnabled) {
                    SliderRow(
                        stringResource(R.string.autosave_interval),
                        s.autosaveIntervalSec.toFloat(),
                        10f..300f,
                        { context.getString(R.string.seconds_value, it.roundToInt()) },
                        { vm.setAutosaveInterval(it.roundToInt()) },
                    )
                }
            }
            Group(stringResource(R.string.settings_export)) {
                SliderRow(
                    stringResource(R.string.export_quality),
                    s.exportQuality.toFloat(),
                    10f..100f,
                    { "${it.roundToInt()}%" },
                    { vm.setExportQuality(it.roundToInt()) },
                )
                SwitchRow(stringResource(R.string.export_include_audio), null, s.exportIncludeAudio, vm::setExportAudio)
                LabeledSegments(
                    stringResource(R.string.export_destination),
                    ExportDestination.entries,
                    s.exportDestination,
                    { stringResource(if (it == ExportDestination.GALLERY) R.string.destination_gallery else R.string.destination_ask) },
                    vm::setExportDestination,
                )
            }
            Group(stringResource(R.string.settings_backup)) {
                val stamp = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
                NavRow(
                    title = stringResource(R.string.backup_all),
                    subtitle = stringResource(R.string.backup_all_desc),
                    onClick = { backupLauncher.launch("GoDraw-backup-$stamp.godrawbackup") },
                )
                NavRow(
                    title = stringResource(R.string.restore_backup),
                    subtitle = stringResource(R.string.restore_backup_desc),
                    onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                )
            }
            if (vm.simulator.isAvailable) {
                Group(stringResource(R.string.settings_debug)) {
                    Text(
                        stringResource(R.string.debug_simulator_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                    val options = listOf<Tier?>(null) + Tier.entries
                    SingleChoiceSegmentedButtonRow(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    ) {
                        options.forEachIndexed { i, t ->
                            SegmentedButton(
                                selected = simulated == t,
                                onClick = { vm.setSimulatedTier(t) },
                                shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            ) {
                                Text(if (t == null) stringResource(R.string.debug_simulator_off) else stringResource(tierName(t)))
                            }
                        }
                    }
                }
            }
            Group(stringResource(R.string.settings_about)) {
                NavRow(title = stringResource(R.string.about_godraw), onClick = onOpenAbout)
                NavRow(title = stringResource(R.string.privacy), onClick = { privacyDialog = true })
            }
        }
    }

    if (languageDialog) {
        val current = vm.currentLanguage()
        AlertDialog(
            onDismissRequest = { languageDialog = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column {
                    AppLanguage.entries.forEach { lang ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    languageDialog = false
                                    vm.setLanguage(lang)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = lang == current, onClick = {
                                languageDialog = false
                                vm.setLanguage(lang)
                            })
                            Text(stringResource(languageName(lang)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { languageDialog = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    if (privacyDialog) {
        AlertDialog(
            onDismissRequest = { privacyDialog = false },
            title = { Text(stringResource(R.string.privacy)) },
            text = { Text(stringResource(R.string.privacy_text)) },
            confirmButton = { TextButton(onClick = { privacyDialog = false }) { Text(stringResource(R.string.close)) } },
        )
    }
    locked?.let { a ->
        UpgradeDialog(a, stringResource(R.string.accent_color), onDismiss = { locked = null }, onUpgrade = { locked = null; onOpenSubscription() })
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) trailing() else Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        Row {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(format(local), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> LabeledSegments(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(6.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = o == selected,
                    onClick = { onSelect(o) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label(o), maxLines = 1) }
            }
        }
    }
}

private val accentChoices = listOf(
    GoDrawColors.Blue, GoDrawColors.Purple, GoDrawColors.Magenta, GoDrawColors.Coral,
    GoDrawColors.Orange, GoDrawColors.Gold, Color(0xFF26C281), GoDrawColors.Cyan,
)

@Composable
private fun AccentColorRow(current: Int?, tier: Tier, onSelect: (Int?) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (tier != Tier.MAX) LockMarker(Tier.MAX)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(2.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable { onSelect(null) },
                contentAlignment = Alignment.Center,
            ) {
                if (current == null) Icon(Icons.Filled.Check, null, Modifier.size(16.dp))
            }
            accentChoices.forEach { c ->
                val argb = c.toArgb()
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(c)
                        .clickable { onSelect(argb) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (current == argb) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(top = 8.dp), color = Color.Transparent)
}
