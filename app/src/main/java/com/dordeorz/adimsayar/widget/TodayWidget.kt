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
import androidx.compose.ui.unit.Dp
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
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.Distance
import com.dordeorz.adimsayar.data.DistanceUnit
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.ui.format
import com.dordeorz.adimsayar.ui.formatDecimal
import com.dordeorz.adimsayar.ui.percent
import java.time.LocalDate

class TodayWidget : GlanceAppWidget() {

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
                TodayContent(localized, week.lastOrNull()?.steps ?: 0L, settings)
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val PADDING = 12.dp
private val TITLE_HEIGHT = 18.dp
private val MIN_RING = 76.dp
private val TINY = 100.dp
private const val MAX_RING_PX = 480
private const val WIDE_RATIO = 1.6f

@Composable
private fun TodayContent(context: Context, steps: Long, settings: AppSettings) {
    val size = LocalSize.current
    val goal = settings.dailyGoal
    val fraction = (steps.toFloat() / goal).coerceIn(0f, 1f)
    val innerWidth = size.width - PADDING * 2
    val innerHeight = size.height - PADDING * 2
    Box(modifier = widgetSurface(), contentAlignment = Alignment.Center) {
        when {
            size.width < TINY || size.height < TINY -> TinyContent(context, steps, goal)
            size.width.value >= size.height.value * WIDE_RATIO && innerHeight >= MIN_RING ->
                WideContent(context, steps, fraction, innerHeight, settings)
            minOf(innerWidth, innerHeight - TITLE_HEIGHT) >= MIN_RING ->
                SquareContent(context, steps, goal, fraction, minOf(innerWidth, innerHeight - TITLE_HEIGHT))
            else -> BarContent(context, steps, goal, fraction)
        }
    }
}

@Composable
private fun TinyContent(context: Context, steps: Long, goal: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatSteps(steps),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = context.getString(R.string.percent_of_goal, percent(steps, goal)),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun SquareContent(context: Context, steps: Long, goal: Long, fraction: Float, ring: Dp) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Title(context)
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            Ring(context, fraction, ring) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = formatSteps(steps),
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = (ring.value * 0.2f).coerceAtMost(30f).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    Text(
                        text = context.getString(R.string.goal_short, formatSteps(goal)),
                        maxLines = 1,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = (ring.value * 0.09f).coerceIn(9f, 12f).sp,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun WideContent(context: Context, steps: Long, fraction: Float, ring: Dp, settings: AppSettings) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(context, fraction, ring) {
            Text(
                text = context.getString(R.string.percent_of_goal, percent(steps, settings.dailyGoal)),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = (ring.value * 0.2f).coerceAtMost(24f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(GlanceModifier.width(16.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Title(context)
            Text(
                text = formatSteps(steps),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = context.getString(R.string.goal_short, formatSteps(settings.dailyGoal)),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            Text(
                text = distanceText(context, steps, settings),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
        }
    }
}

@Composable
private fun BarContent(context: Context, steps: Long, goal: Long, fraction: Float) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Title(context)
        Text(
            text = formatSteps(steps),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = fraction,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = context.getString(R.string.goal_short, formatSteps(goal)),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
        )
    }
}

@Composable
private fun Title(context: Context) {
    Text(
        text = context.getString(R.string.today),
        modifier = GlanceModifier.height(TITLE_HEIGHT),
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}

@Composable
private fun Ring(context: Context, fraction: Float, ring: Dp, content: @Composable () -> Unit) {
    val px = (ring.value * context.resources.displayMetrics.density).toInt().coerceIn(1, MAX_RING_PX)
    val stroke = px * 0.09f
    Box(modifier = GlanceModifier.size(ring), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ringBitmap(px, stroke, 1f)),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer),
        )
        if (fraction > 0f) {
            Image(
                provider = ImageProvider(ringBitmap(px, stroke, fraction)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
        }
        content()
    }
}

private fun distanceText(context: Context, steps: Long, settings: AppSettings): String {
    val distance = Distance.inUnit(Distance.km(steps, settings.heightCm), settings.distanceUnit)
    val unit = context.getString(if (settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
    val kcal = Distance.kcal(steps, settings.heightCm, settings.weightKg)
    return context.getString(R.string.distance_calories, formatDecimal(distance), unit, format(kcal))
}

internal fun ringBitmap(size: Int, stroke: Float, fraction: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    val inset = stroke / 2f
    Canvas(bitmap).drawArc(RectF(inset, inset, size - inset, size - inset), -90f, 360f * fraction, false, paint)
    return bitmap
}
