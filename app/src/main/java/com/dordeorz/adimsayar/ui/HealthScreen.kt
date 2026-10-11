package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BandPeriod
import com.dordeorz.adimsayar.data.BmiCategory
import com.dordeorz.adimsayar.data.HealthMath
import com.dordeorz.adimsayar.data.HeartHour
import com.dordeorz.adimsayar.data.SleepStage
import com.dordeorz.adimsayar.data.Weight
import com.dordeorz.adimsayar.data.Weights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

@Composable
fun HealthScreen(state: MainUiState, actions: MainActions, onOpenSettings: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.wearableEnabled) {
            item { HealthCard(state.today, state.healthRefresh, actions) }
            item { HeartTodayCard(state.today, state.healthRefresh, actions.loadHeartHours) }
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
        item { WeightCard(state, actions) }
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
private fun HealthCard(today: LocalDate, refresh: Int, actions: MainActions) {
    var daysValue by rememberSaveable { mutableIntStateOf(WEEK_DAYS) }
    val days = daysValue.toLong()
    val period by produceState<BandPeriod?>(null, today, days, refresh) {
        value = null
        value = withContext(Dispatchers.IO) { actions.loadBandPeriod(today, days) }
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
            if (data?.missingPermissions == true) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.health_permissions_missing),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = actions.onGrantHealthPermissions) { Text(stringResource(R.string.health_grant)) }
                }
            }
            val nights = data?.nights.orEmpty()
            val resting = data?.resting?.mapNotNull { it.second }.orEmpty()
            val exercises = data?.exercises.orEmpty()
            val kcal = data?.activeKcal.orEmpty().filterValues { it > 0L }
            if (data == null || (nights.isEmpty() && resting.isEmpty() && exercises.isEmpty() && data.distanceKm == null && kcal.isEmpty() && data.oxygen == null)) {
                Text(
                    stringResource(if (data == null) R.string.health_loading else R.string.health_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            val zone = ZoneId.systemDefault()

            if (nights.isNotEmpty()) {
                val average = HealthMath.average(nights.values.map { it.minutes }) ?: 0L
                val longest = nights.entries.maxBy { it.value.minutes }
                RecordRow(
                    stringResource(R.string.health_sleep_average),
                    sleepText(average),
                    stringResource(R.string.health_nights, nights.size),
                )
                RecordRow(stringResource(R.string.health_sleep_longest), sleepText(longest.value.minutes), FULL_DATE_FORMAT.format(longest.key))
                val bed = HealthMath.averageBedtime(nights.values.map { it.bedtime }, zone)
                val wake = HealthMath.averageWake(nights.values.map { it.wake }, zone)
                if (bed != null && wake != null) {
                    RecordRow(
                        stringResource(R.string.health_bed_wake),
                        stringResource(R.string.health_time_range, CLOCK_FORMAT.format(bed), CLOCK_FORMAT.format(wake)),
                        null,
                    )
                }
                val staged = nights.values.filter { it.stages.isNotEmpty() }
                if (staged.isNotEmpty()) {
                    val averages = SleepStage.entries.associateWith { stage -> staged.sumOf { it.stages[stage] ?: 0L } / staged.size }
                        .filterValues { it > 0L }
                    SleepStages(averages, staged.size)
                }
            } else {
                RecordRow(
                    stringResource(R.string.health_sleep_average),
                    "-",
                    stringResource(if (data.sleepAllowed) R.string.health_no_records else R.string.health_no_permission),
                )
            }

            HealthMath.average(resting)?.let { average ->
                val before = data.restingBefore
                val trend = when {
                    before == null -> null
                    average < before -> stringResource(R.string.health_resting_down, daysValue, before - average)
                    average > before -> stringResource(R.string.health_resting_up, daysValue, average - before)
                    else -> stringResource(R.string.health_resting_same, daysValue)
                }
                RecordRow(stringResource(R.string.health_resting_average), stringResource(R.string.health_bpm, average), trend)
            }

            val oxygen = data.oxygen
            if (oxygen != null) {
                RecordRow(
                    stringResource(R.string.health_oxygen),
                    stringResource(R.string.health_percent, formatDecimal(oxygen.average)),
                    stringResource(R.string.health_oxygen_hint, stringResource(R.string.health_percent, formatDecimal(oxygen.min)), oxygen.count),
                )
            } else {
                RecordRow(
                    stringResource(R.string.health_oxygen),
                    "-",
                    stringResource(if (data.oxygenAllowed) R.string.health_no_records else R.string.health_no_permission),
                )
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
                        FULL_DATE_FORMAT.format(longest.start.atZone(zone).toLocalDate()),
                )
                exercises.groupBy { it.kind }.entries.sortedByDescending { (_, list) -> list.sumOf { it.minutes } }.forEach { (kind, list) ->
                    RecordRow(
                        "· " + stringResource(exerciseLabel(kind)),
                        stringResource(R.string.duration_minutes, list.sumOf { it.minutes }),
                        stringResource(R.string.health_exercise_count, list.size),
                    )
                }
                ExerciseDays(data.days, exercises.map { it.start.atZone(zone).toLocalDate() }.toSet())
            } else {
                RecordRow(
                    stringResource(R.string.health_exercise_total),
                    "-",
                    stringResource(if (data.exerciseAllowed) R.string.health_no_records else R.string.health_no_permission),
                )
            }

            if (kcal.isNotEmpty()) {
                val total = kcal.values.sum()
                RecordRow(
                    stringResource(R.string.band_active_kcal),
                    stringResource(R.string.band_kcal_value, format(total)),
                    stringResource(R.string.health_kcal_daily, format(total / kcal.size)),
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

@Composable
private fun sleepText(minutes: Long): String = stringResource(R.string.band_sleep_value, minutes / 60, minutes % 60)

@Composable
private fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.Deep -> MaterialTheme.colorScheme.primary
    SleepStage.Light -> MaterialTheme.colorScheme.primaryContainer
    SleepStage.Rem -> MaterialTheme.colorScheme.tertiary
    SleepStage.Awake -> MaterialTheme.colorScheme.error
}

private fun stageLabel(stage: SleepStage): Int = when (stage) {
    SleepStage.Deep -> R.string.sleep_deep
    SleepStage.Light -> R.string.sleep_light
    SleepStage.Rem -> R.string.sleep_rem
    SleepStage.Awake -> R.string.sleep_awake
}

@Composable
private fun SleepStages(averages: Map<SleepStage, Long>, nights: Int) {
    if (averages.isEmpty()) return
    val total = averages.values.sum().coerceAtLeast(1L)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.health_sleep_stages), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.health_nights_average, nights),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(STAGE_BAR_HEIGHT).clip(RoundedCornerShape(6.dp))) {
            averages.forEach { (stage, minutes) ->
                Box(modifier = Modifier.weight(minutes.toFloat() / total).fillMaxSize().background(stageColor(stage)))
            }
        }
        averages.forEach { (stage, minutes) ->
            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(stageColor(stage)))
                Text(
                    stringResource(stageLabel(stage)),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Text(
                    sleepText(minutes) + " · " + stringResource(R.string.health_percent, (minutes * 100 / total).toString()),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ExerciseDays(days: List<LocalDate>, active: Set<LocalDate>) {
    val count = days.count { it in active }
    val wide = days.size <= WEEK_DAYS
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.health_exercise_days), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.health_days_ratio, count, days.size), style = MaterialTheme.typography.titleMedium)
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(if (wide) 6.dp else 2.dp)) {
            days.forEach { date ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (wide) 20.dp else 14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (date in active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                    )
                    if (wide) {
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.NARROW, appLocale),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeartTodayCard(today: LocalDate, refresh: Int, loadHeartHours: suspend (LocalDate) -> List<HeartHour>?) {
    val hours by produceState<List<HeartHour>?>(null, today, refresh) {
        value = withContext(Dispatchers.IO) { loadHeartHours(today) } ?: emptyList()
    }
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.heart_today_title), style = MaterialTheme.typography.titleMedium)
            val data = hours
            if (data.isNullOrEmpty()) {
                Text(
                    stringResource(if (data == null) R.string.health_loading else R.string.heart_today_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }
            val low = data.minOf { it.min }
            val high = data.maxOf { it.max }
            Text(
                stringResource(R.string.health_min_max, low, high),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val floor = (low - HEART_CHART_MARGIN).coerceAtLeast(0L)
            val range = (high + HEART_CHART_MARGIN - floor).toFloat()
            val color = MaterialTheme.colorScheme.error
            val byHour = data.associateBy { it.hour }
            Canvas(modifier = Modifier.fillMaxWidth().height(HEART_CHART_HEIGHT).padding(top = 12.dp)) {
                val slot = size.width / HOURS
                val stroke = (slot * 0.5f).coerceAtMost(8.dp.toPx())
                for (hour in 0 until HOURS) {
                    val entry = byHour[hour] ?: continue
                    val x = slot * hour + slot / 2
                    val top = size.height * (1f - (entry.max - floor) / range)
                    val bottom = size.height * (1f - (entry.min - floor) / range)
                    drawLine(color, Offset(x, top), Offset(x, maxOf(bottom, top + 1f)), strokeWidth = stroke, cap = StrokeCap.Round)
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf(0, 6, 12, 18).forEach { hour ->
                    Text(
                        CLOCK_FORMAT.format(LocalTime.of(hour, 0)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightCard(state: MainUiState, actions: MainActions) {
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    val weights = state.weights
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.weight_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val latest = weights.lastOrNull()
            if (latest == null) {
                Text(
                    stringResource(R.string.weight_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                RecordRow(stringResource(R.string.weight_current), stringResource(R.string.weight_kg, formatDecimal(latest.kg)), FULL_DATE_FORMAT.format(latest.date))
                HealthMath.bmi(latest.kg, state.settings.heightCm)?.let { bmi ->
                    RecordRow(
                        stringResource(R.string.weight_bmi),
                        formatDecimal(bmi),
                        stringResource(R.string.weight_bmi_hint, stringResource(bmiLabel(HealthMath.bmiCategory(bmi))), state.settings.heightCm),
                    )
                }
                if (weights.size >= 2) {
                    val first = weights.first()
                    val previous = weights[weights.size - 2]
                    RecordRow(
                        stringResource(R.string.weight_total_change),
                        stringResource(R.string.weight_kg, signed(latest.kg - first.kg)),
                        stringResource(R.string.weight_since, FULL_DATE_FORMAT.format(first.date)),
                    )
                    RecordRow(
                        stringResource(R.string.weight_last_change),
                        stringResource(R.string.weight_kg, signed(latest.kg - previous.kg)),
                        FULL_DATE_FORMAT.format(previous.date),
                    )
                    WeightChart(weights.takeLast(WEIGHT_CHART_ENTRIES))
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                Text(
                    stringResource(R.string.weight_entries),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                weights.takeLast(WEIGHT_LIST_ENTRIES).reversed().forEach { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editing = entry.date.toString() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(FULL_DATE_FORMAT.format(entry.date), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.weight_kg, formatDecimal(entry.kg)), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Button(onClick = { editing = state.today.toString() }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(stringResource(R.string.weight_add))
            }
        }
    }
    editing?.let { dateText ->
        val date = LocalDate.parse(dateText)
        val existing = weights.firstOrNull { it.date == date }
        WeightDialog(
            date = date,
            current = existing?.kg ?: weights.lastOrNull()?.kg ?: state.settings.weightKg.toDouble(),
            canDelete = existing != null,
            onSave = { actions.onAddWeight(date, it) },
            onDelete = { actions.onDeleteWeight(date) },
            onDismiss = { editing = null },
        )
    }
}

private fun signed(kg: Double): String = when {
    abs(kg) < 0.05 -> formatDecimal(0.0)
    kg > 0 -> "+" + formatDecimal(kg)
    else -> "−" + formatDecimal(-kg)
}

private fun bmiLabel(category: BmiCategory): Int = when (category) {
    BmiCategory.Under -> R.string.bmi_under
    BmiCategory.Normal -> R.string.bmi_normal
    BmiCategory.Over -> R.string.bmi_over
    BmiCategory.Obese -> R.string.bmi_obese
}

@Composable
private fun WeightChart(entries: List<Weight>) {
    val low = entries.minOf { it.kg }
    val high = entries.maxOf { it.kg }
    val range = (high - low).coerceAtLeast(1.0)
    val color = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            val radius = 4.dp.toPx()
            val step = if (entries.size > 1) (size.width - radius * 2) / (entries.size - 1) else 0f
            val points = entries.mapIndexed { index, entry ->
                Offset(radius + step * index, radius + (size.height - radius * 2) * (1f - ((entry.kg - low) / range).toFloat()))
            }
            points.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round) }
            points.forEach { drawCircle(color, radius, it) }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                DAY_FORMAT.format(entries.first().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(R.string.weight_range, formatDecimal(low), formatDecimal(high)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Text(
                DAY_FORMAT.format(entries.last().date),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WeightDialog(
    date: LocalDate,
    current: Double,
    canDelete: Boolean,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(String.format(Locale.ROOT, "%.1f", current)) }
    val value = text.replace(',', '.').toDoubleOrNull()
    val valid = value != null && value in Weights.KG_RANGE
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.weight_dialog_title, FULL_DATE_FORMAT.format(date))) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    val cleaned = input.filter { it.isDigit() || it == ',' || it == '.' }
                    if (cleaned.count { it == ',' || it == '.' } <= 1 && cleaned.length <= WEIGHT_INPUT_LENGTH) text = cleaned
                },
                singleLine = true,
                isError = !valid,
                suffix = { Text(stringResource(R.string.unit_kg)) },
                supportingText = {
                    Text(stringResource(R.string.goal_range, Weights.KG_RANGE.start.toInt().toString(), Weights.KG_RANGE.endInclusive.toInt().toString()))
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value != null) onSave(Math.round(value * 10) / 10.0)
                    onDismiss()
                },
                enabled = valid,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (canDelete) {
                    TextButton(onClick = {
                        onDelete()
                        onDismiss()
                    }) { Text(stringResource(R.string.weight_delete)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

private const val RESTING_CHART_FLOOR = 5L

private const val HEART_CHART_MARGIN = 5L

private const val HOURS = 24

private const val WEEK_DAYS = 7

private const val MONTH_DAYS = 30

private const val WEIGHT_CHART_ENTRIES = 12

private const val WEIGHT_LIST_ENTRIES = 5

private const val WEIGHT_INPUT_LENGTH = 5

private val CHART_HEIGHT = 72.dp

private val HEART_CHART_HEIGHT = 96.dp

private val STAGE_BAR_HEIGHT = 14.dp
