package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.width
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.Distance
import com.dordeorz.adimsayar.data.DistanceUnit
import com.dordeorz.adimsayar.data.HealthSnapshot
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.Water
import com.dordeorz.adimsayar.data.WearableHealth
import com.dordeorz.adimsayar.ui.Tab
import com.dordeorz.adimsayar.ui.format
import com.dordeorz.adimsayar.ui.formatDecimal
import com.dordeorz.adimsayar.ui.percent
import java.time.LocalDate

class SummaryWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StepRepository.get(context)
        val today = LocalDate.now()
        val initialWeek = repository.loadWeek(today)
        val initialWater = Water.today(context)
        val health = if (WearableHealth.isEnabled(context)) HealthCache.load(context) else null
        provideContent {
            val week by remember { repository.observeWeek(today) }.collectAsState(initialWeek)
            val water by remember { Water.observe(context) }.collectAsState(null)
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            val steps = week.lastOrNull()?.steps ?: 0L
            val ml = water?.let { it[today] ?: 0 } ?: initialWater
            WidgetTheme(settings.dynamicColor) {
                WidgetSurface(Tab.Today, settings.widgetOpacity) {
                    SummaryContent(localized, summaryRows(localized, steps, ml, health, settings))
                }
            }
        }
    }
}

class SummaryWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SummaryWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private class SummaryRow(val icon: Int, val value: String, val label: String)

private val PADDING = 12.dp
private val ROW_HEIGHT = 34.dp
private val TWO_COLUMNS = 240.dp

private fun summaryRows(context: Context, steps: Long, ml: Int, health: HealthSnapshot?, settings: AppSettings): List<SummaryRow> {
    val stepsRow = SummaryRow(
        R.drawable.ic_walk,
        format(steps),
        context.getString(R.string.percent_of_goal, percent(steps, settings.dailyGoal)) + " · " +
            context.getString(R.string.goal_short, format(settings.dailyGoal)),
    )
    val waterRow = SummaryRow(
        R.drawable.ic_water,
        context.getString(R.string.water_ml, format(ml.toLong())),
        context.getString(R.string.water_title) + " · " + context.getString(R.string.goal_short, format(settings.waterGoalMl.toLong())),
    )
    if (health == null) {
        val distance = Distance.inUnit(Distance.km(steps, settings.heightCm), settings.distanceUnit)
        val unit = context.getString(if (settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
        val kcal = Distance.kcal(steps, settings.heightCm, settings.weightKg)
        return listOf(
            stepsRow,
            waterRow,
            SummaryRow(R.drawable.ic_walk, formatDecimal(distance) + " " + unit, context.getString(R.string.widget_summary_distance)),
            SummaryRow(R.drawable.ic_fire, format(kcal) + " kcal", context.getString(R.string.widget_summary_calories)),
        )
    }
    val sleep = health.sleepMinutes?.takeIf { it > 0L }
    return listOf(
        stepsRow,
        waterRow,
        SummaryRow(
            R.drawable.ic_moon,
            if (sleep == null) "-" else context.getString(R.string.band_sleep_value, sleep / 60, sleep % 60),
            context.getString(R.string.widget_health_sleep),
        ),
        SummaryRow(
            R.drawable.ic_heart,
            health.resting?.let { context.getString(R.string.health_bpm, it) } ?: "-",
            context.getString(R.string.heart_resting),
        ),
    )
}

@Composable
private fun SummaryContent(context: Context, rows: List<SummaryRow>) {
    val size = LocalSize.current
    val height = size.height - PADDING * 2
    if (size.width >= TWO_COLUMNS) {
        val perColumn = (rows.size + 1) / 2
        val fits = (height / ROW_HEIGHT).toInt().coerceIn(1, perColumn)
        Row(modifier = GlanceModifier.fillMaxSize()) {
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                rows.take(perColumn).take(fits).forEach { InfoRow(it.icon, it.value, it.label) }
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                rows.drop(perColumn).take(fits).forEach { InfoRow(it.icon, it.value, it.label) }
            }
        }
    } else {
        val fits = (height / ROW_HEIGHT).toInt().coerceIn(1, rows.size)
        Column(modifier = GlanceModifier.fillMaxSize()) {
            rows.take(fits).forEach { InfoRow(it.icon, it.value, it.label) }
        }
    }
}
