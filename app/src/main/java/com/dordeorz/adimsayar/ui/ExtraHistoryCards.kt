package com.dordeorz.adimsayar.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.AchievementMath
import com.dordeorz.adimsayar.data.DayHours
import com.dordeorz.adimsayar.data.Distance
import com.dordeorz.adimsayar.data.DistanceUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Month
import java.time.format.TextStyle
import kotlin.math.roundToInt

private const val HEAT_WEEKS = 8L
private const val EARTH_KM = 40_075.0

/** Month so far and the last 7 days, each against the same stretch before it. */
@Composable
internal fun TrendCard(state: MainUiState) {
    val today = state.today
    val history = state.history
    val month = remember(history, today) {
        val lastMonth = today.minusMonths(1)
        val days = today.dayOfMonth
        val now = sumRange(history, today.withDayOfMonth(1), today)
        val before = sumRange(history, lastMonth.withDayOfMonth(1), lastMonth.withDayOfMonth(minOf(days, lastMonth.lengthOfMonth())))
        now to before
    }
    val week = remember(history, today) {
        sumRange(history, today.minusDays(6), today) to sumRange(history, today.minusDays(13), today.minusDays(7))
    }
    Card {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_trend),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(stringResource(R.string.trend_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
            }
            RecordRow(stringResource(R.string.trend_month, today.dayOfMonth), format(month.first), trendText(month.first, month.second))
            RecordRow(stringResource(R.string.trend_week), format(week.first), trendText(week.first, week.second))
        }
    }
}

private fun sumRange(history: Map<LocalDate, Long>, from: LocalDate, to: LocalDate): Long =
    history.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.values.sum()

@Composable
private fun trendText(now: Long, before: Long): String {
    if (before <= 0L) return stringResource(R.string.trend_no_data)
    val change = ((now - before) * 100.0 / before).roundToInt()
    return when {
        change > 0 -> stringResource(R.string.trend_up, change)
        change < 0 -> stringResource(R.string.trend_down, -change)
        else -> stringResource(R.string.trend_same)
    }
}

/** Average steps per weekday and hour over the last 8 weeks, as a 7 × 24 grid. */
@Composable
internal fun ActiveHoursCard(state: MainUiState) {
    val context = LocalContext.current
    val grid by produceState<Array<DoubleArray>?>(null, state.today, state.lastReadingWallMs != null) {
        value = withContext(Dispatchers.IO) { heatGrid(context, state.today) }
    }
    val days = remember(state.settings.weekStart) { (0L until 7L).map { state.settings.weekStart.plus(it) } }
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.active_hours_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.active_hours_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val values = grid
            val max = values?.maxOfOrNull { row -> row.maxOrNull() ?: 0.0 } ?: 0.0
            if (values == null || max <= 0.0) {
                Text(
                    stringResource(R.string.active_hours_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val cell = MaterialTheme.colorScheme.primary
            val empty = MaterialTheme.colorScheme.surfaceContainerHighest
            days.forEach { day ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        day.getDisplayName(TextStyle.SHORT, appLocale),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(36.dp),
                    )
                    val row = values[day.value - 1]
                    Canvas(modifier = Modifier.weight(1f).height(14.dp)) {
                        val gap = 1.5.dp.toPx()
                        val width = (size.width - gap * (DayHours.HOURS - 1)) / DayHours.HOURS
                        for (hour in 0 until DayHours.HOURS) {
                            val level = (row[hour] / max).toFloat()
                            drawRoundRect(
                                color = if (level < 0.02f) empty else cell.copy(alpha = 0.15f + 0.85f * level),
                                topLeft = Offset(hour * (width + gap), 0f),
                                size = Size(width, size.height),
                                cornerRadius = CornerRadius(2.dp.toPx()),
                            )
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(start = 36.dp)) {
                listOf(0, 6, 12, 18).forEach { hour ->
                    Text(
                        CLOCK_FORMAT.format(LocalTime.of(hour, 0)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            var peakDay = 0
            var peakHour = 0
            values.forEachIndexed { d, row -> row.forEachIndexed { h, v -> if (v > values[peakDay][peakHour]) { peakDay = d; peakHour = h } } }
            Text(
                stringResource(
                    R.string.active_hours_peak,
                    DayOfWeek.of(peakDay + 1).getDisplayName(TextStyle.FULL_STANDALONE, appLocale),
                    CLOCK_FORMAT.format(LocalTime.of(peakHour, 0)),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Rows are Monday..Sunday, columns the hours. */
private suspend fun heatGrid(context: Context, today: LocalDate): Array<DoubleArray> {
    val from = today.minusWeeks(HEAT_WEEKS)
    val hours = DayHours.since(context, from)
    val sums = Array(7) { DoubleArray(DayHours.HOURS) }
    val dates = HashMap<DayOfWeek, MutableSet<LocalDate>>()
    for ((time, steps) in hours) {
        val date = time.toLocalDate()
        if (date.isBefore(from) || date.isAfter(today)) continue
        sums[date.dayOfWeek.value - 1][time.hour] += steps.toDouble()
        dates.getOrPut(date.dayOfWeek) { HashSet() } += date
    }
    for (day in DayOfWeek.entries) {
        val count = dates[day]?.size ?: continue
        for (hour in 0 until DayHours.HOURS) sums[day.value - 1][hour] /= count
    }
    return sums
}

/** The year whose story is shown in December and January; null the rest of the year. */
internal fun storyYear(today: LocalDate): Int? = when (today.month) {
    Month.DECEMBER -> today.year
    Month.JANUARY -> today.year - 1
    else -> null
}

@Composable
internal fun StoryBanner(year: Int, onOpen: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.story_banner_title, year),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.story_banner_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Button(onClick = onOpen) { Text(stringResource(R.string.story_open)) }
        }
    }
}

private class StoryPage(val title: String, val value: String, val text: String?)

/** A swipeable year-end story, one number per page, with a share button at the end. */
@Composable
internal fun YearStoryDialog(state: MainUiState, year: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val summary = remember(state.history, state.achievements, year, state.today) {
        AchievementMath.year(state.history, year, state.today, state.settings.dailyGoal, state.achievements.medals)
    }
    val km = Distance.km(summary.total, state.settings.heightCm)
    val unit = stringResource(if (state.settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
    val pages = listOfNotNull(
        StoryPage(stringResource(R.string.story_total_title, year), format(summary.total), stringResource(R.string.story_total_text, format(summary.dailyAverage))),
        StoryPage(
            stringResource(R.string.story_distance_title),
            "${formatDecimal(Distance.inUnit(km, state.settings.distanceUnit))} $unit",
            stringResource(R.string.story_distance_text, ((km / EARTH_KM) * 100).roundToInt()),
        ),
        summary.bestDay?.let { StoryPage(stringResource(R.string.record_best_day), format(it.steps), FULL_DATE_FORMAT.format(it.date)) },
        summary.bestMonth?.let {
            StoryPage(
                stringResource(R.string.record_best_month),
                format(it.steps),
                MONTH_FORMAT.format(it.month).replaceFirstChar { c -> c.titlecase(appLocale) },
            )
        },
        StoryPage(
            stringResource(R.string.record_goal_days),
            summary.goalDays.toString(),
            stringResource(R.string.active_days, summary.activeDays),
        ),
        StoryPage(stringResource(R.string.year_medals), summary.medals.toString(), stringResource(R.string.story_medals_text)),
        StoryPage(stringResource(R.string.story_end_title), "🐦", stringResource(R.string.story_end_text)),
    )
    val pager = rememberPagerState { pages.size }
    val shareText = stringResource(R.string.story_share, year, format(summary.total), formatDecimal(km), summary.goalDays)
    val shareTitle = stringResource(R.string.share_button)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
            Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(300.dp)) { index ->
                    val page = pages[index]
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(page.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(
                            page.value,
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                        page.text?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    repeat(pages.size) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { shareStory(context, shareText, shareTitle) }) { Text(shareTitle) }
                }
            }
        }
    }
}

private fun shareStory(context: Context, text: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // Nothing can share text; the story stays on screen.
    }
}
