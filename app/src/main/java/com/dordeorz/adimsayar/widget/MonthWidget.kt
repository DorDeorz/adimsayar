package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.ui.MONTH_FORMAT
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle as DateTextStyle

class MonthWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StepRepository.get(context)
        val today = LocalDate.now()
        val initial = repository.loadHistory()
        provideContent {
            val flow = remember { repository.observeHistory() }
            val history by flow.collectAsState(initial)
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            WidgetTheme(settings.dynamicColor) {
                MonthContent(localized, history, today, settings.dailyGoal, settings.weekStart)
            }
        }
    }
}

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val PADDING = 12.dp
private val TITLE_HEIGHT = 22.dp
private val HEADER_HEIGHT = 16.dp
private val CELL_GAP = 2.dp
private val MIN_NUMBER_CELL = 18.dp
private val MIN_HEADER_CELL = 22.dp
private const val DAYS_IN_WEEK = 7

@Composable
private fun MonthContent(context: Context, history: Map<LocalDate, Long>, today: LocalDate, goal: Long, weekStart: DayOfWeek) {
    val size = LocalSize.current
    val month = YearMonth.from(today)
    val leading = (month.atDay(1).dayOfWeek.value - weekStart.value + DAYS_IN_WEEK) % DAYS_IN_WEEK
    val rows = (leading + month.lengthOfMonth() + DAYS_IN_WEEK - 1) / DAYS_IN_WEEK
    val width = (size.width - PADDING * 2) / DAYS_IN_WEEK
    val withHeader = (size.height - PADDING * 2 - TITLE_HEIGHT - HEADER_HEIGHT) / rows >= MIN_HEADER_CELL
    val headerHeight = if (withHeader) HEADER_HEIGHT else 0.dp
    val height = (size.height - PADDING * 2 - TITLE_HEIGHT - headerHeight) / rows
    val cell = minOf(width, height)
    val goalDays = (1..today.dayOfMonth).count { (history[month.atDay(it)] ?: 0L) >= goal }
    Column(modifier = widgetSurface(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = MONTH_FORMAT.format(month).replaceFirstChar { it.titlecase(Locales.current) } + " · " +
                context.getString(R.string.month_goal_days, goalDays),
            maxLines = 1,
            modifier = GlanceModifier.height(TITLE_HEIGHT),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        if (withHeader) {
            Row {
                repeat(DAYS_IN_WEEK) { index ->
                    Text(
                        text = weekStart.plus(index.toLong()).getDisplayName(DateTextStyle.NARROW, Locales.current),
                        modifier = GlanceModifier.size(cell, HEADER_HEIGHT),
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center),
                    )
                }
            }
        }
        repeat(rows) { row ->
            Row {
                repeat(DAYS_IN_WEEK) { column ->
                    val day = row * DAYS_IN_WEEK + column - leading + 1
                    if (day < 1 || day > month.lengthOfMonth()) {
                        Box(modifier = GlanceModifier.size(cell)) {}
                    } else {
                        val date = month.atDay(day)
                        DayCell(day, history[date] ?: 0L, goal, cell, isFuture = date.isAfter(today), isToday = date == today)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: Int, steps: Long, goal: Long, cell: Dp, isFuture: Boolean, isToday: Boolean) {
    val (background, text) = when {
        isFuture || steps <= 0L -> GlanceTheme.colors.surfaceVariant to GlanceTheme.colors.onSurfaceVariant
        steps >= goal -> GlanceTheme.colors.primary to GlanceTheme.colors.onPrimary
        steps * 2 >= goal -> GlanceTheme.colors.primaryContainer to GlanceTheme.colors.onPrimaryContainer
        else -> GlanceTheme.colors.secondaryContainer to GlanceTheme.colors.onSecondaryContainer
    }
    Box(modifier = GlanceModifier.size(cell).padding(CELL_GAP), contentAlignment = Alignment.Center) {
        Box(
            modifier = GlanceModifier.fillMaxWidth().height(cell - CELL_GAP * 2).cornerRadius(cell / 4).background(background),
            contentAlignment = Alignment.Center,
        ) {
            if (cell >= MIN_NUMBER_CELL) {
                Text(
                    text = day.toString(),
                    maxLines = 1,
                    style = TextStyle(
                        color = text,
                        fontSize = (cell.value * 0.38f).coerceAtMost(13f).sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        }
    }
}
