package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
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
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.DaySteps
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import java.time.LocalDate

class WeekWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StepRepository.get(context)
        val today = LocalDate.now()
        val initial = repository.loadWeek(today)
        provideContent {
            val flow = remember { repository.observeWeek(today) }
            val week by flow.collectAsState(initial)
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            WidgetTheme(settings.dynamicColor) {
                WeekContent(localized, week, settings.dailyGoal)
            }
        }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val PADDING = 12.dp
private val TITLE_HEIGHT = 20.dp
private val VALUE_HEIGHT = 16.dp
private val LABEL_HEIGHT = 18.dp
private val MAX_BAR_WIDTH = 18.dp
private val MIN_BAR = 3.dp
private val VALUES_MIN_HEIGHT = 120.dp
private val VALUES_MIN_COLUMN = 30.dp

@Composable
private fun WeekContent(context: Context, week: List<DaySteps>, goal: Long) {
    val size = LocalSize.current
    val column = (size.width - PADDING * 2) / week.size.coerceAtLeast(1)
    val showValues = size.height >= VALUES_MIN_HEIGHT && column >= VALUES_MIN_COLUMN
    val valueHeight = if (showValues) VALUE_HEIGHT else 0.dp
    val maxBar = (size.height - PADDING * 2 - TITLE_HEIGHT - valueHeight - LABEL_HEIGHT).coerceAtLeast(MIN_BAR)
    val barWidth = (column * 0.55f).coerceIn(MIN_BAR, MAX_BAR_WIDTH)
    val maxSteps = maxOf(week.maxOfOrNull { it.steps } ?: 0L, goal).coerceAtLeast(1L)
    Column(modifier = widgetSurface()) {
        Text(
            text = context.getString(R.string.last_7_days),
            modifier = GlanceModifier.height(TITLE_HEIGHT),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { index, day ->
                DayColumn(
                    day = day,
                    barHeight = barHeight(day.steps, maxSteps, maxBar),
                    barWidth = barWidth,
                    showValue = showValues,
                    goalMet = day.steps >= goal,
                    isToday = index == week.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun RowScope.DayColumn(day: DaySteps, barHeight: Dp, barWidth: Dp, showValue: Boolean, goalMet: Boolean, isToday: Boolean) {
    Column(
        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (showValue) {
            Text(
                text = formatCompact(day.steps),
                maxLines = 1,
                modifier = GlanceModifier.height(VALUE_HEIGHT),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp, textAlign = TextAlign.Center),
            )
        }
        Box(
            modifier = GlanceModifier
                .width(barWidth)
                .height(barHeight)
                .cornerRadius(barWidth / 2)
                .background(if (goalMet || isToday) GlanceTheme.colors.primary else GlanceTheme.colors.secondaryContainer),
        ) {}
        Text(
            text = shortDayName(day.date),
            maxLines = 1,
            modifier = GlanceModifier.height(LABEL_HEIGHT),
            style = TextStyle(
                color = if (isToday) GlanceTheme.colors.onSurface else GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun barHeight(steps: Long, maxSteps: Long, maxBar: Dp): Dp =
    if (steps <= 0L) MIN_BAR else (maxBar * (steps.toFloat() / maxSteps)).coerceAtLeast(MIN_BAR)
