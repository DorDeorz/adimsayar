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
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.XiaomiSteps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class HourlyWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StepRepository.get(context)
        val today = LocalDate.now()
        val initial = loadHours(context, today)
        provideContent {
            val flow = remember { repository.observeWeek(today).map { loadHours(context, today) }.flowOn(Dispatchers.IO) }
            val hours by flow.collectAsState(initial)
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            WidgetTheme(settings.dynamicColor) {
                HourlyContent(localized, hours)
            }
        }
    }

    private suspend fun loadHours(context: Context, date: LocalDate): LongArray? =
        if (XiaomiSteps.isEnabled(context)) XiaomiSteps.dayDetail(context, date)?.hours else StepRepository.get(context).loadPhoneHours(date)
}

class HourlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HourlyWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val PADDING = 12.dp
private val TITLE_HEIGHT = 20.dp
private val LABEL_HEIGHT = 16.dp
private const val HOURS = 24

@Composable
private fun HourlyContent(context: Context, hours: LongArray?) {
    val size = LocalSize.current
    Column(modifier = widgetSurface()) {
        Row(modifier = GlanceModifier.fillMaxWidth().height(TITLE_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = context.getString(R.string.widget_hourly_title),
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
            )
            if (hours != null) {
                val peak = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0L }
                if (peak != null) {
                    Text(
                        text = context.getString(R.string.widget_hourly_peak, peak, formatCompact(hours[peak])),
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
        if (hours == null || hours.all { it == 0L }) {
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                Text(
                    text = context.getString(R.string.widget_hourly_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
            }
            return@Column
        }
        val density = context.resources.displayMetrics.density
        val width = ((size.width - PADDING * 2).value * density).toInt().coerceIn(1, MAX_WIDTH_PX)
        val height = ((size.height - PADDING * 2 - TITLE_HEIGHT - LABEL_HEIGHT).value * density).toInt().coerceIn(1, MAX_HEIGHT_PX)
        Image(
            provider = ImageProvider(hourlyBitmap(width, height, hours)),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Row(modifier = GlanceModifier.fillMaxWidth().height(LABEL_HEIGHT)) {
            listOf(0, 6, 12, 18).forEach { hour ->
                Text(
                    text = context.getString(R.string.hour_label, hour),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
                )
            }
        }
    }
}

internal const val MAX_WIDTH_PX = 1200
internal const val MAX_HEIGHT_PX = 800

private fun hourlyBitmap(width: Int, height: Int, hours: LongArray): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val max = hours.max().coerceAtLeast(1L)
    val slot = width / HOURS.toFloat()
    val bar = slot * 0.7f
    val radius = bar / 3f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    val empty = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.argb(60, 255, 255, 255) }
    for (hour in 0 until HOURS) {
        val left = slot * hour + (slot - bar) / 2
        val steps = hours.getOrElse(hour) { 0L }
        if (steps <= 0L) {
            canvas.drawRoundRect(RectF(left, height - 4f, left + bar, height.toFloat()), radius, radius, empty)
            continue
        }
        val top = (height - height * (steps.toFloat() / max)).coerceAtMost(height - 4f)
        canvas.drawRoundRect(RectF(left, top, left + bar, height.toFloat()), radius, radius, paint)
    }
    return bitmap
}
