package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DAILY_GOAL_RANGE
import com.dordeorz.adimsayar.data.HealthWrite
import com.dordeorz.adimsayar.data.MONTHLY_GOAL_RANGE
import com.dordeorz.adimsayar.data.NO_GOAL
import com.dordeorz.adimsayar.data.NO_VACATION
import com.dordeorz.adimsayar.data.YEARLY_GOAL_RANGE
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class ExtraDialog { Weekend, Monthly, Yearly, Vacation, EraseRange, EraseAll, Restore }

private val WEEKEND_PRESETS = listOf(5_000L, 8_000L, 12_000L)
private val MONTHLY_PRESETS = listOf(200_000L, 300_000L, 450_000L)
private val YEARLY_PRESETS = listOf(2_000_000L, 3_000_000L, 4_000_000L)

@Composable
internal fun GoalsExtras(state: MainUiState, actions: MainActions) {
    val settings = state.settings
    var dialog by rememberSaveable { mutableStateOf<ExtraDialog?>(null) }
    val off = stringResource(R.string.goal_off)
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    ValueRow(
        stringResource(R.string.weekend_goal),
        if (settings.weekendGoal == NO_GOAL) off else stringResource(R.string.steps_value, format(settings.weekendGoal)),
    ) { dialog = ExtraDialog.Weekend }
    ValueRow(
        stringResource(R.string.monthly_goal),
        if (settings.monthlyGoal == NO_GOAL) off else stringResource(R.string.steps_value, format(settings.monthlyGoal)),
    ) { dialog = ExtraDialog.Monthly }
    ValueRow(
        stringResource(R.string.yearly_goal),
        if (settings.yearlyGoal == NO_GOAL) off else stringResource(R.string.steps_value, format(settings.yearlyGoal)),
    ) { dialog = ExtraDialog.Yearly }
    val vacation = if (settings.vacationFrom == NO_VACATION) {
        stringResource(R.string.vacation_off)
    } else {
        stringResource(
            R.string.vacation_range,
            DAY_FORMAT.format(LocalDate.ofEpochDay(settings.vacationFrom)),
            DAY_FORMAT.format(LocalDate.ofEpochDay(settings.vacationTo)),
        )
    }
    ClickRow(stringResource(R.string.vacation_mode), vacation + "\n" + stringResource(R.string.vacation_hint)) { dialog = ExtraDialog.Vacation }

    when (dialog) {
        ExtraDialog.Weekend -> OptionalGoalDialog(
            title = stringResource(R.string.weekend_goal),
            hint = stringResource(R.string.weekend_goal_hint),
            current = settings.weekendGoal.takeIf { it != NO_GOAL } ?: settings.dailyGoal,
            range = DAILY_GOAL_RANGE,
            presets = WEEKEND_PRESETS,
            onSave = actions.extra::setWeekendGoal,
            onDismiss = { dialog = null },
        )
        ExtraDialog.Monthly -> OptionalGoalDialog(
            title = stringResource(R.string.monthly_goal),
            hint = stringResource(R.string.period_goal_hint),
            current = settings.monthlyGoal.takeIf { it != NO_GOAL } ?: (settings.dailyGoal * 30),
            range = MONTHLY_GOAL_RANGE,
            presets = MONTHLY_PRESETS,
            onSave = actions.extra::setMonthlyGoal,
            onDismiss = { dialog = null },
        )
        ExtraDialog.Yearly -> OptionalGoalDialog(
            title = stringResource(R.string.yearly_goal),
            hint = stringResource(R.string.period_goal_hint),
            current = settings.yearlyGoal.takeIf { it != NO_GOAL } ?: (settings.dailyGoal * 365),
            range = YEARLY_GOAL_RANGE,
            presets = YEARLY_PRESETS,
            onSave = actions.extra::setYearlyGoal,
            onDismiss = { dialog = null },
        )
        ExtraDialog.Vacation -> DateRangeDialog(
            from = settings.vacationFrom.takeIf { it != NO_VACATION }?.let(LocalDate::ofEpochDay),
            to = settings.vacationTo.takeIf { it != NO_VACATION }?.let(LocalDate::ofEpochDay),
            clearLabel = stringResource(R.string.vacation_end),
            onSave = actions.extra::setVacation,
            onClear = { actions.extra.setVacation(null, null) },
            onDismiss = { dialog = null },
        )
        else -> Unit
    }
}

@Composable
internal fun DataExtras(state: MainUiState, actions: MainActions) {
    var dialog by rememberSaveable { mutableStateOf<ExtraDialog?>(null) }
    val context = LocalContext.current
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    GroupLabel(stringResource(R.string.backup_group))
    ClickRow(stringResource(R.string.backup_write), stringResource(R.string.backup_write_hint), actions.extra::backup)
    ClickRow(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_hint)) { dialog = ExtraDialog.Restore }
    if (state.wearableAvailable) {
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        SwitchRow(
            title = stringResource(R.string.health_write),
            hint = stringResource(if (HealthWrite.canRun(context)) R.string.health_write_hint else R.string.health_write_reading),
            checked = state.extra.extras.healthWrite,
            onCheckedChange = actions.extra::setHealthWrite,
        )
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    GroupLabel(stringResource(R.string.erase_group))
    ClickRow(stringResource(R.string.erase_range), stringResource(R.string.erase_range_hint)) { dialog = ExtraDialog.EraseRange }
    ClickRow(stringResource(R.string.erase_all), stringResource(R.string.erase_all_hint)) { dialog = ExtraDialog.EraseAll }

    when (dialog) {
        ExtraDialog.Restore -> ConfirmDialog(
            title = stringResource(R.string.backup_restore),
            text = stringResource(R.string.restore_confirm),
            confirm = stringResource(R.string.backup_restore_choose),
            onConfirm = actions.extra::restore,
            onDismiss = { dialog = null },
        )
        ExtraDialog.EraseRange -> DateRangeDialog(
            from = null,
            to = null,
            clearLabel = null,
            onSave = { from, to -> actions.extra.eraseRange(from, to) },
            onClear = {},
            onDismiss = { dialog = null },
        )
        ExtraDialog.EraseAll -> ConfirmDialog(
            title = stringResource(R.string.erase_all),
            text = stringResource(R.string.erase_all_confirm),
            confirm = stringResource(R.string.erase_confirm_button),
            onConfirm = actions.extra::eraseAll,
            onDismiss = { dialog = null },
        )
        else -> Unit
    }
}

@Composable
internal fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** A goal that can also be turned off. */
@Composable
internal fun OptionalGoalDialog(
    title: String,
    hint: String,
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
                Text(hint, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter(Char::isDigit).take(range.last.toString().length) },
                    singleLine = true,
                    isError = !valid,
                    suffix = { Text(stringResource(R.string.steps_unit)) },
                    supportingText = { Text(stringResource(R.string.goal_range, format(range.first), format(range.last))) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        SuggestionChip(onClick = { text = preset.toString() }, label = { Text(formatCompact(preset)) })
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
        dismissButton = {
            TextButton(onClick = {
                onSave(NO_GOAL)
                onDismiss()
            }) { Text(stringResource(R.string.goal_turn_off)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangeDialog(
    from: LocalDate?,
    to: LocalDate?,
    clearLabel: String?,
    onSave: (LocalDate, LocalDate) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberDateRangePickerState(
        initialSelectedStartDateMillis = from?.let(::utcMillis),
        initialSelectedEndDateMillis = to?.let(::utcMillis),
    )
    val start = picker.selectedStartDateMillis?.let(::utcDate)
    val end = picker.selectedEndDateMillis?.let(::utcDate) ?: start
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (start != null && end != null) onSave(start, end)
                    onDismiss()
                },
                enabled = start != null,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (clearLabel != null && from != null) {
                    TextButton(onClick = {
                        onClear()
                        onDismiss()
                    }) { Text(clearLabel) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    ) {
        DateRangePicker(state = picker, modifier = Modifier.weight(1f))
    }
}

private fun utcMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
