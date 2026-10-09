package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.AchievementMath
import com.dordeorz.adimsayar.data.DayDetail
import com.dordeorz.adimsayar.data.Records
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
fun HistoryScreen(state: MainUiState, loadDayDetail: suspend (LocalDate) -> DayDetail?) {
    val currentMonth = YearMonth.from(state.today)
    val firstMonth = state.history.keys.minOrNull()?.let(YearMonth::from)?.coerceAtMost(currentMonth) ?: currentMonth
    var monthText by rememberSaveable { mutableStateOf(currentMonth.toString()) }
    var selectedText by rememberSaveable { mutableStateOf(state.today.toString()) }
    val month = YearMonth.parse(monthText).coerceIn(firstMonth, currentMonth)
    val selected = LocalDate.parse(selectedText)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MonthCard(
                month = month,
                state = state,
                selected = selected,
                canGoBack = month > firstMonth,
                canGoForward = month < currentMonth,
                onMonthChange = {
                    monthText = it.toString()
                    selectedText = (if (it == currentMonth) state.today else it.atEndOfMonth()).toString()
                },
                onSelect = { selectedText = it.toString() },
            )
        }
        item { SelectedDayCard(selected, state.history[selected] ?: 0L, state, loadDayDetail) }
        item { RecordsCard(state.achievements.records, state.today) }
        item { YearCard(state) }
    }
}

@Composable
private fun YearCard(state: MainUiState) {
    val currentYear = state.today.year
    val firstYear = state.history.keys.minOrNull()?.year?.coerceAtMost(currentYear) ?: currentYear
    var yearValue by rememberSaveable { mutableIntStateOf(currentYear) }
    val year = yearValue.coerceIn(firstYear, currentYear)
    val summary = remember(state.history, state.achievements, state.settings.dailyGoal, year, state.today) {
        AchievementMath.year(state.history, year, state.today, state.settings.dailyGoal, state.achievements.medals)
    }
    Card {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(modifier = Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { yearValue = year - 1 }, enabled = year > firstYear) {
                    Icon(painterResource(R.drawable.ic_chevron_left), contentDescription = stringResource(R.string.previous_year))
                }
                Text(
                    stringResource(R.string.year_summary, year),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { yearValue = year + 1 }, enabled = year < currentYear) {
                    Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = stringResource(R.string.next_year))
                }
            }
            RecordRow(stringResource(R.string.total), format(summary.total), null)
            RecordRow(stringResource(R.string.daily_average), format(summary.dailyAverage), null)
            RecordRow(
                stringResource(R.string.record_goal_days),
                summary.goalDays.toString(),
                stringResource(R.string.active_days, summary.activeDays),
            )
            RecordRow(
                stringResource(R.string.record_best_month),
                summary.bestMonth?.let { format(it.steps) } ?: "-",
                summary.bestMonth?.let { MONTH_FORMAT.format(it.month).replaceFirstChar { c -> c.titlecase(appLocale) } },
            )
            RecordRow(
                stringResource(R.string.record_best_day),
                summary.bestDay?.let { format(it.steps) } ?: "-",
                summary.bestDay?.let { FULL_DATE_FORMAT.format(it.date) },
            )
            RecordRow(stringResource(R.string.year_medals), summary.medals.toString(), null)
        }
    }
}

@Composable
private fun MonthCard(
    month: YearMonth,
    state: MainUiState,
    selected: LocalDate,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val goal = state.settings.dailyGoal
    val lastDay = if (month == YearMonth.from(state.today)) state.today else month.atEndOfMonth()
    val days = (1..lastDay.dayOfMonth).map { month.atDay(it) }
    val monthTotal = days.sumOf { state.history[it] ?: 0L }
    val goalDays = days.count { (state.history[it] ?: 0L) >= goal }
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
                MonthStat(stringResource(R.string.total), format(monthTotal), Modifier.weight(1f))
                MonthStat(stringResource(R.string.daily_average), format(monthTotal / days.size), Modifier.weight(1f))
                MonthStat(stringResource(R.string.goal_days), goalDays.toString(), Modifier.weight(1f))
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
                                DayCell(
                                    date = date,
                                    steps = state.history[date] ?: 0L,
                                    goal = goal,
                                    future = date.isAfter(state.today),
                                    selected = date == selected,
                                    onClick = { onSelect(date) },
                                )
                            }
                        }
                    }
                    repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MonthStat(label: String, value: String, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayCell(date: LocalDate, steps: Long, goal: Long, future: Boolean, selected: Boolean, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val reached = steps >= goal
    val background = when {
        future || steps == 0L -> Color.Transparent
        reached -> primary
        steps * 2 >= goal -> primary.copy(alpha = 0.4f)
        else -> primary.copy(alpha = 0.15f)
    }
    val textColor = when {
        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        reached -> MaterialTheme.colorScheme.onPrimary
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
        if (!future && steps > 0L) {
            Text(formatCompact(steps), style = MaterialTheme.typography.labelSmall, color = textColor, maxLines = 1)
        }
    }
}

@Composable
private fun SelectedDayCard(date: LocalDate, steps: Long, state: MainUiState, loadDayDetail: suspend (LocalDate) -> DayDetail?) {
    val goal = state.settings.dailyGoal
    val detail by produceState<DayDetail?>(null, date, steps, state.xiaomiEnabled) {
        value = if (state.xiaomiEnabled) withContext(Dispatchers.IO) { loadDayDetail(date) } else null
    }
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(LONG_DAY_FORMAT.format(date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.steps_value, format(steps)), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        distanceAndCalories(steps, state.settings),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.percent_of_goal, percent(steps, goal)),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (steps >= goal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            detail?.let { day ->
                if (day.walking > 0L || day.running > 0L) WalkRunRow(day)
                if (day.hours.any { it > 0L }) HourlyChart(day.hours)
            }
        }
    }
}

@Composable
private fun WalkRunRow(day: DayDetail) {
    val total = (day.walking + day.running).coerceAtLeast(1L)
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            if (day.walking > 0L) Box(modifier = Modifier.weight(day.walking.toFloat() / total).fillMaxHeight().background(primary))
            if (day.running > 0L) Box(modifier = Modifier.weight(day.running.toFloat() / total).fillMaxHeight().background(tertiary))
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(
                stringResource(R.string.walking_steps, format(day.walking)),
                style = MaterialTheme.typography.labelMedium,
                color = primary,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.running_steps, format(day.running)), style = MaterialTheme.typography.labelMedium, color = tertiary)
        }
    }
}

@Composable
private fun HourlyChart(hours: LongArray) {
    val max = hours.max().coerceAtLeast(1L)
    val primary = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.padding(top = 16.dp)) {
        Text(stringResource(R.string.hourly), style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth().height(HOURLY_HEIGHT).padding(top = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            hours.forEach { steps ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height((HOURLY_HEIGHT - 8.dp) * (steps.toFloat() / max))
                        .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        .background(primary),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf(0, 6, 12, 18).forEach { hour ->
                Text(
                    stringResource(R.string.hour_label, hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private val HOURLY_HEIGHT = 72.dp

private fun weekDays(first: DayOfWeek): List<DayOfWeek> = (0L until 7L).map { first.plus(it) }

@Composable
private fun RecordsCard(records: Records, today: LocalDate) {
    Card {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.records),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            RecordRow(
                stringResource(R.string.record_best_day),
                records.bestDay?.let { format(it.steps) } ?: "-",
                records.bestDay?.let { FULL_DATE_FORMAT.format(it.date) },
            )
            RecordRow(
                stringResource(R.string.record_best_week),
                records.bestWeek?.let { format(it.steps) } ?: "-",
                records.bestWeek?.let { stringResource(R.string.week_of, DAY_FORMAT.format(it.start)) },
            )
            RecordRow(
                stringResource(R.string.record_best_month),
                records.bestMonth?.let { format(it.steps) } ?: "-",
                records.bestMonth?.let { MONTH_FORMAT.format(it.month).replaceFirstChar { c -> c.titlecase(appLocale) } },
            )
            RecordRow(stringResource(R.string.record_longest_streak), stringResource(R.string.days_count, records.longestStreak), null)
            RecordRow(stringResource(R.string.record_goal_days), records.goalDays.toString(), null)
            RecordRow(
                stringResource(R.string.record_active_average),
                format(if (records.activeDays == 0) 0L else records.total / records.activeDays),
                stringResource(R.string.active_days, records.activeDays),
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            RecordRow(stringResource(R.string.total), format(records.total), stringResource(R.string.until_date, FULL_DATE_FORMAT.format(today)))
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String, hint: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}
