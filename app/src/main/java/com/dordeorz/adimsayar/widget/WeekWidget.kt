package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
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
            WeekContent(localized, week)
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
private val BAR_WIDTH = 14.dp
private val MIN_BAR = 3.dp

@Composable
private fun WeekContent(context: Context, week: List<DaySteps>) {
    val size = LocalSize.current
    val maxBar = (size.height - PADDING * 2 - TITLE_HEIGHT - VALUE_HEIGHT - LABEL_HEIGHT).coerceAtLeast(MIN_BAR)
    val maxSteps = week.maxOfOrNull { it.steps }?.coerceAtLeast(1L) ?: 1L
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .clickable(actionStartActivity<MainActivity>())
            .padding(PADDING),
    ) {
        Text(
            text = context.getString(R.string.last_7_days),
            modifier = GlanceModifier.height(TITLE_HEIGHT),
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { index, day ->
                DayColumn(day, barHeight(day.steps, maxSteps, maxBar), isToday = index == week.lastIndex)
            }
        }
    }
}

@Composable
private fun RowScope.DayColumn(day: DaySteps, barHeight: Dp, isToday: Boolean) {
    Column(
        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = formatCompact(day.steps),
            maxLines = 1,
            modifier = GlanceModifier.height(VALUE_HEIGHT),
            style = TextStyle(color = TextSecondary, fontSize = 10.sp, textAlign = TextAlign.Center),
        )
        Box(
            modifier = GlanceModifier
                .width(BAR_WIDTH)
                .height(barHeight)
                .background(if (isToday) Accent else AccentMuted),
        ) {}
        Text(
            text = shortDayName(day.date),
            maxLines = 1,
            modifier = GlanceModifier.height(LABEL_HEIGHT),
            style = TextStyle(
                color = if (isToday) TextPrimary else TextSecondary,
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

private fun barHeight(steps: Long, maxSteps: Long, maxBar: Dp): Dp =
    if (steps <= 0L) MIN_BAR else (maxBar * (steps.toFloat() / maxSteps)).coerceAtLeast(MIN_BAR)
