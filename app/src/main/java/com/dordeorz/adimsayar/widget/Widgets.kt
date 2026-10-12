package com.dordeorz.adimsayar.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.ui.CLOCK_FORMAT
import com.dordeorz.adimsayar.ui.Tab
import com.dordeorz.adimsayar.ui.theme.DarkColors
import com.dordeorz.adimsayar.ui.theme.LightColors
import com.dordeorz.adimsayar.ui.thousandSuffix
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import androidx.glance.text.TextStyle

object Widgets {

    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        WeekWidget().updateAll(context)
        StreakWidget().updateAll(context)
        MonthWidget().updateAll(context)
        HourlyWidget().updateAll(context)
        YearWidget().updateAll(context)
        HealthWidget().updateAll(context)
        BirdWidget().updateAll(context)
        WaterWidget().updateAll(context)
        RouteWidget().updateAll(context)
        ChallengeWidget().updateAll(context)
        WeightWidget().updateAll(context)
        SummaryWidget().updateAll(context)
    }

    suspend fun updateWeight(context: Context) {
        WeightWidget().updateAll(context)
    }

    suspend fun updateWater(context: Context) {
        WaterWidget().updateAll(context)
        SummaryWidget().updateAll(context)
    }
}

private val AppColors = ColorProviders(light = LightColors, dark = DarkColors)

@Composable
internal fun WidgetTheme(dynamicColor: Boolean, content: @Composable () -> Unit) {
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = AppColors, content = content)
    }
}

private val TabKey = ActionParameters.Key<String>(MainActivity.EXTRA_TAB)

@Composable
internal fun WidgetSurface(tab: Tab, opacity: Int, content: @Composable () -> Unit) {
    val base = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .cornerRadius(24.dp)
        .clickable(actionStartActivity<MainActivity>(actionParametersOf(TabKey to tab.name.lowercase())))
    Box(modifier = if (opacity >= FULL_OPACITY) base.background(GlanceTheme.colors.widgetBackground) else base) {
        if (opacity in 1 until FULL_OPACITY) {
            Image(
                provider = ImageProvider(opacityBitmap(opacity)),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.widgetBackground),
            )
        }
        Box(modifier = GlanceModifier.fillMaxSize().padding(12.dp)) {
            content()
        }
    }
}

private const val FULL_OPACITY = 100

private fun opacityBitmap(opacity: Int): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).apply {
    eraseColor(android.graphics.Color.argb(opacity * 255 / FULL_OPACITY, 255, 255, 255))
}

@Composable
internal fun UpdatedText(context: Context, wallMs: Long?) {
    if (wallMs == null) return
    Text(
        text = context.getString(R.string.widget_updated_at, CLOCK_FORMAT.format(Instant.ofEpochMilli(wallMs).atZone(ZoneId.systemDefault()))),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp),
    )
}

internal fun formatSteps(steps: Long): String = NumberFormat.getIntegerInstance(Locales.current).format(steps)

internal fun formatCompact(steps: Long): String = if (steps < 1_000L) {
    steps.toString()
} else {
    String.format(Locales.current, "%.1f%s", steps / 1_000.0, thousandSuffix(Locales.current))
}

internal fun shortDayName(date: java.time.LocalDate): String = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locales.current)

@Composable
internal fun ColumnScope.InfoRow(icon: Int, value: String, label: String) {
    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(18.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = value,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = label,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
    }
}

@Composable
internal fun WidgetTitle(text: String) {
    Text(
        text = text,
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}

internal fun tintBitmap(width: Int, height: Int, draw: (Canvas) -> Unit): Bitmap =
    Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }

internal fun whitePaint(stroke: Float = 0f): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = android.graphics.Color.WHITE
    if (stroke > 0f) {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
}

internal fun Dp.px(context: Context, max: Int = MAX_BITMAP_PX): Int = (value * context.resources.displayMetrics.density).toInt().coerceIn(1, max)

private const val MAX_BITMAP_PX = 720
