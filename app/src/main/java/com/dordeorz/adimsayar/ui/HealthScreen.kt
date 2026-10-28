package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BandPeriod
import com.dordeorz.adimsayar.data.BmiCategory
import com.dordeorz.adimsayar.data.HealthMath
import com.dordeorz.adimsayar.data.HeartDay
import com.dordeorz.adimsayar.data.HeartSlot
import com.dordeorz.adimsayar.data.HeartZone
import com.dordeorz.adimsayar.data.RestingCategory
import com.dordeorz.adimsayar.data.WearableHealth
import com.dordeorz.adimsayar.data.SleepStage
import com.dordeorz.adimsayar.data.SleepInsights
import com.dordeorz.adimsayar.data.SourceReport
import com.dordeorz.adimsayar.data.Weight
import com.dordeorz.adimsayar.data.Weights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HealthScreen(state: MainUiState, actions: MainActions, onOpenSettings: () -> Unit) {
    var setup by rememberSaveable { mutableStateOf(false) }
    if (setup) {
        WearableSetupScreen(state, actions, onClose = { setup = false })
        return
    }
    val sources by produceState<SourceReport?>(null, state.healthEnabled, state.healthRefresh) {
        value = if (state.healthEnabled) withContext(Dispatchers.IO) { actions.loadSources() } else null
    }
    var daysValue by rememberSaveable { mutableIntStateOf(WEEK_DAYS) }
    val period by produceState<BandPeriod?>(null, state.healthEnabled, state.today, daysValue, state.healthRefresh) {
        value = null
        if (state.healthEnabled) value = withContext(Dispatchers.IO) { actions.loadBandPeriod(state.today, daysValue.toLong()) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.healthEnabled) {
            if (sources?.hasWearable == false) item { SourceWarningCard(onSetup = { setup = true }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PERIOD_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = option == daysValue,
                            onClick = { daysValue = option },
                            label = { Text(stringResource(R.string.health_last_days, option)) },
                        )
                    }
                }
            }
            val data = period
            if (data?.missingPermissions == true) {
                item {
                    Card {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
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
                }
            }
            if (data == null) {
                item {
                    Text(
                        stringResource(R.string.health_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                item { SleepCard(data) }
                if (data.nights.keys.any { it in data.days }) item { SleepInsightCard(data, state) }
                item { HeartDetailCard(state.today, state.healthRefresh, state.settings.age, actions.loadHeartDay) }
                item { RestingCard(data, daysValue) }
                item { OxygenCard(data) }
                if (data.stressDaily.isNotEmpty()) item { StressCard(data) }
                item { ExerciseCard(data) }
                if (data.activeKcal.values.any { it > 0L } || data.distanceKm != null) item { ActivityCard(data) }
            }
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { setup = true }) { Text(stringResource(R.string.setup_open)) }
                            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
                        }
                    }
                }
            }
        }
        item { WaterWeekCard(state) }
        item { WeightCard(state, actions) }
        if (state.healthEnabled) {
            item {
                TextButton(onClick = { setup = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.setup_title)) }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, content: @Composable () -> Unit) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp))
            content()
        }
    }
}

@Composable
private fun MetricHeadline(value: String, caption: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyMetric(allowed: Boolean) {
    Text(
        stringResource(if (allowed) R.string.health_no_records else R.string.health_no_permission),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun dayCaption(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> stringResource(R.string.today)
    today.minusDays(1) -> stringResource(R.string.yesterday)
    else -> FULL_DATE_FORMAT.format(date)
}

@Composable
private fun DailyBars(
    days: List<LocalDate>,
    values: Map<LocalDate, Double>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
    color: Color,
    floor: Double = 0.0,
    label: (Double) -> String,
) {
    val present = days.mapNotNull { values[it] }
    if (present.isEmpty()) return
    val low = floor.coerceAtMost(present.min())
    val range = (present.max() - low).coerceAtLeast(1.0)
    val wide = days.size <= WEEK_DAYS
    val spacing = if (wide) 6.dp else if (days.size <= MONTH_DAYS) 2.dp else 1.dp
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(BAR_CHART_HEIGHT),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(spacing),
        ) {
            days.forEach { date ->
                val value = values[date]
                val active = selected == null || selected == date
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = value != null) { onSelect(date) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (value != null) {
                        if (wide) Text(label(value), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(((BAR_CHART_HEIGHT - 28.dp) * ((value - low) / range).toFloat()).coerceAtLeast(3.dp))
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (active) color else color.copy(alpha = 0.35f)),
                        )
                    }
                }
            }
        }
        if (wide) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(spacing)) {
                days.forEach { date ->
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, appLocale),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (date == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    DAY_FORMAT.format(days.first()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DAY_FORMAT.format(days.last()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            stringResource(R.string.health_tap_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SleepCard(data: BandPeriod) {
    val nights = data.nights.filterKeys { it in data.days }
    MetricCard(stringResource(R.string.health_sleep_title)) {
        if (nights.isEmpty()) {
            EmptyMetric(data.sleepAllowed)
            return@MetricCard
        }
        val zone = ZoneId.systemDefault()
        val latest = nights.keys.max()
        var selectedText by rememberSaveable(latest.toString()) { mutableStateOf(latest.toString()) }
        val selected = LocalDate.parse(selectedText).takeIf { it in nights } ?: latest
        val night = nights.getValue(selected)
        val caption = (if (selected == latest) stringResource(R.string.health_last_night) + " · " else "") + dayCaption(selected) + " · " +
            stringResource(
                R.string.health_time_range,
                CLOCK_FORMAT.format(night.bedtime.atZone(zone).toLocalTime()),
                CLOCK_FORMAT.format(night.wake.atZone(zone).toLocalTime()),
            )
        MetricHeadline(sleepText(night.minutes), caption)
        SleepStages(SleepStage.entries.mapNotNull { stage -> night.stages[stage]?.let { stage to it } }.toMap(), null)
        DailyBars(
            days = data.days,
            values = nights.mapValues { it.value.minutes.toDouble() },
            selected = selected,
            onSelect = { selectedText = it.toString() },
            color = MaterialTheme.colorScheme.primary,
            label = { formatDecimal(it / 60.0) },
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        val average = HealthMath.average(nights.values.map { it.minutes }) ?: 0L
        val longest = nights.entries.maxBy { it.value.minutes }
        val shortest = nights.entries.minBy { it.value.minutes }
        RecordRow(stringResource(R.string.health_sleep_average), sleepText(average), stringResource(R.string.health_nights, nights.size))
        RecordRow(stringResource(R.string.health_sleep_longest), sleepText(longest.value.minutes), FULL_DATE_FORMAT.format(longest.key))
        if (nights.size > 1) {
            RecordRow(stringResource(R.string.health_sleep_shortest), sleepText(shortest.value.minutes), FULL_DATE_FORMAT.format(shortest.key))
        }
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
        if (staged.size > 1) {
            val averages = SleepStage.entries.associateWith { stage -> staged.sumOf { it.stages[stage] ?: 0L } / staged.size }
                .filterValues { it > 0L }
            SleepStages(averages, staged.size)
        }
    }
}

@Composable
private fun SleepInsightCard(data: BandPeriod, state: MainUiState) {
    val goal = state.settings.goalOn(state.today)
    val insight = remember(data, state.history, state.today, goal) {
        SleepInsights.compute(data.nights.filterKeys { it in data.days }, state.history, state.today, goal)
    }
    MetricCard(stringResource(R.string.insight_title)) {
        val steps = insight.steps
        val sleep = insight.sleep
        if (steps == null && sleep == null) {
            Text(
                stringResource(R.string.insight_not_enough, SleepInsights.MIN_GROUP),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            return@MetricCard
        }
        val hours = SleepInsights.GOOD_NIGHT_MINUTES / 60
        if (steps != null) {
            val difference = steps.goodSteps - steps.shortSteps
            val headline = when {
                difference > 0L -> stringResource(R.string.insight_steps_more, hours, format(difference))
                difference < 0L -> stringResource(R.string.insight_steps_less, hours, format(-difference))
                else -> stringResource(R.string.insight_steps_same, hours)
            }
            Text(headline, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            RecordRow(
                stringResource(R.string.insight_after_long, hours),
                stringResource(R.string.steps_value, format(steps.goodSteps)),
                stringResource(R.string.health_nights, steps.goodNights),
            )
            RecordRow(
                stringResource(R.string.insight_after_short, hours),
                stringResource(R.string.steps_value, format(steps.shortSteps)),
                stringResource(R.string.health_nights, steps.shortNights),
            )
        }
        if (steps != null && sleep != null) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        if (sleep != null) {
            RecordRow(
                stringResource(R.string.insight_sleep_active),
                sleepText(sleep.activeSleep),
                stringResource(R.string.insight_days, sleep.activeDays),
            )
            RecordRow(
                stringResource(R.string.insight_sleep_rest),
                sleepText(sleep.restSleep),
                stringResource(R.string.insight_days, sleep.restDays),
            )
        }
        Text(
            stringResource(R.string.insight_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RestingCard(data: BandPeriod, days: Int) {
    val values = data.resting.mapNotNull { (date, bpm) -> bpm?.let { date to it.toDouble() } }.toMap()
    MetricCard(stringResource(R.string.health_resting_chart)) {
        if (values.isEmpty()) {
            EmptyMetric(true)
            return@MetricCard
        }
        val latest = values.keys.max()
        var selectedText by rememberSaveable(latest.toString()) { mutableStateOf(latest.toString()) }
        val selected = LocalDate.parse(selectedText).takeIf { it in values } ?: latest
        MetricHeadline(stringResource(R.string.health_bpm, values.getValue(selected).toLong()), dayCaption(selected))
        DailyBars(
            days = data.days,
            values = values,
            selected = selected,
            onSelect = { selectedText = it.toString() },
            color = MaterialTheme.colorScheme.error,
            floor = values.values.min() - RESTING_CHART_FLOOR,
            label = { it.toLong().toString() },
        )
        val average = HealthMath.average(values.values.map { it.toLong() }) ?: 0L
        val before = data.restingBefore
        val trend = when {
            before == null -> null
            average < before -> stringResource(R.string.health_resting_down, days, before - average)
            average > before -> stringResource(R.string.health_resting_up, days, average - before)
            else -> stringResource(R.string.health_resting_same, days)
        }
        RecordRow(stringResource(R.string.health_resting_average), stringResource(R.string.health_bpm, average), trend)
        RecordRow(
            stringResource(R.string.heart_lowest),
            stringResource(R.string.health_bpm, values.values.min().toLong()),
            FULL_DATE_FORMAT.format(values.entries.minBy { it.value }.key),
        )
    }
}

@Composable
private fun OxygenCard(data: BandPeriod) {
    val values = data.oxygenDaily.filterKeys { it in data.days }
    MetricCard(stringResource(R.string.health_oxygen)) {
        val oxygen = data.oxygen
        if (values.isEmpty() || oxygen == null) {
            EmptyMetric(data.oxygenAllowed)
            return@MetricCard
        }
        val latest = values.keys.max()
        var selectedText by rememberSaveable(latest.toString()) { mutableStateOf(latest.toString()) }
        val selected = LocalDate.parse(selectedText).takeIf { it in values } ?: latest
        MetricHeadline(stringResource(R.string.health_percent, formatDecimal(values.getValue(selected))), dayCaption(selected))
        DailyBars(
            days = data.days,
            values = values,
            selected = selected,
            onSelect = { selectedText = it.toString() },
            color = MaterialTheme.colorScheme.tertiary,
            floor = values.values.min() - 2.0,
            label = { it.toLong().toString() },
        )
        RecordRow(
            stringResource(R.string.health_oxygen_average),
            stringResource(R.string.health_percent, formatDecimal(oxygen.average)),
            stringResource(R.string.health_oxygen_hint, stringResource(R.string.health_percent, formatDecimal(oxygen.min)), oxygen.count),
        )
    }
}

private fun stressLevel(value: Long): Int = when {
    value < 40L -> R.string.stress_relaxed
    value < 60L -> R.string.stress_normal
    value < 80L -> R.string.stress_medium
    else -> R.string.stress_high
}

@Composable
private fun StressCard(data: BandPeriod) {
    val values = data.stressDaily.filterKeys { it in data.days }
    MetricCard(stringResource(R.string.health_stress_title)) {
        if (values.isEmpty()) {
            EmptyMetric(true)
            return@MetricCard
        }
        val latest = values.keys.max()
        var selectedText by rememberSaveable(latest.toString()) { mutableStateOf(latest.toString()) }
        val selected = LocalDate.parse(selectedText).takeIf { it in values } ?: latest
        val value = values.getValue(selected)
        MetricHeadline("$value · ${stringResource(stressLevel(value))}", dayCaption(selected))
        DailyBars(
            days = data.days,
            values = values.mapValues { it.value.toDouble() },
            selected = selected,
            onSelect = { selectedText = it.toString() },
            color = MaterialTheme.colorScheme.secondary,
            label = { it.toLong().toString() },
        )
        val average = HealthMath.average(values.values.toList()) ?: 0L
        RecordRow(stringResource(R.string.health_stress_average), average.toString(), stringResource(stressLevel(average)))
        Text(
            stringResource(R.string.health_stress_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ExerciseCard(data: BandPeriod) {
    val exercises = data.exercises
    MetricCard(stringResource(R.string.health_exercise_title)) {
        if (exercises.isEmpty()) {
            EmptyMetric(data.exerciseAllowed)
            return@MetricCard
        }
        val zone = ZoneId.systemDefault()
        MetricHeadline(
            stringResource(R.string.duration_minutes, exercises.sumOf { it.minutes }),
            stringResource(R.string.health_exercise_count, exercises.size),
        )
        ExerciseDays(data.days, exercises.map { it.start.atZone(zone).toLocalDate() }.toSet())
        exercises.groupBy { it.kind }.entries.sortedByDescending { (_, list) -> list.sumOf { it.minutes } }.forEach { (kind, list) ->
            RecordRow(
                stringResource(exerciseLabel(kind)),
                stringResource(R.string.duration_minutes, list.sumOf { it.minutes }),
                stringResource(R.string.health_exercise_count, list.size),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Text(
            stringResource(R.string.health_exercise_recent),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        exercises.sortedByDescending { it.start }.take(RECENT_EXERCISES).forEach { exercise ->
            val start = exercise.start.atZone(zone)
            RecordRow(
                exercise.title?.takeIf { it.isNotBlank() } ?: stringResource(exerciseLabel(exercise.kind)),
                stringResource(R.string.duration_minutes, exercise.minutes),
                dayCaption(start.toLocalDate()) + " · " + CLOCK_FORMAT.format(start.toLocalTime()),
            )
        }
    }
}

@Composable
private fun ActivityCard(data: BandPeriod) {
    val kcal = data.activeKcal.filterValues { it > 0L }.filterKeys { it in data.days }
    MetricCard(stringResource(R.string.health_activity_title)) {
        if (kcal.isNotEmpty()) {
            val latest = kcal.keys.max()
            var selectedText by rememberSaveable(latest.toString()) { mutableStateOf(latest.toString()) }
            val selected = LocalDate.parse(selectedText).takeIf { it in kcal } ?: latest
            MetricHeadline(stringResource(R.string.band_kcal_value, format(kcal.getValue(selected))), dayCaption(selected))
            DailyBars(
                days = data.days,
                values = kcal.mapValues { it.value.toDouble() },
                selected = selected,
                onSelect = { selectedText = it.toString() },
                color = MaterialTheme.colorScheme.primary,
                label = { formatCompact(it.toLong()) },
            )
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
private fun SleepStages(averages: Map<SleepStage, Long>, nights: Int?) {
    if (averages.isEmpty()) return
    val total = averages.values.sum().coerceAtLeast(1L)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (nights != null) {
            Text(stringResource(R.string.health_sleep_stages), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.health_nights_average, nights),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
private fun HeartDetailCard(today: LocalDate, refresh: Int, age: Int, loadHeartDay: suspend (LocalDate) -> HeartDay?) {
    var offset by rememberSaveable { mutableIntStateOf(0) }
    val date = today.minusDays(offset.toLong())
    val day by produceState<HeartDay?>(null, date, refresh) {
        value = null
        value = withContext(Dispatchers.IO) { loadHeartDay(date) } ?: HeartDay(emptyList(), null)
    }
    var selected by rememberSaveable(date.toString()) { mutableStateOf<Int?>(null) }
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.heart_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { offset++ }, enabled = offset < HEART_DAYS - 1) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.heart_previous_day))
                }
                Text(
                    if (offset == 0) stringResource(R.string.today) else DAY_FORMAT.format(date),
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = { offset-- }, enabled = offset > 0) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.heart_next_day))
                }
            }
            val data = day
            if (data == null || data.slots.isEmpty()) {
                Text(
                    stringResource(if (data == null) R.string.health_loading else R.string.heart_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }
            val slots = data.slots
            val lowest = slots.minBy { it.min }
            val highest = slots.maxBy { it.max }
            val average = HealthMath.average(slots.map { it.avg }) ?: 0L
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeartStat(stringResource(R.string.heart_resting), data.resting?.toString() ?: "-", null, Modifier.weight(1f))
                HeartStat(stringResource(R.string.heart_lowest), lowest.min.toString(), CLOCK_FORMAT.format(lowest.start), Modifier.weight(1f))
                HeartStat(stringResource(R.string.heart_average), average.toString(), null, Modifier.weight(1f))
                HeartStat(stringResource(R.string.heart_highest), highest.max.toString(), CLOCK_FORMAT.format(highest.start), Modifier.weight(1f))
            }
            data.resting?.let { resting ->
                Text(
                    stringResource(
                        when (HealthMath.restingCategory(resting)) {
                            RestingCategory.Low -> R.string.heart_resting_low
                            RestingCategory.Normal -> R.string.heart_resting_normal
                            RestingCategory.High -> R.string.heart_resting_high
                        },
                        resting,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            HeartChart(slots, selected) { selected = it }
            val hour = selected
            val hourText = if (hour == null) {
                stringResource(R.string.heart_tap_hint)
            } else {
                val from = CLOCK_FORMAT.format(LocalTime.of(hour, 0))
                val to = CLOCK_FORMAT.format(LocalTime.of(hour, 0).plusHours(1))
                val inHour = slots.filter { it.start.hour == hour }
                if (inHour.isEmpty()) {
                    stringResource(R.string.heart_hour_empty, from, to)
                } else {
                    stringResource(
                        R.string.heart_hour_detail,
                        from,
                        to,
                        inHour.minOf { it.min },
                        HealthMath.average(inHour.map { it.avg }) ?: 0L,
                        inHour.maxOf { it.max },
                    )
                }
            }
            Text(
                hourText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hour == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            HeartZones(slots.map { it.avg }, age)
            Text(
                stringResource(R.string.heart_explain),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun HeartStat(label: String, value: String, hint: String?, modifier: Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(hint ?: stringResource(R.string.heart_unit), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeartChart(slots: List<HeartSlot>, selected: Int?, onSelect: (Int) -> Unit) {
    val low = slots.minOf { it.min }
    val high = slots.maxOf { it.max }
    val step = when {
        high - low <= 50L -> 10L
        high - low <= 120L -> 20L
        else -> 40L
    }
    val floor = ((low - 5L).coerceAtLeast(0L) / step) * step
    val ceil = ((high + 5L + step - 1L) / step) * step
    val range = (ceil - floor).coerceAtLeast(1L).toFloat()
    val line = MaterialTheme.colorScheme.error
    val band = line.copy(alpha = 0.25f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val highlight = MaterialTheme.colorScheme.surfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    val hours = remember(slots) {
        slots.groupBy { it.start.hour }.mapValues { (_, list) -> list.minOf { it.min } to list.maxOf { it.max } }
    }
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(HEART_CHART_HEIGHT)
                .pointerInput(slots) {
                    detectTapGestures { tap ->
                        val gutter = HEART_GUTTER.toPx()
                        val slot = (size.width - gutter) / HOURS
                        if (tap.x >= gutter) onSelect(((tap.x - gutter) / slot).toInt().coerceIn(0, HOURS - 1))
                    }
                },
        ) {
            val gutter = HEART_GUTTER.toPx()
            val width = size.width - gutter
            val slot = width / HOURS
            fun y(bpm: Long) = size.height * (1f - (bpm - floor) / range)
            selected?.let { drawRect(highlight, Offset(gutter + slot * it, 0f), Size(slot, size.height)) }
            var mark = floor
            while (mark <= ceil) {
                val at = y(mark)
                drawLine(grid, Offset(gutter, at), Offset(size.width, at), strokeWidth = 1.dp.toPx())
                val label = measurer.measure(mark.toString(), labelStyle)
                drawText(label, topLeft = Offset(0f, (at - label.size.height / 2f).coerceIn(0f, size.height - label.size.height)))
                mark += step
            }
            for ((hour, value) in hours) {
                val x = gutter + slot * hour + slot / 2
                val top = y(value.second)
                drawLine(band, Offset(x, top), Offset(x, maxOf(y(value.first), top + 1f)), strokeWidth = slot * 0.6f, cap = StrokeCap.Round)
            }
            val minutesPerSlot = WearableHealth.HEART_SLOT_MINUTES
            var previous: Pair<Offset, Int>? = null
            for (entry in slots) {
                val minute = entry.start.hour * 60 + entry.start.minute
                val point = Offset(gutter + width * (minute + minutesPerSlot / 2f) / (HOURS * 60), y(entry.avg))
                val last = previous
                if (last != null && minute - last.second <= HEART_GAP_MINUTES) {
                    drawLine(line, last.first, point, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                } else {
                    drawCircle(line, 1.5.dp.toPx(), point)
                }
                previous = point to minute
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(start = HEART_GUTTER, top = 4.dp)) {
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

private val ZONE_COLORS = mapOf(
    HeartZone.Rest to Color(0xFF9E9E9E),
    HeartZone.Light to Color(0xFF64B5F6),
    HeartZone.FatBurn to Color(0xFF81C784),
    HeartZone.Cardio to Color(0xFFFFD54F),
    HeartZone.Peak to Color(0xFFFF8A65),
    HeartZone.Max to Color(0xFFE57373),
)

private fun zoneLabel(zone: HeartZone): Int = when (zone) {
    HeartZone.Rest -> R.string.heart_zone_rest
    HeartZone.Light -> R.string.heart_zone_light
    HeartZone.FatBurn -> R.string.heart_zone_fat
    HeartZone.Cardio -> R.string.heart_zone_cardio
    HeartZone.Peak -> R.string.heart_zone_peak
    HeartZone.Max -> R.string.heart_zone_max
}

@Composable
private fun HeartZones(averages: List<Long>, age: Int) {
    val maxRate = HealthMath.maxHeartRate(age)
    val minutes = HealthMath.zoneMinutes(averages, maxRate, WearableHealth.HEART_SLOT_MINUTES)
    val total = minutes.values.sum().coerceAtLeast(1L)
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.heart_zones_title), style = MaterialTheme.typography.labelLarge)
        Text(
            stringResource(R.string.heart_zones_hint, age, maxRate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(STAGE_BAR_HEIGHT).clip(RoundedCornerShape(6.dp))) {
            HeartZone.entries.forEach { zone ->
                val value = minutes[zone] ?: 0L
                if (value > 0L) Box(modifier = Modifier.weight(value.toFloat() / total).fillMaxSize().background(ZONE_COLORS.getValue(zone)))
            }
        }
        HeartZone.entries.forEach { zone ->
            val from = HealthMath.zoneBpm(zone, maxRate)
            val next = HeartZone.entries.getOrNull(zone.ordinal + 1)?.let { HealthMath.zoneBpm(it, maxRate) }
            val range = when {
                zone == HeartZone.Rest && next != null -> stringResource(R.string.heart_zone_below, next)
                next == null -> stringResource(R.string.heart_zone_above, from)
                else -> stringResource(R.string.heart_zone_range, from, next - 1)
            }
            val value = minutes[zone] ?: 0L
            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ZONE_COLORS.getValue(zone)))
                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(stringResource(zoneLabel(zone)), style = MaterialTheme.typography.bodyMedium)
                    Text(range, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    if (value >= 60L) sleepText(value) else stringResource(R.string.duration_minutes, value),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (value > 0L) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WaterWeekCard(state: MainUiState) {
    val days = (6 downTo 0).map { state.today.minusDays(it.toLong()) }
    val goal = state.settings.waterGoalMl
    val values = days.map { state.water[it] ?: 0 }
    val scale = maxOf(values.max(), goal).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.water_week_title), style = MaterialTheme.typography.titleMedium)
            val logged = values.filter { it > 0 }
            Text(
                stringResource(
                    R.string.water_week_summary,
                    format((if (logged.isEmpty()) 0 else logged.sum() / logged.size).toLong()),
                    values.count { it >= goal },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT).padding(top = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                values.forEach { ml ->
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (ml > 0) Text(formatCompact(ml.toLong()), style = MaterialTheme.typography.labelSmall)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((CHART_HEIGHT - 28.dp) * (ml.toFloat() / scale))
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(if (ml >= goal) primary else primary.copy(alpha = 0.4f)),
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEach { date ->
                    Text(
                        date.dayOfWeek.getDisplayName(TextStyle.NARROW, appLocale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
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
                        stringResource(R.string.weight_kg, signedKg(latest.kg - first.kg)),
                        stringResource(R.string.weight_since, FULL_DATE_FORMAT.format(first.date)),
                    )
                    RecordRow(
                        stringResource(R.string.weight_last_change),
                        stringResource(R.string.weight_kg, signedKg(latest.kg - previous.kg)),
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

private const val HEART_DAYS = 30

private const val HEART_GAP_MINUTES = 30

private const val HOURS = 24

private const val WEEK_DAYS = 7

private const val MONTH_DAYS = 30

private val PERIOD_OPTIONS = listOf(WEEK_DAYS, MONTH_DAYS, 90)

private const val RECENT_EXERCISES = 5

private val BAR_CHART_HEIGHT = 96.dp

private const val WEIGHT_CHART_ENTRIES = 12

private const val WEIGHT_LIST_ENTRIES = 5

private const val WEIGHT_INPUT_LENGTH = 5

private val CHART_HEIGHT = 72.dp

private val HEART_CHART_HEIGHT = 160.dp

private val HEART_GUTTER = 32.dp

private val STAGE_BAR_HEIGHT = 14.dp
