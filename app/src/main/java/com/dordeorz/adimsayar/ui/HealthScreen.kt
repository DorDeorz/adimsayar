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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BandPeriod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle

@Composable
fun HealthScreen(state: MainUiState, loadBandPeriod: suspend (LocalDate, Long) -> BandPeriod?, onOpenSettings: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.wearableEnabled) {
            item { HealthCard(state.today, loadBandPeriod) }
        } else {
            item {
                Card {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.health_off_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.health_off_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RestingChart(title: String, days: List<Pair<LocalDate, Long?>>) {
    val values = days.mapNotNull { it.second }
    if (values.isEmpty()) return
    val max = values.max()
    val min = (values.min() - RESTING_CHART_FLOOR).coerceAtLeast(0L)
    val range = (max - min).coerceAtLeast(1L)
    val color = MaterialTheme.colorScheme.tertiary
    val wide = days.size <= WEEK_DAYS
    val spacing = if (wide) 6.dp else 2.dp
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT).padding(top = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            days.forEach { (_, bpm) ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bpm != null) {
                        if (wide) Text(bpm.toString(), style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((CHART_HEIGHT - 28.dp) * ((bpm - min).toFloat() / range))
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(color),
                        )
                    }
                }
            }
        }
        if (wide) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                days.forEach { (date, _) ->
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, appLocale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    DAY_FORMAT.format(days.first().first),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.health_min_max, values.min(), values.max()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DAY_FORMAT.format(days.last().first),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HealthCard(today: LocalDate, loadBandPeriod: suspend (LocalDate, Long) -> BandPeriod?) {
    var daysValue by rememberSaveable { mutableIntStateOf(WEEK_DAYS) }
    val days = daysValue.toLong()
    val period by produceState<BandPeriod?>(null, today, days) {
        value = null
        value = withContext(Dispatchers.IO) { loadBandPeriod(today, days) }
    }
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.health_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(WEEK_DAYS, MONTH_DAYS).forEach { option ->
                    FilterChip(
                        selected = option == daysValue,
                        onClick = { daysValue = option },
                        label = { Text(stringResource(R.string.health_last_days, option)) },
                    )
                }
            }
            val data = period
            val sleep = data?.sleep.orEmpty()
            val resting = data?.resting?.mapNotNull { it.second }.orEmpty()
            val exercises = data?.exercises.orEmpty()
            if (data == null || (sleep.isEmpty() && resting.isEmpty() && exercises.isEmpty() && data.distanceKm == null)) {
                Text(
                    stringResource(if (data == null) R.string.health_loading else R.string.health_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            if (sleep.isNotEmpty()) {
                val average = sleep.values.sum() / sleep.size
                val longest = sleep.entries.maxBy { it.value }
                RecordRow(
                    stringResource(R.string.health_sleep_average),
                    stringResource(R.string.band_sleep_value, average / 60, average % 60),
                    stringResource(R.string.health_nights, sleep.size),
                )
                RecordRow(
                    stringResource(R.string.health_sleep_longest),
                    stringResource(R.string.band_sleep_value, longest.value / 60, longest.value % 60),
                    FULL_DATE_FORMAT.format(longest.key),
                )
            } else {
                RecordRow(
                    stringResource(R.string.health_sleep_average),
                    "-",
                    stringResource(if (data.sleepAllowed) R.string.health_no_records else R.string.health_no_permission),
                )
            }
            if (resting.isNotEmpty()) {
                RecordRow(stringResource(R.string.health_resting_average), stringResource(R.string.health_bpm, resting.sum() / resting.size), null)
            }
            if (exercises.isNotEmpty()) {
                val longest = exercises.maxBy { it.minutes }
                RecordRow(
                    stringResource(R.string.health_exercise_total),
                    stringResource(R.string.duration_minutes, exercises.sumOf { it.minutes }),
                    stringResource(R.string.health_exercise_count, exercises.size),
                )
                RecordRow(
                    stringResource(R.string.health_exercise_longest),
                    stringResource(R.string.duration_minutes, longest.minutes),
                    (longest.title?.takeIf { it.isNotBlank() } ?: stringResource(exerciseLabel(longest.kind))) + " · " +
                        FULL_DATE_FORMAT.format(longest.start.atZone(ZoneId.systemDefault()).toLocalDate()),
                )
            } else {
                RecordRow(
                    stringResource(R.string.health_exercise_total),
                    "-",
                    stringResource(if (data.exerciseAllowed) R.string.health_no_records else R.string.health_no_permission),
                )
            }
            data.distanceKm?.let { km ->
                RecordRow(stringResource(R.string.health_distance), stringResource(R.string.health_km, formatDecimal(km)), null)
            }
            if (resting.isNotEmpty()) {
                Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                    RestingChart(stringResource(R.string.health_resting_chart), data.resting)
                }
            }
        }
    }
}

private const val RESTING_CHART_FLOOR = 5L

private const val WEEK_DAYS = 7

private const val MONTH_DAYS = 30

private val CHART_HEIGHT = 72.dp
