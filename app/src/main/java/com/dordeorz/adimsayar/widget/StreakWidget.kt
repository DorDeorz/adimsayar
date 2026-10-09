package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.AchievementMath
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.weekStartOf
import java.time.LocalDate

class StreakWidget : GlanceAppWidget() {

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
            val streak = remember(history, settings) {
                AchievementMath.compute(history, today, settings.dailyGoal, settings.weeklyGoal, settings.weekStart).records.currentStreak
            }
            val weekStart = weekStartOf(today, settings.weekStart)
            val weekSteps = history.filterKeys { !it.isBefore(weekStart) && !it.isAfter(today) }.values.sum()
            WidgetTheme(settings.dynamicColor) {
                StreakContent(localized, streak, weekSteps, settings)
            }
        }
    }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val COMPACT_HEIGHT = 110.dp
private val COMPACT_WIDTH = 150.dp
private const val WIDE_RATIO = 1.8f

@Composable
private fun StreakContent(context: Context, streak: Int, weekSteps: Long, settings: AppSettings) {
    val size = LocalSize.current
    Box(modifier = widgetSurface(), contentAlignment = Alignment.Center) {
        when {
            size.height < COMPACT_HEIGHT || size.width < COMPACT_WIDTH -> StreakBlock(context, streak)
            size.width.value >= size.height.value * WIDE_RATIO -> Row(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) { StreakBlock(context, streak) }
                Spacer(GlanceModifier.width(12.dp))
                Box(modifier = GlanceModifier.defaultWeight()) { WeekBlock(context, weekSteps, settings.weeklyGoal) }
            }
            else -> Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                    StreakBlock(context, streak)
                }
                WeekBlock(context, weekSteps, settings.weeklyGoal)
            }
        }
    }
}

@Composable
private fun StreakBlock(context: Context, streak: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_fire),
                contentDescription = null,
                modifier = GlanceModifier.size(24.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = streak.toString(),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
        }
        Text(
            text = context.getString(R.string.streak) + " · " + context.getString(R.string.days_count, streak),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
        )
    }
}

@Composable
private fun WeekBlock(context: Context, weekSteps: Long, weeklyGoal: Long) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = context.getString(R.string.this_week),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Text(
            text = formatSteps(weekSteps) + " / " + formatSteps(weeklyGoal),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = (weekSteps.toFloat() / weeklyGoal).coerceIn(0f, 1f),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
    }
}
