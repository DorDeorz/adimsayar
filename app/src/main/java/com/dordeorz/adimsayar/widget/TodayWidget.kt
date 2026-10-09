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
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
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
            TodayContent(localized, week.lastOrNull()?.steps ?: 0L, settings.dailyGoal)
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
private const val MAX_RING_PX = 480

@Composable
private fun TodayContent(context: Context, steps: Long, goal: Long) {
    val size = LocalSize.current
    val ring = minOf(size.width - PADDING * 2, size.height - PADDING * 2 - TITLE_HEIGHT)
    val fraction = (steps.toFloat() / goal).coerceIn(0f, 1f)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(R.drawable.widget_background))
            .clickable(actionStartActivity<MainActivity>())
            .padding(PADDING),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.today),
            modifier = GlanceModifier.height(TITLE_HEIGHT),
            style = TextStyle(color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
        if (ring >= MIN_RING) {
            RingContent(context, steps, goal, fraction, ring)
        } else {
            BarContent(context, steps, goal, fraction)
        }
    }
}

@Composable
private fun RingContent(context: Context, steps: Long, goal: Long, fraction: Float, ring: Dp) {
    val px = (ring.value * context.resources.displayMetrics.density).toInt().coerceIn(1, MAX_RING_PX)
    val stroke = px * 0.09f
    Box(modifier = GlanceModifier.size(ring), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ringBitmap(px, stroke, 1f)),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(AccentMuted),
        )
        if (fraction > 0f) {
            Image(
                provider = ImageProvider(ringBitmap(px, stroke, fraction)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(Accent),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatSteps(steps),
                maxLines = 1,
                style = TextStyle(
                    color = TextPrimary,
                    fontSize = (ring.value * 0.2f).coerceAtMost(30f).sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = context.getString(R.string.goal_short, formatSteps(goal)),
                maxLines = 1,
                style = TextStyle(color = TextSecondary, fontSize = (ring.value * 0.09f).coerceIn(9f, 12f).sp),
            )
        }
    }
}

@Composable
private fun BarContent(context: Context, steps: Long, goal: Long, fraction: Float) {
    Text(
        text = formatSteps(steps),
        maxLines = 1,
        style = TextStyle(color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold),
    )
    Spacer(GlanceModifier.height(6.dp))
    LinearProgressIndicator(
        progress = fraction,
        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
        color = Accent,
        backgroundColor = AccentMuted,
    )
    Spacer(GlanceModifier.height(4.dp))
    Text(
        text = context.getString(R.string.goal_short, formatSteps(goal)),
        style = TextStyle(color = TextSecondary, fontSize = 11.sp),
    )
}

private fun ringBitmap(size: Int, stroke: Float, fraction: Float): Bitmap {
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
