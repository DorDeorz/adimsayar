package com.dordeorz.adimsayar.ui

import android.os.Build
import androidx.annotation.ArrayRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DAILY_GOAL_RANGE
import com.dordeorz.adimsayar.data.ThemeMode
import com.dordeorz.adimsayar.data.WEEKLY_GOAL_RANGE

private enum class SettingsDialog { DailyGoal, WeeklyGoal, Log, ReleaseNotes }

private class ReleaseNote(val version: String, @param:ArrayRes val lines: Int)

private val RELEASE_NOTES = listOf(
    ReleaseNote("0.2.0", R.array.release_notes_0_2_0),
    ReleaseNote("0.1.0", R.array.release_notes_0_1_0),
)

private val DAILY_PRESETS = listOf(6_000L, 8_000L, 10_000L, 12_000L, 15_000L)
private val WEEKLY_PRESETS = listOf(42_000L, 56_000L, 70_000L, 84_000L, 105_000L)

@Composable
fun SettingsScreen(state: MainUiState, actions: MainActions, onBack: () -> Unit) {
    val settings = state.settings
    var dialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge)
            }

            SectionTitle(stringResource(R.string.settings_goals))
            ValueRow(stringResource(R.string.daily_goal), stringResource(R.string.steps_value, format(settings.dailyGoal))) {
                dialog = SettingsDialog.DailyGoal
            }
            ValueRow(stringResource(R.string.weekly_goal), stringResource(R.string.steps_value, format(settings.weeklyGoal))) {
                dialog = SettingsDialog.WeeklyGoal
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_theme))
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

            val showXiaomi = state.xiaomiAvailable || state.xiaomiEnabled
            val showService = state.sensorAvailable && state.permission == PermissionState.Granted && !state.xiaomiEnabled
            if (showXiaomi || showService) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                SectionTitle(stringResource(R.string.settings_counting))
                if (showXiaomi) {
                    SwitchRow(
                        title = stringResource(R.string.xiaomi_card_title),
                        hint = stringResource(R.string.xiaomi_card_text),
                        checked = state.xiaomiEnabled,
                        onCheckedChange = actions.onXiaomiEnabledChange,
                    )
                }
                if (showService) {
                    SwitchRow(
                        title = stringResource(R.string.service_card_title),
                        hint = stringResource(R.string.service_card_text),
                        checked = state.serviceEnabled,
                        onCheckedChange = actions.onServiceEnabledChange,
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle(stringResource(R.string.settings_about))
            ValueRow(stringResource(R.string.settings_version), state.version, onClick = null)
            ClickRow(stringResource(R.string.release_notes), stringResource(R.string.release_notes_hint)) {
                dialog = SettingsDialog.ReleaseNotes
            }
            ClickRow(stringResource(R.string.log_title), stringResource(R.string.log_hint)) {
                dialog = SettingsDialog.Log
            }
        }
    }

    when (dialog) {
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
        null -> Unit
    }
}

@Composable
private fun GoalDialog(
    title: String,
    current: Long,
    range: LongRange,
    presets: List<Long>,
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
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit).take(7) },
                    singleLine = true,
                    isError = !valid,
                    suffix = { Text(stringResource(R.string.steps_unit)) },
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.System -> R.string.theme_system
    ThemeMode.Light -> R.string.theme_light
    ThemeMode.Dark -> R.string.theme_dark
}
