package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.weekStartOf
import java.time.LocalDate

class YearWidget : GlanceAppWidget() {

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
                YearContent(localized, history, today, settings)
            }
        }
    }
}

class YearWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = YearWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val PADDING = 12.dp
private val TITLE_HEIGHT = 20.dp
private const val DAYS = 7
private const val MAX_WEEKS = 53
private const val GAP_RATIO = 0.18f

@Composable
private fun YearContent(context: Context, history: Map<LocalDate, Long>, today: LocalDate, settings: AppSettings) {
    val size = LocalSize.current
    val density = context.resources.displayMetrics.density
    val width = ((size.width - PADDING * 2).value * density).toInt().coerceIn(1, MAX_WIDTH_PX)
    val height = ((size.height - PADDING * 2 - TITLE_HEIGHT).value * density).toInt().coerceIn(1, MAX_HEIGHT_PX)
    val cell = height / DAYS.toFloat()
    val weeks = (width / cell).toInt().coerceIn(1, MAX_WEEKS)
    val firstWeek = weekStartOf(today, settings.weekStart).minusWeeks(weeks - 1L)
    val goal = settings.dailyGoal
    val goalDays = history.count { (date, steps) -> !date.isBefore(firstWeek) && !date.isAfter(today) && steps >= goal }
    val levels = Array(weeks) { week ->
        IntArray(DAYS) { day ->
            val date = firstWeek.plusDays(week * 7L + day)
            if (date.isAfter(today)) -1 else level(history[date] ?: 0L, goal)
        }
    }
    Column(modifier = widgetSurface()) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(TITLE_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(R.string.widget_year_title, weeks),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = context.getString(R.string.widget_year_goal_days, goalDays),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            Image(
                provider = ImageProvider(heatmapBitmap(width, height, cell, levels, filled = false)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer),
            )
            Image(
                provider = ImageProvider(heatmapBitmap(width, height, cell, levels, filled = true)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
        }
    }
}

private fun level(steps: Long, goal: Long): Int = when {
    steps <= 0L -> 0
    steps * 2 < goal -> 1
    steps < goal -> 2
    steps * 2 < goal * 3 -> 3
    else -> 4
}

private val LEVEL_ALPHA = intArrayOf(0, 70, 130, 200, 255)

private fun heatmapBitmap(width: Int, height: Int, cell: Float, levels: Array<IntArray>, filled: Boolean): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val gap = cell * GAP_RATIO
    val left = (width - cell * levels.size) / 2f
    val radius = cell / 5f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    levels.forEachIndexed { week, days ->
        days.forEachIndexed { day, value ->
            if (value < 0) return@forEachIndexed
            val alpha = if (filled) LEVEL_ALPHA[value] else if (value == 0) 255 else 0
            if (alpha == 0) return@forEachIndexed
            paint.color = android.graphics.Color.argb(alpha, 255, 255, 255)
            val x = left + week * cell
            val y = day * cell
            canvas.drawRoundRect(RectF(x + gap / 2, y + gap / 2, x + cell - gap / 2, y + cell - gap / 2), radius, radius, paint)
        }
    }
    return bitmap
}
