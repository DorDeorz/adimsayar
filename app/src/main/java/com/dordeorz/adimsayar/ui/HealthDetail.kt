package com.dordeorz.adimsayar.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BandDay
import com.dordeorz.adimsayar.data.BandPeriod
import com.dordeorz.adimsayar.data.DayReadings
import com.dordeorz.adimsayar.data.HealthAxis
import com.dordeorz.adimsayar.data.HealthMath
import com.dordeorz.adimsayar.data.HeartDay
import com.dordeorz.adimsayar.data.SleepDay
import com.dordeorz.adimsayar.data.SleepInsights
import com.dordeorz.adimsayar.data.SleepStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import kotlin.math.roundToLong

/** The Sağlık cards that open a detail page: a month calendar, and the tapped day in detail. */
enum class HealthMetric(@param:StringRes val title: Int, @param:StringRes val legend: Int) {
    Sleep(R.string.health_sleep_title, R.string.detail_legend_sleep),
    Heart(R.string.heart_title, R.string.detail_legend_heart),
    Resting(R.string.health_resting_chart, R.string.detail_legend_resting),
    Oxygen(R.string.health_oxygen, R.string.detail_legend_oxygen),
    Stress(R.string.health_stress_title, R.string.detail_legend_stress),
    Recovery(R.string.recovery_title, R.string.detail_legend_recovery),
    Exercise(R.string.health_exercise_title, R.string.detail_legend_exercise),
    Activity(R.string.health_activity_title, R.string.detail_legend_activity),
    Water(R.string.water_title, R.string.detail_legend_water),
    Weight(R.string.widget_weight_label, R.string.detail_legend_weight),
    ;

    /** Read from the band or Health Connect; water and weight are kept by the app itself. */
    val fromBand: Boolean get() = this != Water && this != Weight
}

@Composable
fun HealthDetailScreen(metric: HealthMetric, start: LocalDate, state: MainUiState, actions: MainActions, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var shown by rememberSaveable { mutableStateOf(metric) }
    val currentMonth = YearMonth.from(state.today)
    val firstMonth = remember(state.history, state.weights, state.water, currentMonth) {
        listOfNotNull(state.history.keys.minOrNull(), state.weights.firstOrNull()?.date, state.water.keys.minOrNull())
            .minOrNull()
            ?.let(YearMonth::from)
            ?.coerceIn(currentMonth.minusMonths(MAX_MONTHS_BACK), currentMonth)
            ?: currentMonth
    }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(start).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(start.toString()) }
    val month = YearMonth.parse(monthText).coerceIn(firstMonth, currentMonth)
    val selected = LocalDate.parse(selectedText)
    val last = if (month == currentMonth) state.today else month.atEndOfMonth()
    val fromBand = shown.fromBand && state.healthEnabled
    // A week more than the month: recovery compares each day with the week before it.
    val period by produceState<BandPeriod?>(null, month, last, fromBand, state.healthRefresh) {
        value = null
        if (fromBand) value = withContext(Dispatchers.IO) { actions.loadBandPeriod(last, last.dayOfMonth + Recovery.WINDOW_DAYS) }
    }
    val values = remember(shown, period, state.water, state.weights, month, last) {
        metricValues(shown, period, state).filterKeys { YearMonth.from(it) == month && !it.isAfter(last) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(shown.title), style = MaterialTheme.typography.titleLarge)
            }
        }
        if (shown == HealthMetric.Heart || shown == HealthMetric.Resting) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(HealthMetric.Heart to R.string.detail_heart_average, HealthMetric.Resting to R.string.health_resting_chart)
                        .forEach { (option, label) ->
                            FilterChip(selected = shown == option, onClick = { shown = option }, label = { Text(stringResource(label)) })
                        }
                }
            }
        }
        item {
            CalendarCard(
                metric = shown,
                month = month,
                last = last,
                state = state,
                period = period,
                values = values,
                loading = fromBand && period == null,
                selected = selected,
                canGoBack = month > firstMonth,
                canGoForward = month < currentMonth,
                onMonthChange = { next ->
                    monthText = next.toString()
                    selectedText = (if (next == currentMonth) state.today else next.atEndOfMonth()).toString()
                },
                onSelect = { selectedText = it.toString() },
            )
        }
        item { DayCard(shown, selected, state, actions, period) }
    }
}

private fun metricValues(metric: HealthMetric, period: BandPeriod?, state: MainUiState): Map<LocalDate, Double> = when (metric) {
    HealthMetric.Water -> state.water.filterValues { it > 0 }.mapValues { it.value.toDouble() }
    HealthMetric.Weight -> state.weights.associate { it.date to it.kg }
    else -> if (period == null) emptyMap() else bandValues(metric, period)
}

private fun bandValues(metric: HealthMetric, period: BandPeriod): Map<LocalDate, Double> = when (metric) {
    HealthMetric.Sleep -> period.nights.mapValues { it.value.minutes.toDouble() }
    HealthMetric.Heart -> period.heartDaily.mapValues { it.value.average.toDouble() }
    HealthMetric.Resting -> restingMap(period).mapValues { it.value.toDouble() }
    HealthMetric.Oxygen -> period.oxygenDaily
    HealthMetric.Stress -> period.stressDaily.mapValues { it.value.toDouble() }
    HealthMetric.Recovery -> Recovery.days(period.days, period.nights.mapValues { it.value.minutes }, restingMap(period))
        .mapValues { it.value.toDouble() }
    HealthMetric.Exercise -> period.exercises
        .groupBy { it.start.atZone(ZoneId.systemDefault()).toLocalDate() }
        .mapValues { (_, list) -> list.sumOf { it.minutes }.toDouble() }
    HealthMetric.Activity -> period.activeKcal.filterValues { it > 0L }.mapValues { it.value.toDouble() }
    HealthMetric.Water, HealthMetric.Weight -> emptyMap()
}

private fun restingMap(period: BandPeriod): Map<LocalDate, Long> = period.resting.mapNotNull { (date, bpm) -> bpm?.let { date to it } }.toMap()

/** The short number written in a calendar day. */
private fun cellLabel(metric: HealthMetric, value: Double): String = when (metric) {
    HealthMetric.Sleep -> formatDecimal(value / 60.0)
    HealthMetric.Water -> formatDecimal(value / 1000.0)
    HealthMetric.Weight -> formatDecimal(value)
    HealthMetric.Activity -> formatCompact(value.roundToLong())
    else -> value.roundToLong().toString()
}

/** How strongly a day is shaded, 0 to 1. Metrics with a goal reach 1 (drawn solid) on a good day; others are shaded by the month's range. */
private fun cellLevel(metric: HealthMetric, value: Double, low: Double, high: Double, state: MainUiState): Float {
    val goal = when (metric) {
        HealthMetric.Sleep -> SleepInsights.GOOD_NIGHT_MINUTES.toDouble()
        HealthMetric.Water -> state.settings.waterGoalMl.toDouble()
        HealthMetric.Recovery -> GOOD_RECOVERY
        HealthMetric.Exercise -> EXERCISE_GOAL_MINUTES
        else -> null
    }
    return when {
        goal != null -> (value / goal.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
        high > low -> (RELATIVE_FLOOR + RELATIVE_SPAN * (value - low) / (high - low)).toFloat()
        else -> RELATIVE_FLOOR + RELATIVE_SPAN / 2
    }
}

@Composable
private fun metricColor(metric: HealthMetric): Color = when (metric) {
    HealthMetric.Heart, HealthMetric.Resting -> MaterialTheme.colorScheme.error
    HealthMetric.Oxygen -> MaterialTheme.colorScheme.tertiary
    HealthMetric.Stress -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

@Composable
private fun CalendarCard(
    metric: HealthMetric,
    month: YearMonth,
    last: LocalDate,
    state: MainUiState,
    period: BandPeriod?,
    values: Map<LocalDate, Double>,
    loading: Boolean,
    selected: LocalDate,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val color = metricColor(metric)
    val low = values.values.minOrNull() ?: 0.0
    val high = values.values.maxOrNull() ?: 0.0
    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }, enabled = canGoBack) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.previous_month))
                }
                Text(
                    MONTH_FORMAT.format(month).replaceFirstChar { it.titlecase(appLocale) },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }, enabled = canGoForward) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.next_month))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                monthStats(metric, values, period, state).forEach { (label, value) -> MonthStat(label, value, Modifier.weight(1f)) }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                weekDays(state.settings.weekStart).forEach { day ->
                    Text(
                        day.getDisplayName(TextStyle.SHORT, appLocale),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val leading = (month.atDay(1).dayOfWeek.value - state.settings.weekStart.value + 7) % 7
            val cells: List<LocalDate?> = List(leading) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        Box(modifier = Modifier.weight(1f).padding(2.dp)) {
                            if (date != null) {
                                val value = values[date]
                                DetailDayCell(
                                    date = date,
                                    text = value?.let { cellLabel(metric, it) },
                                    level = value?.let { cellLevel(metric, it, low, high, state) },
                                    color = color,
                                    future = date.isAfter(last),
                                    selected = date == selected,
                                    onClick = { onSelect(date) },
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
            Text(
                stringResource(if (loading) R.string.health_loading else metric.legend),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp),
            )
        }
    }
}

@Composable
private fun DetailDayCell(date: LocalDate, text: String?, level: Float?, color: Color, future: Boolean, selected: Boolean, onClick: () -> Unit) {
    val solid = level != null && level >= 1f
    val background = when {
        future || level == null -> Color.Transparent
        solid -> color
        else -> color.copy(alpha = CELL_ALPHA_FLOOR + CELL_ALPHA_SPAN * level)
    }
    val textColor = when {
        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        solid -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .clip(shape)
            .background(background)
            .then(if (selected) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, shape) else Modifier)
            .clickable(enabled = !future, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelLarge, color = textColor)
        if (text != null && !future) Text(text, style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
    }
}

/** Three numbers for the month above the calendar. */
@Composable
private fun monthStats(metric: HealthMetric, values: Map<LocalDate, Double>, period: BandPeriod?, state: MainUiState): List<Pair<String, String>> {
    val list = values.values
    val average = if (list.isEmpty()) null else list.average()
    val lowest = list.minOrNull()
    val highest = list.maxOrNull()
    val count = values.size.toString()
    val recorded = stringResource(R.string.detail_days_recorded)
    return when (metric) {
        HealthMetric.Sleep -> listOf(
            stringResource(R.string.health_sleep_average) to (average?.let { sleepText(it.roundToLong()) } ?: DASH),
            recorded to count,
            stringResource(R.string.health_sleep_longest) to (highest?.let { sleepText(it.roundToLong()) } ?: DASH),
        )
        HealthMetric.Heart -> {
            val days = period?.heartDaily.orEmpty().filterKeys { it in values }.values
            listOf(
                stringResource(R.string.heart_average) to bpmText(average),
                stringResource(R.string.heart_lowest) to bpmText(days.minOfOrNull { it.min }?.toDouble()),
                stringResource(R.string.heart_highest) to bpmText(days.maxOfOrNull { it.max }?.toDouble()),
            )
        }
        HealthMetric.Resting -> listOf(
            stringResource(R.string.heart_average) to bpmText(average),
            stringResource(R.string.heart_lowest) to bpmText(lowest),
            recorded to count,
        )
        HealthMetric.Oxygen -> listOf(
            stringResource(R.string.heart_average) to (average?.let { stringResource(R.string.health_percent, formatDecimal(it)) } ?: DASH),
            stringResource(R.string.heart_lowest) to (lowest?.let { stringResource(R.string.health_percent, formatDecimal(it)) } ?: DASH),
            recorded to count,
        )
        HealthMetric.Stress -> listOf(
            stringResource(R.string.heart_average) to (average?.roundToLong()?.toString() ?: DASH),
            stringResource(R.string.heart_lowest) to (lowest?.roundToLong()?.toString() ?: DASH),
            stringResource(R.string.heart_highest) to (highest?.roundToLong()?.toString() ?: DASH),
        )
        HealthMetric.Recovery -> listOf(
            stringResource(R.string.heart_average) to (average?.let { stringResource(R.string.recovery_value, it.roundToLong().toInt()) } ?: DASH),
            stringResource(R.string.detail_best) to (highest?.let { stringResource(R.string.recovery_value, it.roundToLong().toInt()) } ?: DASH),
            recorded to count,
        )
        HealthMetric.Exercise -> listOf(
            stringResource(R.string.total) to stringResource(R.string.duration_minutes, list.sum().roundToLong()),
            recorded to count,
            stringResource(R.string.detail_best) to (highest?.let { stringResource(R.string.duration_minutes, it.roundToLong()) } ?: DASH),
        )
        HealthMetric.Activity -> listOf(
            stringResource(R.string.total) to stringResource(R.string.band_kcal_value, format(list.sum().roundToLong())),
            stringResource(R.string.daily_average) to (average?.let { stringResource(R.string.band_kcal_value, format(it.roundToLong())) } ?: DASH),
            stringResource(R.string.detail_best) to (highest?.let { stringResource(R.string.band_kcal_value, format(it.roundToLong())) } ?: DASH),
        )
        HealthMetric.Water -> listOf(
            stringResource(R.string.daily_average) to (average?.let { stringResource(R.string.water_ml, format(it.roundToLong())) } ?: DASH),
            stringResource(R.string.goal_days) to values.count { it.value >= state.settings.waterGoalMl }.toString(),
            stringResource(R.string.total) to stringResource(R.string.water_ml, format(list.sum().roundToLong())),
        )
        HealthMetric.Weight -> {
            val entries = values.entries.sortedBy { it.key }
            val change = if (entries.size >= 2) entries.last().value - entries.first().value else null
            listOf(
                stringResource(R.string.detail_last_entry) to (entries.lastOrNull()?.let { stringResource(R.string.weight_kg, formatDecimal(it.value)) } ?: DASH),
                stringResource(R.string.detail_change) to (change?.let { stringResource(R.string.weight_kg, signedKg(it)) } ?: DASH),
                recorded to count,
            )
        }
    }
}

@Composable
private fun bpmText(value: Double?): String = value?.let { stringResource(R.string.health_bpm, it.roundToLong()) } ?: DASH

@Composable
private fun DayCard(metric: HealthMetric, date: LocalDate, state: MainUiState, actions: MainActions, period: BandPeriod?) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Text(
                LONG_DAY_FORMAT.format(date).replaceFirstChar { it.titlecase(appLocale) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            when (metric) {
                HealthMetric.Sleep -> SleepDayDetail(date, state.healthRefresh, actions.loadSleepDay)
                HealthMetric.Heart, HealthMetric.Resting -> HeartDayDetail(date, state, actions.loadHeartDay)
                HealthMetric.Oxygen, HealthMetric.Stress -> ReadingsDayDetail(metric == HealthMetric.Oxygen, date, state.healthRefresh, actions.loadReadings)
                HealthMetric.Recovery -> RecoveryDayDetail(date, period)
                HealthMetric.Exercise -> ExerciseDayDetail(date, period)
                HealthMetric.Activity -> ActivityDayDetail(date, state, period, actions.loadBandDay)
                HealthMetric.Water -> WaterDayDetail(date, state)
                HealthMetric.Weight -> WeightDayDetail(date, state, actions)
            }
        }
    }
}

private class Loaded<T>(val value: T)

/** Loads on a background thread when a key changes; null while loading. */
@Composable
private fun <T> rememberLoaded(vararg keys: Any?, load: suspend () -> T): Loaded<T>? =
    produceState<Loaded<T>?>(null, *keys) {
        value = null
        value = Loaded(withContext(Dispatchers.IO) { load() })
    }.value

@Composable
private fun DetailText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SleepDayDetail(date: LocalDate, refresh: Int, load: suspend (LocalDate) -> SleepDay?) {
    val loaded = rememberLoaded(date, refresh) { load(date) }
    if (loaded == null) {
        DetailText(stringResource(R.string.health_loading))
        return
    }
    val day = loaded.value
    if (day == null) {
        DetailText(stringResource(R.string.detail_no_data))
        return
    }
    val zone = ZoneId.systemDefault()
    val night = day.night
    MetricHeadline(
        sleepText(night.minutes),
        stringResource(
            R.string.detail_bed_wake,
            CLOCK_FORMAT.format(night.bedtime.atZone(zone)),
            CLOCK_FORMAT.format(night.wake.atZone(zone)),
        ),
    )
    val (from, to) = remember(night) { HealthAxis.hoursAround(night.bedtime, night.wake, zone) }
    SectionTitle(stringResource(R.string.detail_sleep_hours))
    SleepTimeline(day, from, to)
    val inBed = day.parts.sumOf { Duration.between(it.start, it.end).toMinutes() }
    RecordRow(stringResource(R.string.detail_in_bed), sleepText(inBed), null)
    if (day.parts.size > 1) {
        SectionTitle(stringResource(R.string.detail_sleep_parts))
        day.parts.forEachIndexed { index, part ->
            RecordRow(
                stringResource(R.string.detail_sleep_part, index + 1),
                sleepText(part.minutes),
                stringResource(R.string.health_time_range, CLOCK_FORMAT.format(part.start.atZone(zone)), CLOCK_FORMAT.format(part.end.atZone(zone))),
            )
        }
    }
    val stages = SleepStage.entries.mapNotNull { stage -> night.stages[stage]?.let { stage to it } }.toMap()
    SectionTitle(stringResource(R.string.health_sleep_stages))
    if (stages.isNotEmpty()) SleepStages(stages, null) else DetailText(stringResource(R.string.detail_no_stages))
    if (day.heart.isNotEmpty()) {
        val bpm = day.heart.map { it.second }
        SectionTitle(stringResource(R.string.detail_sleep_heart))
        DetailText(stringResource(R.string.detail_heart_range, bpm.min(), HealthMath.average(bpm) ?: 0L, bpm.max()))
        TimeChart(day.heart.map { it.first to it.second.toDouble() }, from, to, MaterialTheme.colorScheme.error, HEART_JOIN_SECONDS) {
            it.roundToLong().toString()
        }
    }
    if (day.oxygen.isNotEmpty()) {
        val spo2 = day.oxygen.map { it.second }
        SectionTitle(stringResource(R.string.detail_sleep_oxygen))
        DetailText(
            stringResource(
                R.string.detail_oxygen_range,
                stringResource(R.string.health_percent, formatDecimal(spo2.average())),
                stringResource(R.string.health_percent, spo2.min().roundToLong().toString()),
                spo2.size,
            ),
        )
    }
}

/** When asleep, in hours: blocks for each part of the night, or the stages in rows when the source gives their times. */
@Composable
private fun SleepTimeline(day: SleepDay, from: Instant, to: Instant) {
    val zone = ZoneId.systemDefault()
    val staged = day.stages.isNotEmpty()
    val colors = SleepStage.entries.associateWith { stageColor(it) }
    val rowLabels = STAGE_ROWS.map { stringResource(stageLabel(it)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val asleep = MaterialTheme.colorScheme.primary
    val ticks = remember(from, to) { HealthAxis.ticks(from, to, zone) }
    val tickLabels = ticks.map { CLOCK_FORMAT.format(it.atZone(zone)) }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(if (staged) STAGED_TIMELINE_HEIGHT else TIMELINE_HEIGHT),
    ) {
        val bottom = size.height - AXIS_LABEL_SPACE.toPx()
        val rowLayouts = if (staged) rowLabels.map { measurer.measure(it, labelStyle) } else emptyList()
        val gutter = if (staged) (rowLayouts.maxOf { it.size.width } + 6.dp.toPx()) else 0f
        val width = size.width - gutter
        val span = (to.epochSecond - from.epochSecond).coerceAtLeast(1L).toFloat()
        fun x(time: Instant) = gutter + width * ((time.epochSecond - from.epochSecond) / span).coerceIn(0f, 1f)
        val radius = CornerRadius(4.dp.toPx())
        if (staged) {
            val rowHeight = bottom / STAGE_ROWS.size
            rowLayouts.forEachIndexed { row, layout ->
                val middle = rowHeight * (row + 0.5f)
                drawText(layout, topLeft = Offset(0f, middle - layout.size.height / 2f))
                drawLine(track, Offset(gutter, middle), Offset(size.width, middle), strokeWidth = 1.dp.toPx())
            }
            for ((stage, start, end) in day.stages) {
                val row = STAGE_ROWS.indexOf(stage)
                drawRoundRect(
                    colors.getValue(stage),
                    topLeft = Offset(x(start), rowHeight * row + 2.dp.toPx()),
                    size = Size((x(end) - x(start)).coerceAtLeast(1f), rowHeight - 4.dp.toPx()),
                    cornerRadius = radius,
                )
            }
        } else {
            drawRoundRect(track, topLeft = Offset(gutter, 0f), size = Size(width, bottom), cornerRadius = radius)
            for (part in day.parts) {
                drawRoundRect(
                    asleep,
                    topLeft = Offset(x(part.start), 0f),
                    size = Size((x(part.end) - x(part.start)).coerceAtLeast(2f), bottom),
                    cornerRadius = radius,
                )
            }
        }
        ticks.forEachIndexed { index, tick ->
            val layout = measurer.measure(tickLabels[index], labelStyle)
            val left = (x(tick) - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
            drawText(layout, topLeft = Offset(left, bottom + 2.dp.toPx()))
        }
    }
}

/** Readings over time: a line where they are close together, dots where they are apart; the lowest and highest marked. */
@Composable
private fun TimeChart(
    points: List<Pair<Instant, Double>>,
    from: Instant,
    to: Instant,
    color: Color,
    joinSeconds: Long,
    label: @Composable (Double) -> String,
) {
    if (points.isEmpty()) return
    val zone = ZoneId.systemDefault()
    val low = points.minOf { it.second }
    val high = points.maxOf { it.second }
    val pad = ((high - low) * CHART_PAD).coerceAtLeast(CHART_MIN_PAD)
    val floor = low - pad
    val range = high + pad - floor
    val marks = listOf(low, high).distinct()
    val markLabels = marks.map { label(it) }
    val ticks = remember(from, to) { HealthAxis.ticks(from, to, zone) }
    val tickLabels = ticks.map { CLOCK_FORMAT.format(it.atZone(zone)) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(TIME_CHART_HEIGHT)) {
        val gutter = TIME_GUTTER.toPx()
        val bottom = size.height - AXIS_LABEL_SPACE.toPx()
        val width = size.width - gutter
        val span = (to.epochSecond - from.epochSecond).coerceAtLeast(1L).toFloat()
        fun x(time: Instant) = gutter + width * ((time.epochSecond - from.epochSecond) / span).coerceIn(0f, 1f)
        fun y(value: Double) = bottom * (1f - ((value - floor) / range).toFloat())
        marks.forEachIndexed { index, mark ->
            val at = y(mark)
            drawLine(grid, Offset(gutter, at), Offset(size.width, at), strokeWidth = 1.dp.toPx())
            val layout = measurer.measure(markLabels[index], labelStyle)
            drawText(layout, topLeft = Offset(0f, (at - layout.size.height / 2f).coerceIn(0f, (bottom - layout.size.height).coerceAtLeast(0f))))
        }
        var previous: Pair<Offset, Long>? = null
        for ((time, value) in points) {
            val point = Offset(x(time), y(value))
            val last = previous
            if (last != null && time.epochSecond - last.second <= joinSeconds) {
                drawLine(color, last.first, point, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            } else {
                drawCircle(color, 2.dp.toPx(), point)
            }
            previous = point to time.epochSecond
        }
        ticks.forEachIndexed { index, tick ->
            val layout = measurer.measure(tickLabels[index], labelStyle)
            val left = (x(tick) - layout.size.width / 2f).coerceIn(gutter, (size.width - layout.size.width).coerceAtLeast(gutter))
            drawText(layout, topLeft = Offset(left, bottom + 2.dp.toPx()))
        }
    }
}

@Composable
private fun HeartDayDetail(date: LocalDate, state: MainUiState, load: suspend (LocalDate) -> HeartDay?) {
    val loaded = rememberLoaded(date, state.healthRefresh) { load(date) ?: HeartDay(emptyList(), null) }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        HeartDayBody(loaded?.value, state.settings.age, date)
    }
}

@Composable
private fun readingText(oxygen: Boolean, value: Double): String =
    if (oxygen) stringResource(R.string.health_percent, value.roundToLong().toString()) else value.roundToLong().toString()

@Composable
private fun ReadingsDayDetail(oxygen: Boolean, date: LocalDate, refresh: Int, load: suspend (LocalDate) -> DayReadings) {
    val loaded = rememberLoaded(date, refresh) { load(date) }
    if (loaded == null) {
        DetailText(stringResource(R.string.health_loading))
        return
    }
    val points = if (oxygen) loaded.value.oxygen else loaded.value.stress.map { it.first to it.second.toDouble() }
    if (points.isEmpty()) {
        DetailText(stringResource(R.string.detail_no_data))
        return
    }
    val zone = ZoneId.systemDefault()
    val average = points.map { it.second }.average()
    val lowest = points.minBy { it.second }
    val highest = points.maxBy { it.second }
    MetricHeadline(
        if (oxygen) {
            stringResource(R.string.health_percent, formatDecimal(average))
        } else {
            average.roundToLong().toString() + " · " + stringResource(stressLevel(average.roundToLong()))
        },
        stringResource(R.string.detail_readings, points.size),
    )
    RecordRow(stringResource(R.string.heart_lowest), readingText(oxygen, lowest.second), CLOCK_FORMAT.format(lowest.first.atZone(zone)))
    RecordRow(stringResource(R.string.heart_highest), readingText(oxygen, highest.second), CLOCK_FORMAT.format(highest.first.atZone(zone)))
    TimeChart(
        points,
        date.atStartOfDay(zone).toInstant(),
        date.plusDays(1).atStartOfDay(zone).toInstant(),
        if (oxygen) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary,
        READING_JOIN_SECONDS,
    ) { readingText(oxygen, it) }
    if (oxygen) {
        DetailText(stringResource(R.string.detail_oxygen_hint))
    } else {
        StressLevels(points.map { it.second.roundToLong() })
        DetailText(stringResource(R.string.health_stress_hint))
    }
}

@Composable
private fun StressLevels(values: List<Long>) {
    val counts = values.groupingBy { stressLevel(it) }.eachCount()
    val total = values.size.coerceAtLeast(1)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.detail_stress_levels), style = MaterialTheme.typography.labelLarge)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(LEVEL_BAR_HEIGHT).clip(RoundedCornerShape(6.dp))) {
            STRESS_LEVELS.forEach { (level, color) ->
                val count = counts[level] ?: 0
                if (count > 0) Box(modifier = Modifier.weight(count.toFloat() / total).fillMaxSize().background(color))
            }
        }
        STRESS_LEVELS.forEach { (level, color) ->
            val count = counts[level] ?: 0
            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(stringResource(level), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                Text(
                    stringResource(R.string.health_percent, (count * 100 / total).toString()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (count > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecoveryDayDetail(date: LocalDate, period: BandPeriod?) {
    if (period == null) {
        DetailText(stringResource(R.string.health_loading))
        return
    }
    val nights = period.nights.mapValues { it.value.minutes }
    val resting = restingMap(period)
    val window = (1L..Recovery.WINDOW_DAYS).map { date.minusDays(it) }
    val sleep = nights[date]
    val sleepAverage = HealthMath.average(window.mapNotNull { nights[it] })
    val rest = resting[date]
    val restAverage = HealthMath.average(window.mapNotNull { resting[it] })
    val score = Recovery.score(sleep, sleepAverage, rest, restAverage)
    if (score == null) {
        DetailText(stringResource(R.string.detail_no_data))
        return
    }
    MetricHeadline(
        stringResource(R.string.recovery_value, score),
        stringResource(
            when {
                score >= GOOD_RECOVERY -> R.string.recovery_good
                score >= MID_RECOVERY -> R.string.recovery_mid
                else -> R.string.recovery_low
            },
        ),
    )
    LinearProgressIndicator(
        progress = { score / 100f },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
    )
    RecordRow(
        stringResource(R.string.band_sleep),
        sleep?.let { sleepText(it) } ?: DASH,
        sleepAverage?.let { stringResource(R.string.detail_week_average, sleepText(it)) },
    )
    RecordRow(
        stringResource(R.string.health_resting_chart),
        rest?.let { stringResource(R.string.health_bpm, it) } ?: DASH,
        restAverage?.let { stringResource(R.string.detail_week_average, stringResource(R.string.health_bpm, it)) },
    )
    DetailText(stringResource(R.string.detail_recovery_text))
}

@Composable
private fun ExerciseDayDetail(date: LocalDate, period: BandPeriod?) {
    if (period == null) {
        DetailText(stringResource(R.string.health_loading))
        return
    }
    val zone = ZoneId.systemDefault()
    val exercises = period.exercises.filter { it.start.atZone(zone).toLocalDate() == date }.sortedBy { it.start }
    if (exercises.isEmpty()) {
        DetailText(stringResource(R.string.detail_no_data))
        return
    }
    MetricHeadline(
        stringResource(R.string.duration_minutes, exercises.sumOf { it.minutes }),
        stringResource(R.string.health_exercise_count, exercises.size),
    )
    exercises.forEach { exercise ->
        val start = exercise.start.atZone(zone)
        RecordRow(
            exercise.title?.takeIf { it.isNotBlank() } ?: stringResource(exerciseLabel(exercise.kind)),
            stringResource(R.string.duration_minutes, exercise.minutes),
            stringResource(R.string.health_time_range, CLOCK_FORMAT.format(start), CLOCK_FORMAT.format(start.plusMinutes(exercise.minutes))),
        )
    }
}

@Composable
private fun ActivityDayDetail(date: LocalDate, state: MainUiState, period: BandPeriod?, load: suspend (LocalDate) -> BandDay?) {
    val loaded = rememberLoaded(date, state.healthRefresh) { load(date) }
    if (loaded == null) {
        DetailText(stringResource(R.string.health_loading))
        return
    }
    val band = loaded.value
    val kcal = period?.activeKcal?.get(date)?.takeIf { it > 0L } ?: band?.activeKcal
    MetricHeadline(
        kcal?.let { stringResource(R.string.band_kcal_value, format(it)) } ?: DASH,
        stringResource(R.string.band_active_kcal),
    )
    RecordRow(stringResource(R.string.detail_steps), format(state.history[date] ?: 0L), null)
    band?.distanceKm?.let { km ->
        RecordRow(stringResource(R.string.health_distance), stringResource(R.string.health_km, formatDecimal(km)), null)
    }
    val exercises = band?.exercises.orEmpty()
    if (exercises.isNotEmpty()) {
        RecordRow(
            stringResource(R.string.health_exercise_title),
            stringResource(R.string.duration_minutes, exercises.sumOf { it.minutes }),
            stringResource(R.string.health_exercise_count, exercises.size),
        )
    }
}

@Composable
private fun WaterDayDetail(date: LocalDate, state: MainUiState) {
    val ml = state.water[date] ?: 0
    if (ml <= 0) {
        DetailText(stringResource(R.string.detail_no_data))
        return
    }
    val goal = state.settings.waterGoalMl.coerceAtLeast(1)
    val glass = state.settings.glassMl.coerceAtLeast(1)
    MetricHeadline(
        stringResource(R.string.water_ml, format(ml.toLong())),
        stringResource(R.string.water_value, format(ml.toLong()), format(goal.toLong())),
    )
    LinearProgressIndicator(
        progress = { (ml.toFloat() / goal).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
    )
    RecordRow(
        stringResource(R.string.water_goal),
        stringResource(R.string.percent_of_goal, percent(ml.toLong(), goal.toLong())),
        if (ml >= goal) stringResource(R.string.detail_water_reached) else null,
    )
    RecordRow(stringResource(R.string.detail_glass_count), (ml / glass).toString(), stringResource(R.string.water_ml, format(glass.toLong())))
}

@Composable
private fun WeightDayDetail(date: LocalDate, state: MainUiState, actions: MainActions) {
    var editing by rememberSaveable(date.toString()) { mutableStateOf(false) }
    val weights = state.weights
    val entry = weights.firstOrNull { it.date == date }
    val previous = weights.lastOrNull { it.date < date }
    if (entry == null) {
        DetailText(stringResource(R.string.detail_no_data))
    } else {
        MetricHeadline(stringResource(R.string.weight_kg, formatDecimal(entry.kg)), null)
        previous?.let {
            RecordRow(
                stringResource(R.string.detail_weight_previous),
                stringResource(R.string.weight_kg, signedKg(entry.kg - it.kg)),
                FULL_DATE_FORMAT.format(it.date),
            )
        }
        HealthMath.bmi(entry.kg, state.settings.heightCm)?.let { bmi ->
            RecordRow(stringResource(R.string.weight_bmi), formatDecimal(bmi), stringResource(bmiLabel(HealthMath.bmiCategory(bmi))))
        }
    }
    if (!date.isAfter(state.today)) {
        Button(onClick = { editing = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(stringResource(if (entry == null) R.string.weight_add else R.string.detail_weight_edit))
        }
    }
    if (editing) {
        WeightDialog(
            date = date,
            current = entry?.kg ?: previous?.kg ?: state.settings.weightKg.toDouble(),
            canDelete = entry != null,
            onSave = { actions.onAddWeight(date, it) },
            onDelete = { actions.onDeleteWeight(date) },
            onDismiss = { editing = false },
        )
    }
}

private val STAGE_ROWS = listOf(SleepStage.Awake, SleepStage.Rem, SleepStage.Light, SleepStage.Deep)

private val STRESS_LEVELS = listOf(
    R.string.stress_relaxed to Color(0xFF64B5F6),
    R.string.stress_normal to Color(0xFF81C784),
    R.string.stress_medium to Color(0xFFFFD54F),
    R.string.stress_high to Color(0xFFE57373),
)

private const val DASH = "-"

private const val MAX_MONTHS_BACK = 24L

private const val GOOD_RECOVERY = 70.0

private const val MID_RECOVERY = 40

private const val EXERCISE_GOAL_MINUTES = 30.0

private const val RELATIVE_FLOOR = 0.2f

private const val RELATIVE_SPAN = 0.6f

private const val CELL_ALPHA_FLOOR = 0.12f

private const val CELL_ALPHA_SPAN = 0.45f

private const val CHART_PAD = 0.15

private const val CHART_MIN_PAD = 2.0

private const val HEART_JOIN_SECONDS = 15L * 60L

private const val READING_JOIN_SECONDS = 30L * 60L

private val TIMELINE_HEIGHT = 44.dp

private val STAGED_TIMELINE_HEIGHT = 132.dp

private val TIME_CHART_HEIGHT = 140.dp

private val TIME_GUTTER = 32.dp

private val AXIS_LABEL_SPACE = 18.dp

private val LEVEL_BAR_HEIGHT = 14.dp
