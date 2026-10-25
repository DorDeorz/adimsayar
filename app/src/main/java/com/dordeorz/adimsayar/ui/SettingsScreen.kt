package com.dordeorz.adimsayar.ui

import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import com.dordeorz.adimsayar.background.BandNotificationListener
import com.dordeorz.adimsayar.data.BandLive
import com.dordeorz.adimsayar.data.BandNotify
import com.dordeorz.adimsayar.data.BandBattery
import com.dordeorz.adimsayar.data.BandReplies
import com.dordeorz.adimsayar.data.BandWeather
import com.dordeorz.adimsayar.data.BandSchedule
import com.dordeorz.adimsayar.band.BandAlarm
import java.time.DayOfWeek
import com.dordeorz.adimsayar.data.WeatherPlace
import android.Manifest
import androidx.compose.runtime.mutableStateListOf
import android.app.TimePickerDialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import java.time.LocalTime
import kotlinx.coroutines.delay
import androidx.annotation.ArrayRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.dordeorz.adimsayar.band.BandModels
import com.dordeorz.adimsayar.band.BandResult
import com.dordeorz.adimsayar.band.BandShape
import com.dordeorz.adimsayar.data.DeviceKind
import com.dordeorz.adimsayar.data.SourceDevice
import com.dordeorz.adimsayar.data.WearableHealth
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.dordeorz.adimsayar.data.BandDirect
import com.dordeorz.adimsayar.data.BandStatus
import java.time.Instant
import java.time.ZoneId
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.WIDGET_OPACITY_OPTIONS
import com.dordeorz.adimsayar.data.AGE_RANGE
import com.dordeorz.adimsayar.data.DAILY_GOAL_RANGE
import com.dordeorz.adimsayar.data.GLASS_OPTIONS
import com.dordeorz.adimsayar.data.WATER_GOAL_RANGE
import com.dordeorz.adimsayar.data.WATER_INTERVAL_OPTIONS
import com.dordeorz.adimsayar.data.Water
import com.dordeorz.adimsayar.data.DistanceUnit
import com.dordeorz.adimsayar.data.SUPPORTED_LANGUAGES
import com.dordeorz.adimsayar.data.SYSTEM_LANGUAGE
import com.dordeorz.adimsayar.data.HEIGHT_RANGE
import com.dordeorz.adimsayar.data.ThemeMode
import com.dordeorz.adimsayar.data.InstallStart
import com.dordeorz.adimsayar.data.UpdateChecker
import com.dordeorz.adimsayar.data.UpdateInstaller
import com.dordeorz.adimsayar.data.UpdateResult
import com.dordeorz.adimsayar.data.WEEKLY_GOAL_RANGE
import com.dordeorz.adimsayar.data.WEEK_START_OPTIONS
import com.dordeorz.adimsayar.data.WEIGHT_RANGE
import java.io.File
import java.time.format.TextStyle
import kotlinx.coroutines.launch

private enum class SettingsDialog { DailyGoal, WeeklyGoal, Height, Weight, Age, WaterGoal, Log, ReleaseNotes, Language }

private class ReleaseNote(val version: String, @param:ArrayRes val lines: Int)

private val RELEASE_NOTES = listOf(
    ReleaseNote("0.12.0", R.array.release_notes_0_12_0),
    ReleaseNote("0.11.0", R.array.release_notes_0_11_0),
    ReleaseNote("0.10.0", R.array.release_notes_0_10_0),
    ReleaseNote("0.9.6", R.array.release_notes_0_9_6),
    ReleaseNote("0.9.5", R.array.release_notes_0_9_5),
    ReleaseNote("0.9.4", R.array.release_notes_0_9_4),
    ReleaseNote("0.9.3", R.array.release_notes_0_9_3),
    ReleaseNote("0.9.2", R.array.release_notes_0_9_2),
    ReleaseNote("0.9.1", R.array.release_notes_0_9_1),
    ReleaseNote("0.9.0", R.array.release_notes_0_9_0),
    ReleaseNote("0.8.0", R.array.release_notes_0_8_0),
    ReleaseNote("0.7.0", R.array.release_notes_0_7_0),
    ReleaseNote("0.6.1", R.array.release_notes_0_6_1),
    ReleaseNote("0.6.0", R.array.release_notes_0_6_0),
    ReleaseNote("0.5.0", R.array.release_notes_0_5_0),
    ReleaseNote("0.4.0", R.array.release_notes_0_4_0),
    ReleaseNote("0.3.0", R.array.release_notes_0_3_0),
    ReleaseNote("0.2.0", R.array.release_notes_0_2_0),
    ReleaseNote("0.1.0", R.array.release_notes_0_1_0),
)

private val DAILY_PRESETS = listOf(6_000L, 8_000L, 10_000L, 12_000L, 15_000L)
private val WEEKLY_PRESETS = listOf(42_000L, 56_000L, 70_000L, 84_000L, 105_000L)
private val WATER_PRESETS = listOf(1_500, 2_000, 2_500, 3_000)

@Composable
fun SettingsScreen(state: MainUiState, actions: MainActions) {
    val settings = state.settings
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    var bandSetup by rememberSaveable { mutableStateOf(false) }
    if (bandSetup) {
        BandSetupScreen(actions, onClose = { bandSetup = false })
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bandStatus = remember(state.bandSyncing, state.bandDirectEnabled) { BandDirect.status(context) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            if (state.bandDirectEnabled || state.wearableEnabled) {
                DeviceCard(state, bandStatus, actions, onManage = { bandSetup = true })
            }

            val showXiaomi = state.xiaomiAvailable || state.xiaomiEnabled
            val showService = state.sensorAvailable && state.permission == PermissionState.Granted && !state.xiaomiEnabled
            val showWearable = state.wearableAvailable || state.wearableEnabled
            SettingsGroup(stringResource(R.string.settings_counting)) {
                GroupLabel(stringResource(R.string.settings_phone_source))
                RadioRow(stringResource(R.string.source_sensor), stringResource(R.string.source_sensor_hint), selected = !state.xiaomiEnabled) {
                    if (state.xiaomiEnabled) actions.onXiaomiEnabledChange(false)
                }
                if (showXiaomi) {
                    RadioRow(stringResource(R.string.xiaomi_card_title), stringResource(R.string.xiaomi_card_text), selected = state.xiaomiEnabled) {
                        if (!state.xiaomiEnabled) actions.onXiaomiEnabledChange(true)
                    }
                }
                if (showService) {
                    SwitchRow(
                        title = stringResource(R.string.service_card_title),
                        hint = stringResource(R.string.service_card_text),
                        checked = state.serviceEnabled,
                        onCheckedChange = actions.onServiceEnabledChange,
                    )
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                GroupLabel(stringResource(R.string.settings_wearable_source))
                RadioRow(
                    stringResource(R.string.source_none),
                    stringResource(R.string.source_none_hint),
                    selected = !state.bandDirectEnabled && !state.wearableEnabled,
                ) {
                    if (state.wearableEnabled) actions.onWearableEnabledChange(false)
                    if (state.bandDirectEnabled) scope.launch { actions.onBandDirectChange(false) }
                }
                RadioRow(stringResource(R.string.source_direct), stringResource(R.string.band_hint), selected = state.bandDirectEnabled) {
                    when {
                        state.bandDirectEnabled -> Unit
                        bandStatus.address != null && bandStatus.hasKey -> scope.launch { actions.onBandDirectChange(true) }
                        else -> bandSetup = true
                    }
                }
                if (showWearable) {
                    RadioRow(stringResource(R.string.source_connect), stringResource(R.string.wearable_card_text), selected = state.wearableEnabled) {
                        if (!state.wearableEnabled) actions.onWearableEnabledChange(true)
                    }
                }
                Text(
                    stringResource(R.string.source_merge_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (state.bandDirectEnabled) BandNotifyGroup()

            SettingsGroup(stringResource(R.string.settings_goals)) {
                ValueRow(stringResource(R.string.daily_goal), stringResource(R.string.steps_value, format(settings.dailyGoal))) {
                    dialog = SettingsDialog.DailyGoal
                }
                ValueRow(stringResource(R.string.weekly_goal), stringResource(R.string.steps_value, format(settings.weeklyGoal))) {
                    dialog = SettingsDialog.WeeklyGoal
                }
                ChoiceRow(
                    title = stringResource(R.string.week_start),
                    options = WEEK_START_OPTIONS,
                    selected = settings.weekStart,
                    label = { it.getDisplayName(TextStyle.FULL, appLocale) },
                    onSelect = actions.onWeekStartChange,
                )
            }

            SettingsGroup(stringResource(R.string.settings_body)) {
                ValueRow(stringResource(R.string.height), stringResource(R.string.height_value, settings.heightCm)) {
                    dialog = SettingsDialog.Height
                }
                ValueRow(stringResource(R.string.weight), stringResource(R.string.weight_value, settings.weightKg)) {
                    dialog = SettingsDialog.Weight
                }
                ValueRow(stringResource(R.string.age), stringResource(R.string.age_value, settings.age)) {
                    dialog = SettingsDialog.Age
                }
                ChoiceRow(
                    title = stringResource(R.string.distance_unit),
                    options = DistanceUnit.entries,
                    selected = settings.distanceUnit,
                    label = { stringResource(if (it == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km) },
                    onSelect = actions.onDistanceUnitChange,
                )
                Text(
                    stringResource(R.string.body_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            SettingsGroup(stringResource(R.string.water_title)) {
                ValueRow(stringResource(R.string.water_goal), stringResource(R.string.water_ml, format(settings.waterGoalMl.toLong()))) {
                    dialog = SettingsDialog.WaterGoal
                }
                ChoiceRow(
                    title = stringResource(R.string.water_glass),
                    options = GLASS_OPTIONS,
                    selected = settings.glassMl,
                    label = { stringResource(R.string.water_ml, it.toString()) },
                    onSelect = actions.onGlassChange,
                )
                SwitchRow(
                    title = stringResource(R.string.water_reminder),
                    hint = stringResource(R.string.water_reminder_hint),
                    checked = settings.waterReminder,
                    onCheckedChange = actions.onWaterReminderChange,
                )
                if (settings.waterReminder) {
                    ChoiceRow(
                        title = stringResource(R.string.water_interval),
                        options = WATER_INTERVAL_OPTIONS,
                        selected = settings.waterIntervalHours,
                        label = { stringResource(R.string.water_interval_value, it) },
                        onSelect = actions.onWaterIntervalChange,
                    )
                }
            }

            SettingsGroup(stringResource(R.string.settings_notifications)) {
                SwitchRow(
                    title = stringResource(R.string.goal_notification),
                    hint = stringResource(R.string.goal_notification_hint),
                    checked = settings.goalNotification,
                    onCheckedChange = actions.onGoalNotificationChange,
                )
                SwitchRow(
                    title = stringResource(R.string.streak_reminder),
                    hint = stringResource(R.string.streak_reminder_hint),
                    checked = settings.streakReminder,
                    onCheckedChange = actions.onStreakReminderChange,
                )
                SwitchRow(
                    title = stringResource(R.string.near_goal_notification),
                    hint = stringResource(R.string.near_goal_notification_hint),
                    checked = settings.nearGoalNotification,
                    onCheckedChange = actions.onNearGoalNotificationChange,
                )
                SwitchRow(
                    title = stringResource(R.string.weekly_summary),
                    hint = stringResource(R.string.weekly_summary_hint) +
                        if (state.healthEnabled) " " + stringResource(R.string.weekly_summary_band_hint) else "",
                    checked = settings.weeklySummary,
                    onCheckedChange = actions.onWeeklySummaryChange,
                )
                SwitchRow(
                    title = stringResource(R.string.weight_reminder),
                    hint = stringResource(R.string.weight_reminder_hint),
                    checked = settings.weightReminder,
                    onCheckedChange = actions.onWeightReminderChange,
                )
            }

            SettingsGroup(stringResource(R.string.settings_appearance)) {
                Column(Modifier.selectableGroup()) {
                    ThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = mode == settings.themeMode,
                                    onClick = { actions.onThemeModeChange(mode) },
                                    role = Role.RadioButton,
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            RadioButton(selected = mode == settings.themeMode, onClick = null)
                            Text(stringResource(themeLabel(mode)), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        hint = stringResource(R.string.settings_dynamic_color_hint),
                        checked = settings.dynamicColor,
                        onCheckedChange = actions.onDynamicColorChange,
                    )
                }
                ChoiceRow(
                    title = stringResource(R.string.settings_widget_opacity),
                    options = WIDGET_OPACITY_OPTIONS,
                    selected = settings.widgetOpacity,
                    label = { stringResource(R.string.percent_of_goal, it) },
                    onSelect = actions.onWidgetOpacityChange,
                )
                ValueRow(stringResource(R.string.language), languageLabel(settings.language)) {
                    dialog = SettingsDialog.Language
                }
            }

            SettingsGroup(stringResource(R.string.settings_data)) {
                ClickRow(stringResource(R.string.export_csv), stringResource(R.string.export_csv_hint), actions.onExport)
                ClickRow(stringResource(R.string.import_csv), stringResource(R.string.import_csv_hint), actions.onImport)
            }

            SettingsGroup(stringResource(R.string.settings_about)) {
                ValueRow(stringResource(R.string.settings_version), state.version, onClick = null)
                ClickRow(stringResource(R.string.release_notes), stringResource(R.string.release_notes_hint)) {
                    dialog = SettingsDialog.ReleaseNotes
                }
                ClickRow(stringResource(R.string.log_title), stringResource(R.string.log_hint)) {
                    dialog = SettingsDialog.Log
                }
                val uriHandler = LocalUriHandler.current
                var checking by remember { mutableStateOf(false) }
                var update by remember { mutableStateOf<UpdateResult?>(null) }
                var progress by remember { mutableStateOf<Int?>(null) }
                var apk by remember { mutableStateOf<File?>(null) }
                var downloadFailed by remember { mutableStateOf(false) }
                var needsPermission by remember { mutableStateOf(false) }
                var confirmDownload by remember { mutableStateOf(false) }
                val available = update as? UpdateResult.Available
                val download: (UpdateResult.Available, String) -> Unit = { release, url ->
                    scope.launch {
                        progress = 0
                        val downloaded = UpdateInstaller.download(context, url, release.version) { percent ->
                            scope.launch { progress = percent }
                        }
                        progress = null
                        apk = downloaded
                        downloadFailed = downloaded == null
                        if (downloaded != null) {
                            val start = UpdateInstaller.install(context, downloaded)
                            needsPermission = start == InstallStart.NeedsPermission
                        }
                    }
                }
                ClickRow(
                    stringResource(R.string.check_updates),
                    when {
                        checking -> stringResource(R.string.update_checking)
                        progress != null -> stringResource(R.string.update_downloading, progress ?: 0)
                        needsPermission -> stringResource(R.string.update_install_permission)
                        apk != null -> stringResource(R.string.update_ready)
                        downloadFailed -> stringResource(R.string.update_download_failed)
                        available != null -> stringResource(R.string.update_available, available.version)
                        update == UpdateResult.UpToDate -> stringResource(R.string.update_up_to_date)
                        update == UpdateResult.NoRelease -> stringResource(R.string.update_no_release)
                        update == UpdateResult.Failed -> stringResource(R.string.update_failed)
                        else -> stringResource(R.string.check_updates_hint)
                    },
                ) {
                    val file = apk
                    when {
                        checking || progress != null -> Unit
                        file != null -> {
                            val start = UpdateInstaller.install(context, file)
                            needsPermission = start == InstallStart.NeedsPermission
                            if (start == InstallStart.Failed) {
                                apk = null
                                downloadFailed = true
                            }
                        }
                        available != null && (downloadFailed || available.apkUrl == null) -> runCatching { uriHandler.openUri(available.url) }
                        available?.apkUrl != null -> confirmDownload = true
                        else -> scope.launch {
                            checking = true
                            update = UpdateChecker.check(state.version)
                            checking = false
                        }
                    }
                }
                val url = available?.apkUrl
                if (confirmDownload && available != null && url != null) {
                    AlertDialog(
                        onDismissRequest = { confirmDownload = false },
                        title = { Text(stringResource(R.string.update_consent_title)) },
                        text = { Text(stringResource(R.string.update_consent_text)) },
                        confirmButton = {
                            TextButton(onClick = {
                                confirmDownload = false
                                download(available, url)
                            }) { Text(stringResource(R.string.update_consent_download)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { confirmDownload = false }) { Text(stringResource(R.string.cancel)) }
                        },
                    )
                }
                ClickRow(stringResource(R.string.github_page), stringResource(R.string.github_page_hint)) {
                    runCatching { uriHandler.openUri(UpdateChecker.GITHUB_URL) }
                }
            }
        }
    }

    when (dialog) {
        SettingsDialog.Height -> GoalDialog(
            title = stringResource(R.string.height),
            current = settings.heightCm.toLong(),
            range = HEIGHT_RANGE.first.toLong()..HEIGHT_RANGE.last.toLong(),
            presets = emptyList(),
            unit = stringResource(R.string.unit_cm),
            onSave = { actions.onHeightChange(it.toInt()) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Weight -> GoalDialog(
            title = stringResource(R.string.weight),
            current = settings.weightKg.toLong(),
            range = WEIGHT_RANGE.first.toLong()..WEIGHT_RANGE.last.toLong(),
            presets = emptyList(),
            unit = stringResource(R.string.unit_kg),
            onSave = { actions.onWeightChange(it.toInt()) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.Age -> GoalDialog(
            title = stringResource(R.string.age),
            current = settings.age.toLong(),
            range = AGE_RANGE.first.toLong()..AGE_RANGE.last.toLong(),
            presets = emptyList(),
            unit = stringResource(R.string.age_unit),
            onSave = { actions.onAgeChange(it.toInt()) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.WaterGoal -> GoalDialog(
            title = stringResource(R.string.water_goal),
            current = settings.waterGoalMl.toLong(),
            range = WATER_GOAL_RANGE.first.toLong()..WATER_GOAL_RANGE.last.toLong(),
            presets = (listOf(Water.suggestedGoal(settings.weightKg)) + WATER_PRESETS).distinct().sorted().map { it.toLong() },
            unit = stringResource(R.string.unit_ml),
            hint = stringResource(R.string.water_goal_hint, format(Water.suggestedGoal(settings.weightKg).toLong()), settings.weightKg),
            onSave = { actions.onWaterGoalChange(it.toInt()) },
            onDismiss = { dialog = null },
        )
        SettingsDialog.DailyGoal -> GoalDialog(
            title = stringResource(R.string.daily_goal),
            current = settings.dailyGoal,
            range = DAILY_GOAL_RANGE,
            presets = DAILY_PRESETS,
            onSave = actions.onDailyGoalChange,
            onDismiss = { dialog = null },
        )
        SettingsDialog.WeeklyGoal -> GoalDialog(
            title = stringResource(R.string.weekly_goal),
            current = settings.weeklyGoal,
            range = WEEKLY_GOAL_RANGE,
            presets = (listOf(settings.dailyGoal * 7) + WEEKLY_PRESETS).distinct().sorted(),
            onSave = actions.onWeeklyGoalChange,
            onDismiss = { dialog = null },
        )
        SettingsDialog.Log -> TextListDialog(stringResource(R.string.log_title), onDismiss = { dialog = null }) {
            if (state.logLines.isEmpty()) {
                Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodySmall)
            }
            state.logLines.take(50).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingsDialog.ReleaseNotes -> TextListDialog(stringResource(R.string.release_notes), onDismiss = { dialog = null }) {
            RELEASE_NOTES.forEach { note ->
                Text(note.version, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                stringArrayResource(note.lines).forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        SettingsDialog.Language -> LanguageDialog(
            selected = settings.language,
            onSelect = actions.onLanguageChange,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun GoalDialog(
    title: String,
    current: Long,
    range: LongRange,
    presets: List<Long>,
    unit: String = stringResource(R.string.steps_unit),
    hint: String? = null,
    onSave: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(current.toString()) }
    val value = text.toLongOrNull()
    val valid = value != null && value in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit).take(range.last.toString().length) },
                    singleLine = true,
                    isError = !valid,
                    suffix = { Text(unit) },
                    supportingText = { Text(stringResource(R.string.goal_range, format(range.first), format(range.last))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                presets.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            SuggestionChip(onClick = { text = preset.toString() }, label = { Text(format(preset)) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value != null) onSave(value)
                    onDismiss()
                },
                enabled = valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextListDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) { content() }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun ValueRow(title: String, value: String, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClickRow(title: String, hint: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun languageLabel(language: String): String =
    if (language == SYSTEM_LANGUAGE) stringResource(R.string.language_system) else Locales.displayName(language)

@Composable
private fun LanguageDialog(selected: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    TextListDialog(stringResource(R.string.language), onDismiss = onDismiss) {
        Column(Modifier.selectableGroup()) {
            (listOf(SYSTEM_LANGUAGE) + SUPPORTED_LANGUAGES).forEach { language ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = language == selected,
                            onClick = {
                                onDismiss()
                                onSelect(language)
                            },
                            role = Role.RadioButton,
                        )
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    RadioButton(selected = language == selected, onClick = null)
                    Text(languageLabel(language), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun BandNotifyGroup() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(NOTIFY_POLL_MS)
            tick++
        }
    }
    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        BandLive.refresh(context)
        tick++
    }
    val access = remember(tick) { BandNotify.hasAccess(context) }
    val supported = remember(tick) { BandNotify.supported(context) }
    val apps = remember(tick) { BandNotify.apps(context) }
    val calls = remember(tick) { BandNotify.calls(context) }
    val music = remember(tick) { BandNotify.music(context) }
    val reminders = remember(tick) { BandNotify.reminders(context) }
    val lost = remember(tick) { BandNotify.lostAlert(context) }
    val quiet = remember(tick) { BandNotify.quiet(context) }
    val quietFrom = remember(tick) { BandNotify.quietFrom(context) }
    val quietTo = remember(tick) { BandNotify.quietTo(context) }
    val batteryAlert = remember(tick) { BandBattery.alertOn(context) }
    val blockedCount = remember(tick) { BandNotify.blocked(context).size }
    val connected = remember(tick) { BandLive.connected }
    var pickApps by remember { mutableStateOf(false) }
    var editReplies by remember { mutableStateOf(false) }
    var pickPlace by remember { mutableStateOf(false) }
    var showAlarms by remember { mutableStateOf(false) }
    val calendar = remember(tick) { BandSchedule.calendarOn(context) }
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        BandSchedule.setCalendar(context, ok)
        tick++
    }
    val weather = remember(tick) { BandWeather.isOn(context) }
    val weatherPlace = remember(tick) { BandWeather.place(context) }
    fun pickTime(current: Int, onPicked: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(hour * 60 + minute) },
            current / 60,
            current % 60,
            android.text.format.DateFormat.is24HourFormat(context),
        ).show()
    }
    fun openAccess() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, BandNotificationListener::class.java).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
    }
    fun change(on: Boolean, set: (Boolean) -> Unit) {
        set(on)
        tick++
        if (on && !access) openAccess()
    }
    SettingsGroup(stringResource(R.string.band_notify_title)) {
        Text(
            stringResource(if (supported) R.string.band_notify_text else R.string.band_notify_huami),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (supported) {
        SwitchRow(stringResource(R.string.band_notify_apps), stringResource(R.string.band_notify_apps_hint), apps) {
            change(it) { on -> BandNotify.setApps(context, on) }
        }
        if (apps) {
            ClickRow(
                stringResource(R.string.band_notify_pick_apps),
                if (blockedCount == 0) stringResource(R.string.band_notify_pick_apps_all) else stringResource(R.string.band_notify_pick_apps_off, blockedCount),
            ) { pickApps = true }
        }
        SwitchRow(stringResource(R.string.band_notify_calls), stringResource(R.string.band_notify_calls_hint), calls) {
            change(it) { on -> BandNotify.setCalls(context, on) }
            if (it && !BandNotify.canRejectCalls(context) && BandNotify.CALL_PERMISSIONS.isNotEmpty()) callPermission.launch(BandNotify.CALL_PERMISSIONS)
        }
        if (calls) {
            ClickRow(stringResource(R.string.band_replies), stringResource(R.string.band_replies_hint)) { editReplies = true }
        }
        SwitchRow(stringResource(R.string.band_notify_music), stringResource(R.string.band_notify_music_hint), music) {
            change(it) { on -> BandNotify.setMusic(context, on) }
        }
        SwitchRow(stringResource(R.string.band_notify_reminders), stringResource(R.string.band_notify_reminders_hint), reminders) {
            change(it) { on -> BandNotify.setReminders(context, on) }
        }
        SwitchRow(stringResource(R.string.band_notify_quiet), stringResource(R.string.band_notify_quiet_hint), quiet) {
            BandNotify.setQuiet(context, it)
            tick++
        }
        if (quiet) {
            ValueRow(stringResource(R.string.band_notify_quiet_from), minuteLabel(quietFrom)) {
                pickTime(quietFrom) { BandNotify.setQuietHours(context, it, quietTo); tick++ }
            }
            ValueRow(stringResource(R.string.band_notify_quiet_to), minuteLabel(quietTo)) {
                pickTime(quietTo) { BandNotify.setQuietHours(context, quietFrom, it); tick++ }
            }
        }
        SwitchRow(stringResource(R.string.band_notify_lost), stringResource(R.string.band_notify_lost_hint), lost) {
            change(it) { on -> BandNotify.setLostAlert(context, on) }
        }
        SwitchRow(stringResource(R.string.band_weather), stringResource(R.string.band_weather_hint), weather) {
            change(it) { on -> BandWeather.setOn(context, on) }
            if (it && weatherPlace == null) pickPlace = true
        }
        if (weather) {
            ClickRow(stringResource(R.string.band_weather_place), weatherPlace?.name ?: stringResource(R.string.band_weather_place_none)) { pickPlace = true }
        }
        ClickRow(stringResource(R.string.band_alarms), stringResource(R.string.band_alarms_hint)) { showAlarms = true }
        SwitchRow(stringResource(R.string.band_calendar), stringResource(R.string.band_calendar_hint), calendar) { on ->
            if (on && !BandSchedule.hasCalendarPermission(context)) {
                calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            } else {
                change(on) { BandSchedule.setCalendar(context, it) }
            }
        }
        SwitchRow(stringResource(R.string.band_battery_alert), stringResource(R.string.band_battery_alert_hint, BandBattery.LOW_LEVEL), batteryAlert) {
            BandBattery.setAlert(context, it)
            tick++
        }
        if (BandNotify.anyOn(context)) {
            if (!access) {
                ClickRow(stringResource(R.string.band_notify_access), stringResource(R.string.band_notify_access_hint), ::openAccess)
            } else {
                Text(
                    stringResource(if (connected) R.string.band_notify_state_connected else R.string.band_notify_state_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            Text(
                stringResource(R.string.band_notify_autostart),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        }
    }
    if (editReplies) RepliesDialog(onDismiss = { editReplies = false })
    if (showAlarms) AlarmsDialog(onDismiss = { showAlarms = false })
    if (pickPlace) {
        WeatherPlaceDialog(onDismiss = {
            pickPlace = false
            tick++
        })
    }
    if (pickApps) {
        AppPickerDialog(onDismiss = {
            pickApps = false
            tick++
        })
    }
}

private const val NOTIFY_POLL_MS = 1_000L

private fun minuteLabel(minute: Int): String = CLOCK_FORMAT.format(LocalTime.of(minute / 60, minute % 60))

/** Picks the weather place: a city search, or the phone's position while the app is open. */
@Composable
private fun WeatherPlaceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<WeatherPlace>?>(null) }
    var busy by remember { mutableStateOf(false) }
    val myPlace = stringResource(R.string.band_weather_my_place)
    fun useHere() {
        scope.launch {
            busy = true
            val place = BandWeather.currentPlace(context, myPlace)
            busy = false
            if (place != null) {
                BandWeather.setPlace(context, place)
                onDismiss()
            } else {
                results = emptyList()
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) useHere() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.band_weather_place)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(60) },
                    singleLine = true,
                    label = { Text(stringResource(R.string.band_weather_search)) },
                    trailingIcon = {
                        TextButton(
                            enabled = query.isNotBlank() && !busy,
                            onClick = {
                                scope.launch {
                                    busy = true
                                    results = BandWeather.search(query)
                                    busy = false
                                }
                            },
                        ) { Text(stringResource(R.string.band_weather_find)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(enabled = !busy, onClick = {
                    if (BandWeather.hasLocationPermission(context)) useHere() else locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }) { Text(stringResource(R.string.band_weather_use_location)) }
                if (busy) Text(stringResource(R.string.band_notify_pick_loading), style = MaterialTheme.typography.bodySmall)
                val list = results
                if (list != null && list.isEmpty() && !busy) {
                    Text(stringResource(R.string.band_weather_not_found), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    items(list.orEmpty()) { place ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    BandWeather.setPlace(context, place)
                                    onDismiss()
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(place.name, style = MaterialTheme.typography.bodyLarge)
                            if (place.detail.isNotEmpty()) {
                                Text(place.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** The band's alarms: switch on/off, tap to edit, add new ones. Needs the open connection. */
@Composable
private fun AlarmsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val alarms by remember { BandSchedule.alarms() }.collectAsState()
    var editing by remember { mutableStateOf<BandAlarm?>(null) }
    val connected by produceState(BandLive.connected) {
        BandLive.requestAlarms()
        while (true) {
            value = BandLive.connected
            delay(NOTIFY_POLL_MS)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.band_alarms)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val current = alarms
                when {
                    !connected -> Text(stringResource(R.string.band_alarms_offline), style = MaterialTheme.typography.bodyMedium)
                    current == null -> Text(stringResource(R.string.band_notify_pick_loading), style = MaterialTheme.typography.bodyMedium)
                    current.list.isEmpty() -> Text(stringResource(R.string.band_alarms_empty), style = MaterialTheme.typography.bodyMedium)
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(current?.list.orEmpty(), key = { it.id }) { alarm ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { editing = alarm }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(minuteLabel(alarm.hour * 60 + alarm.minute), style = MaterialTheme.typography.titleLarge)
                                Text(repeatLabel(alarm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = alarm.enabled, onCheckedChange = { BandSchedule.save(alarm.copy(enabled = it)) })
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            val current = alarms
            TextButton(
                enabled = connected && current != null && (current.max == 0 || current.list.size < current.max),
                onClick = { editing = BandAlarm(0, 7, 0, BandAlarm.Repeat.Once, 0, enabled = true) },
            ) { Text(stringResource(R.string.band_alarm_add)) }
        },
    )
    editing?.let { alarm ->
        AlarmEditDialog(alarm, onDismiss = { editing = null }, onSave = {
            BandSchedule.save(it)
            editing = null
        }, onDelete = {
            BandSchedule.delete(alarm)
            editing = null
        })
    }
}

@Composable
private fun repeatLabel(alarm: BandAlarm): String = when {
    alarm.repeat == BandAlarm.Repeat.Once -> stringResource(R.string.band_alarm_once)
    alarm.repeat == BandAlarm.Repeat.Daily -> stringResource(R.string.band_alarm_daily)
    alarm.days == BandAlarm.WEEKDAYS -> stringResource(R.string.band_alarm_weekdays)
    else -> DayOfWeek.entries.filter { alarm.days and (1 shl (it.value - 1)) != 0 }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locales.current) }
}

@Composable
private fun AlarmEditDialog(alarm: BandAlarm, onDismiss: () -> Unit, onSave: (BandAlarm) -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var minute by remember { mutableIntStateOf(alarm.hour * 60 + alarm.minute) }
    var repeat by remember { mutableStateOf(alarm.repeat) }
    var days by remember { mutableIntStateOf(if (alarm.repeat == BandAlarm.Repeat.Weekly) alarm.days else BandAlarm.WEEKDAYS) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (alarm.id == 0) R.string.band_alarm_add else R.string.band_alarm_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    TimePickerDialog(context, { _, h, m -> minute = h * 60 + m }, minute / 60, minute % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
                }) { Text(minuteLabel(minute), style = MaterialTheme.typography.displaySmall) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(repeat == BandAlarm.Repeat.Once, onClick = { repeat = BandAlarm.Repeat.Once }, label = { Text(stringResource(R.string.band_alarm_once)) })
                    FilterChip(repeat == BandAlarm.Repeat.Daily, onClick = { repeat = BandAlarm.Repeat.Daily }, label = { Text(stringResource(R.string.band_alarm_daily)) })
                    FilterChip(repeat == BandAlarm.Repeat.Weekly, onClick = { repeat = BandAlarm.Repeat.Weekly }, label = { Text(stringResource(R.string.band_alarm_days)) })
                }
                if (repeat == BandAlarm.Repeat.Weekly) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DayOfWeek.entries.forEach { day ->
                            val bit = 1 shl (day.value - 1)
                            FilterChip(
                                selected = days and bit != 0,
                                onClick = { days = days xor bit },
                                label = { Text(day.getDisplayName(TextStyle.NARROW, Locales.current)) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = repeat != BandAlarm.Repeat.Weekly || days != 0,
                onClick = { onSave(alarm.copy(hour = minute / 60, minute = minute % 60, repeat = repeat, days = if (repeat == BandAlarm.Repeat.Weekly) days else 0)) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (alarm.id != 0) TextButton(onClick = onDelete) { Text(stringResource(R.string.band_alarm_delete), color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private const val DEFAULT_REPLY_SLOTS = 5

/** Edits the texts the band offers when a call is rejected; they go out as SMS. */
@Composable
private fun RepliesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val slots = BandReplies.max(context).takeIf { it > 0 } ?: DEFAULT_REPLY_SLOTS
    val texts = remember {
        val saved = BandReplies.replies(context)
        mutableStateListOf<String>().apply { repeat(slots) { add(saved.getOrElse(it) { "" }) } }
    }
    var granted by remember { mutableStateOf(BandReplies.granted(context)) }
    var notSent by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = BandReplies.granted(context)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.band_replies)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.band_replies_text), style = MaterialTheme.typography.bodyMedium)
                if (!granted) {
                    TextButton(onClick = { permission.launch(BandReplies.PERMISSIONS) }) { Text(stringResource(R.string.band_replies_permission)) }
                }
                texts.forEachIndexed { index, text ->
                    OutlinedTextField(
                        value = text,
                        onValueChange = { texts[index] = it.take(MAX_REPLY_LENGTH) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (notSent) Text(stringResource(R.string.band_replies_not_sent), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (BandReplies.save(context, texts.toList())) onDismiss() else notSent = true
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private const val MAX_REPLY_LENGTH = 60

private class PhoneApp(val packageName: String, val label: String)

/** Lets the user turn off band notifications for single apps. */
@Composable
private fun AppPickerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(BandNotify.blocked(context)) }
    val apps by produceState<List<PhoneApp>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != context.packageName }
                .map { pkg -> PhoneApp(pkg, runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)) }
                .sortedBy { it.label.lowercase(Locales.current) }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.band_notify_pick_apps)) },
        text = {
            val list = apps
            if (list == null) {
                Text(stringResource(R.string.band_notify_pick_loading))
            } else {
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(list, key = { it.packageName }) { app ->
                        val on = app.packageName !in blocked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(value = on, role = Role.Checkbox, onValueChange = { checked ->
                                    blocked = if (checked) blocked - app.packageName else blocked + app.packageName
                                    BandNotify.setBlocked(context, blocked)
                                })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Checkbox(checked = on, onCheckedChange = null)
                            Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = {
                blocked = if (blocked.isEmpty()) apps.orEmpty().map { it.packageName }.toSet() else emptySet()
                BandNotify.setBlocked(context, blocked)
            }) { Text(stringResource(if (blocked.isEmpty()) R.string.band_notify_pick_none else R.string.band_notify_pick_all)) }
        },
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun RadioRow(title: String, hint: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeviceCard(state: MainUiState, status: BandStatus, actions: MainActions, onManage: () -> Unit) {
    val direct = state.bandDirectEnabled
    val context = LocalContext.current
    val connected by produceState<SourceDevice?>(null, direct, state.wearableEnabled) {
        value = if (direct || !state.wearableEnabled) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { WearableHealth.sources(context.applicationContext, DEVICE_LOOKUP_DAYS) }.getOrNull()
                    ?.apps?.flatMap { it.devices }
                    ?.firstOrNull { it.kind != DeviceKind.Phone && !it.name.isNullOrBlank() }
            }
        }
    }
    val battery by remember { BandBattery.observe(context) }.collectAsState()
    // (connected, finding) of the always-open connection; the find band button needs it.
    val live by produceState(BandLive.connected to BandLive.findingBand, direct) {
        while (direct) {
            value = BandLive.connected to BandLive.findingBand
            delay(NOTIFY_POLL_MS)
        }
    }
    val model = if (direct) BandModels.find(status.name) else BandModels.find(connected?.name)
    val shape = when {
        model != null -> model.shape
        direct -> BandModels.shapeOf(status.name)
        connected?.kind == DeviceKind.Watch -> BandShape.SquareWatch
        else -> BandShape.Band
    }
    val title = when {
        direct -> BandModels.displayName(status.name) ?: status.address ?: stringResource(R.string.source_direct)
        else -> model?.name ?: connected?.name ?: stringResource(R.string.source_connect)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier.size(DEVICE_IMAGE_SIZE).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(deviceImage(shape)), contentDescription = null, modifier = Modifier.size(48.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                val via = stringResource(if (direct) R.string.source_direct else R.string.source_connect)
                Text(
                    when {
                        direct && model != null && !model.tested -> stringResource(R.string.band_model_experimental, via)
                        direct || connected != null -> via
                        else -> stringResource(R.string.device_connect_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (direct) {
                    val line = when {
                        state.bandSyncing -> stringResource(R.string.band_testing)
                        status.lastSyncMs > 0L -> stringResource(
                            R.string.band_last_sync,
                            Instant.ofEpochMilli(status.lastSyncMs).atZone(ZoneId.systemDefault()).format(TIME_FORMAT),
                        )
                        else -> null
                    }
                    line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    status.lastResult?.takeIf { it != BandResult.Ok && !state.bandSyncing }?.let {
                        Text(stringResource(BandDirect.resultText(it)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    battery?.let { b ->
                        val low = b.level <= BandBattery.LOW_LEVEL && b.charging != true
                        Text(
                            stringResource(R.string.band_battery_line, b.level) +
                                if (b.charging == true) " · " + stringResource(R.string.band_battery_charging) else "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = if (direct) actions.onSyncBand else actions.onWearableResync, enabled = !state.bandSyncing) {
                Text(stringResource(if (direct) R.string.band_reconnect else R.string.band_sync_now))
            }
            if (direct) OutlinedButton(onClick = onManage) { Text(stringResource(R.string.source_direct_manage)) }
        }
        if (direct && live.first) {
            Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                OutlinedButton(onClick = { BandLive.findBand(!live.second) }) {
                    Text(stringResource(if (live.second) R.string.band_find_stop else R.string.band_find))
                }
            }
        }
    }
}

private val DEVICE_IMAGE_SIZE = 64.dp
private const val DEVICE_LOOKUP_DAYS = 7L

fun deviceImage(shape: BandShape): Int = when (shape) {
    BandShape.Band -> R.drawable.ic_band
    BandShape.SquareWatch -> R.drawable.ic_watch_square
    BandShape.RoundWatch -> R.drawable.ic_watch_round
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}
