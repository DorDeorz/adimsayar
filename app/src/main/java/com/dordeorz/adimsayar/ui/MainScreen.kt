package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DAILY_GOAL
import com.dordeorz.adimsayar.data.DaySteps
import com.dordeorz.adimsayar.ui.oem.BatteryHelpCard
import com.dordeorz.adimsayar.ui.oem.OemProfile
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", TURKISH)
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", TURKISH)

enum class PermissionState { Granted, NeedsRequest, Denied }

enum class XiaomiProblem { Unreadable, Stalled }

data class MainUiState(
    val week: List<DaySteps>,
    val total: Long,
    val lastReadingWallMs: Long?,
    val permission: PermissionState,
    val sensorAvailable: Boolean,
    val frozen: Boolean,
    val batteryProfile: OemProfile?,
    val serviceEnabled: Boolean,
    val xiaomiAvailable: Boolean,
    val xiaomiEnabled: Boolean,
    val xiaomiProblem: XiaomiProblem?,
    val logLines: List<String>,
)

private fun format(steps: Long): String = NumberFormat.getIntegerInstance(TURKISH).format(steps)

@Composable
fun MainScreen(
    state: MainUiState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onDismissBatteryCard: () -> Unit,
    onServiceEnabledChange: (Boolean) -> Unit,
    onXiaomiEnabledChange: (Boolean) -> Unit,
) {
    val today = state.week.lastOrNull()?.steps ?: 0L
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { TodayHeader(today) }
            if (!state.sensorAvailable) {
                item { MessageCard(stringResource(R.string.sensor_missing_title), stringResource(R.string.sensor_missing_text)) }
            } else if (state.permission != PermissionState.Granted) {
                item { PermissionCard(state.permission, onRequestPermission, onOpenAppSettings) }
            }
            if (state.frozen) {
                item { MessageCard(stringResource(R.string.frozen_title), stringResource(R.string.frozen_text)) }
            }
            when (state.xiaomiProblem) {
                XiaomiProblem.Unreadable -> item {
                    MessageCard(stringResource(R.string.xiaomi_unreadable_title), stringResource(R.string.xiaomi_unreadable_text))
                }
                XiaomiProblem.Stalled -> item {
                    MessageCard(stringResource(R.string.xiaomi_stalled_title), stringResource(R.string.xiaomi_stalled_text))
                }
                null -> Unit
            }
            if (state.xiaomiAvailable || state.xiaomiEnabled) {
                item {
                    SwitchCard(
                        stringResource(R.string.xiaomi_card_title),
                        stringResource(R.string.xiaomi_card_text),
                        state.xiaomiEnabled,
                        onXiaomiEnabledChange,
                    )
                }
            }
            if (state.sensorAvailable && state.permission == PermissionState.Granted && !state.xiaomiEnabled) {
                item {
                    SwitchCard(
                        stringResource(R.string.service_card_title),
                        stringResource(R.string.service_card_text),
                        state.serviceEnabled,
                        onServiceEnabledChange,
                    )
                }
            }
            state.batteryProfile?.let { profile ->
                item { BatteryHelpCard(profile = profile, onDismiss = onDismissBatteryCard) }
            }
            item { WeekCard(state.week) }
            item { StatsRow(state.total, state.week) }
            item {
                val reading = state.lastReadingWallMs
                Text(
                    text = if (reading == null) {
                        stringResource(R.string.no_reading_yet)
                    } else {
                        stringResource(R.string.last_reading, TIME_FORMAT.format(Instant.ofEpochMilli(reading).atZone(ZoneId.systemDefault())))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
            item { LogCard(state.logLines) }
        }
    }
}

@Composable
private fun SwitchCard(title: String, text: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun LogCard(lines: List<String>) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.log_title), style = MaterialTheme.typography.titleMedium)
            if (lines.isEmpty()) {
                Text(stringResource(R.string.log_empty), style = MaterialTheme.typography.bodySmall)
            }
            lines.take(30).forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TodayHeader(steps: Long) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.today), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(format(steps), style = MaterialTheme.typography.displayLarge)
        Text(stringResource(R.string.steps_unit), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(
            progress = { (steps.toFloat() / DAILY_GOAL).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(8.dp).clip(RoundedCornerShape(4.dp)),
        )
        Text(
            text = stringResource(R.string.goal_short, format(DAILY_GOAL)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun PermissionCard(permission: PermissionState, onRequest: () -> Unit, onOpenSettings: () -> Unit) {
    val denied = permission == PermissionState.Denied
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (denied) R.string.permission_denied_text else R.string.permission_text),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = if (denied) onOpenSettings else onRequest, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(if (denied) R.string.permission_open_settings else R.string.permission_grant))
            }
        }
    }
}

@Composable
private fun MessageCard(title: String, text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun WeekCard(week: List<DaySteps>) {
    val maxSteps = week.maxOfOrNull { it.steps }?.coerceAtLeast(1L) ?: 1L
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.last_7_days), style = MaterialTheme.typography.titleMedium)
            week.asReversed().forEachIndexed { index, day ->
                DayRow(day, fraction = day.steps.toFloat() / maxSteps, isToday = index == 0)
            }
        }
    }
}

@Composable
private fun DayRow(day: DaySteps, fraction: Float, isToday: Boolean) {
    val weight = if (isToday) FontWeight.Bold else FontWeight.Normal
    val barColor = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, TURKISH) + " " + DAY_FORMAT.format(day.date),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            modifier = Modifier.width(96.dp),
        )
        Box(modifier = Modifier.weight(1f).height(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (day.steps > 0L) barColor else Color.Transparent),
            )
        }
        Text(
            text = format(day.steps),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = weight,
            textAlign = TextAlign.End,
            modifier = Modifier.width(72.dp),
        )
    }
}

@Composable
private fun StatsRow(total: Long, week: List<DaySteps>) {
    val average = if (week.isEmpty()) 0L else week.sumOf { it.steps } / week.size
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatCard(stringResource(R.string.total), format(total), Modifier.weight(1f))
        StatCard(stringResource(R.string.week_average), format(average), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}
