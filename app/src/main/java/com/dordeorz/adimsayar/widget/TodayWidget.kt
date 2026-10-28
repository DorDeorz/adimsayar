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
import androidx.glance.unit.ColorProvider
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.ui.Tab
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
            val updated by repository.lastReadingWallMs.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            WidgetTheme(settings.dynamicColor) {
                WidgetSurface(Tab.Today, settings.widgetOpacity) {
                    TodayContent(localized, week.lastOrNull()?.steps ?: 0L, settings, updated)
                }
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
private val STAMP_HEIGHT = 14.dp
private val MIN_RING = 76.dp
private val TINY = 100.dp
private const val MAX_RING_PX = 480
private const val WIDE_RATIO = 1.6f

@Composable
private fun TodayContent(context: Context, steps: Long, settings: AppSettings, updated: Long?) {
    val size = LocalSize.current
    val goal = settings.goalOn(java.time.LocalDate.now())
    val fraction = (steps.toFloat() / goal).coerceIn(0f, 1f)
    val done = steps >= goal
    val innerWidth = size.width - PADDING * 2
    val innerHeight = size.height - PADDING * 2
    val squareRing = minOf(innerWidth, innerHeight - TITLE_HEIGHT - STAMP_HEIGHT)
    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            size.width < TINY || size.height < TINY -> TinyContent(context, steps, goal, fraction, done)
            size.width.value >= size.height.value * WIDE_RATIO && innerHeight >= MIN_RING ->
                WideContent(context, steps, fraction, done, innerHeight, settings, updated)
            squareRing >= MIN_RING -> SquareContent(context, steps, goal, fraction, done, squareRing, updated)
            else -> BarContent(context, steps, goal, fraction, done)
        }
    }
}

@Composable
private fun accent(done: Boolean): ColorProvider = if (done) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary

@Composable
private fun GoalLine(context: Context, goal: Long, done: Boolean, fontSize: Float) {
    if (!done) {
        Text(
            text = context.getString(R.string.goal_short, formatSteps(goal)),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = fontSize.sp),
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(R.drawable.ic_check),
            contentDescription = null,
            modifier = GlanceModifier.size((fontSize + 2f).dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.tertiary),
        )
        Spacer(GlanceModifier.width(3.dp))
        Text(
            text = context.getString(R.string.widget_goal_done),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.tertiary, fontSize = fontSize.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun TinyContent(context: Context, steps: Long, goal: Long, fraction: Float, done: Boolean) {
    Column(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = formatSteps(steps),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Image(
                    provider = ImageProvider(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = GlanceModifier.size(13.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.tertiary),
                )
                Spacer(GlanceModifier.width(2.dp))
            }
            Text(
                text = context.getString(R.string.percent_of_goal, percent(steps, goal)),
                maxLines = 1,
                style = TextStyle(color = accent(done), fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = fraction,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = accent(done),
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
    }
}

@Composable
private fun SquareContent(context: Context, steps: Long, goal: Long, fraction: Float, done: Boolean, ring: Dp, updated: Long?) {
    Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Title(context)
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
            Ring(context, fraction, ring, accent(done)) {
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
                    GoalLine(context, goal, done, (ring.value * 0.09f).coerceIn(9f, 12f))
                }
            }
        }
        Box(modifier = GlanceModifier.height(STAMP_HEIGHT), contentAlignment = Alignment.BottomCenter) {
            UpdatedText(context, updated)
        }
    }
}

@Composable
private fun WideContent(context: Context, steps: Long, fraction: Float, done: Boolean, ring: Dp, settings: AppSettings, updated: Long?) {
    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(context, fraction, ring, accent(done)) {
            Text(
                text = context.getString(R.string.percent_of_goal, percent(steps, settings.dailyGoal)),
                maxLines = 1,
                style = TextStyle(
                    color = accent(done),
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
            GoalLine(context, settings.dailyGoal, done, 12f)
            Text(
                text = distanceText(context, steps, settings),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            if (ring >= MIN_RING + STAMP_HEIGHT) UpdatedText(context, updated)
        }
    }
}

@Composable
private fun BarContent(context: Context, steps: Long, goal: Long, fraction: Float, done: Boolean) {
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
            color = accent(done),
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        Spacer(GlanceModifier.height(4.dp))
        GoalLine(context, goal, done, 11f)
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
internal fun Ring(context: Context, fraction: Float, ring: Dp, color: ColorProvider, content: @Composable () -> Unit) {
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
                colorFilter = ColorFilter.tint(color),
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
