package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.DaySteps
import com.dordeorz.adimsayar.data.weekStartOf
import com.dordeorz.adimsayar.ui.oem.BatteryHelpCard
import java.time.Instant
import java.time.ZoneId

private val CHART_HEIGHT = 120.dp

@Composable
fun TodayScreen(state: MainUiState, actions: MainActions) {
    val steps = state.week.lastOrNull()?.steps ?: 0L
    val goal = state.settings.dailyGoal
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TodayRing(state, steps, goal) }
        if (!state.sensorAvailable) {
            item { MessageCard(stringResource(R.string.sensor_missing_title), stringResource(R.string.sensor_missing_text)) }
        } else if (state.permission != PermissionState.Granted) {
            item { PermissionCard(state.permission, actions.onRequestPermission, actions.onOpenAppSettings) }
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
        if (state.xiaomiProblem != null) {
            item {
                SwitchCard(
                    stringResource(R.string.xiaomi_card_title),
                    stringResource(R.string.xiaomi_card_text),
                    state.xiaomiEnabled,
                    actions.onXiaomiEnabledChange,
                )
            }
        }
        state.batteryProfile?.let { profile ->
            item { BatteryHelpCard(profile = profile, onDismiss = actions.onDismissBatteryCard) }
        }
        item { SummaryRow(state) }
        item { WeekCard(state.week, goal) }
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
    }
}

@Composable
private fun TodayRing(state: MainUiState, steps: Long, goal: Long) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            LONG_DAY_FORMAT.format(state.today),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.padding(top = 16.dp).size(240.dp), contentAlignment = Alignment.Center) {
            ProgressRing(fraction = steps.toFloat() / goal, stroke = 18.dp, modifier = Modifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(format(steps), style = MaterialTheme.typography.displayLarge)
                Text(
                    stringResource(R.string.steps_unit),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.goal_short, format(goal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Text(
            text = if (steps >= goal) {
                stringResource(R.string.goal_reached)
            } else {
                stringResource(R.string.goal_remaining, format(goal - steps))
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (steps >= goal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            distanceAndCalories(steps, state.settings),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun ProgressRing(fraction: Float, stroke: Dp, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val progress = MaterialTheme.colorScheme.primary
    val sweep = 360f * fraction.coerceIn(0f, 1f)
    Canvas(modifier = modifier) {
        val width = stroke.toPx()
        val inset = width / 2f
        val arcSize = Size(size.width - width, size.height - width)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(width))
        if (sweep > 0f) {
            drawArc(
                progress,
                -90f,
                sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width, cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun SummaryRow(state: MainUiState) {
    val weekStart = weekStartOf(state.today, state.settings.weekStart)
    val weekSteps = state.history.filterKeys { !it.isBefore(weekStart) && !it.isAfter(state.today) }.values.sum()
    val weeklyGoal = state.settings.weeklyGoal
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_fire),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(stringResource(R.string.streak), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    stringResource(R.string.days_count, state.achievements.records.currentStreak),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    stringResource(R.string.streak_best, state.achievements.records.longestStreak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Card(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.this_week), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(format(weekSteps), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
                LinearProgressIndicator(
                    progress = { (weekSteps.toFloat() / weeklyGoal).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    stringResource(R.string.goal_short, format(weeklyGoal)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeekCard(week: List<DaySteps>, goal: Long) {
    val scale = maxOf(week.maxOfOrNull { it.steps } ?: 0L, goal).coerceAtLeast(1L)
    val goalLine = (CHART_HEIGHT * (goal.toFloat() / scale)).coerceAtMost(CHART_HEIGHT - 1.dp)
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.last_7_days), style = MaterialTheme.typography.titleMedium)
            Box(modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(CHART_HEIGHT)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(bottom = goalLine)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    week.forEachIndexed { index, day ->
                        Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                            Bar(day.steps, scale, goal, isToday = index == week.lastIndex)
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                week.forEachIndexed { index, day ->
                    val isToday = index == week.lastIndex
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            formatCompact(day.steps),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            shortDayName(day.date),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(steps: Long, scale: Long, goal: Long, isToday: Boolean) {
    val primary = MaterialTheme.colorScheme.primary
    val color = when {
        steps >= goal -> primary
        isToday -> primary.copy(alpha = 0.7f)
        else -> primary.copy(alpha = 0.35f)
    }
    val height = (CHART_HEIGHT * (steps.toFloat() / scale)).coerceAtLeast(if (steps > 0L) 4.dp else 0.dp)
    Box(
        modifier = Modifier
            .width(18.dp)
            .height(height)
            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
            .background(color),
    )
}

@Composable
private fun SwitchCard(title: String, text: String, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Card {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onChange, modifier = Modifier.padding(start = 12.dp))
        }
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
