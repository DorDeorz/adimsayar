package com.dordeorz.adimsayar.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
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
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.Weight
import com.dordeorz.adimsayar.data.Weights
import com.dordeorz.adimsayar.ui.FULL_DATE_FORMAT
import com.dordeorz.adimsayar.ui.Tab
import com.dordeorz.adimsayar.ui.formatDecimal
import com.dordeorz.adimsayar.ui.signedKg

class WeightWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val weights by remember { Weights.observe(context) }.collectAsState(emptyList())
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            WidgetTheme(settings.dynamicColor) {
                WidgetSurface(Tab.Health, settings.widgetOpacity) {
                    WeightContent(localized, weights)
                }
            }
        }
    }
}

class WeightWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeightWidget()
}

private val PADDING = 12.dp
private val TEXT_HEIGHT = 70.dp
private val MIN_CHART = 30.dp
private const val CHART_POINTS = 30

@Composable
private fun WeightContent(context: Context, weights: List<Weight>) {
    val size = LocalSize.current
    val width = size.width - PADDING * 2
    val height = size.height - PADDING * 2
    Column(modifier = GlanceModifier.fillMaxSize()) {
        WidgetTitle(context.getString(R.string.widget_weight_label))
        val latest = weights.lastOrNull()
        if (latest == null) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = context.getString(R.string.widget_weight_empty),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            return@Column
        }
        Text(
            text = context.getString(R.string.weight_kg, formatDecimal(latest.kg)),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 24.sp, fontWeight = FontWeight.Bold),
        )
        val first = weights.first()
        if (weights.size > 1) {
            Text(
                text = context.getString(R.string.weight_kg, signedKg(latest.kg - first.kg)) + " · " +
                    context.getString(R.string.weight_since, FULL_DATE_FORMAT.format(first.date)),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
        val chartHeight = height - TEXT_HEIGHT
        if (weights.size > 1 && chartHeight >= MIN_CHART) {
            Spacer(GlanceModifier.height(6.dp))
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                WeightChart(context, weights.takeLast(CHART_POINTS), width, chartHeight)
            }
        }
    }
}

@Composable
private fun WeightChart(context: Context, weights: List<Weight>, width: Dp, height: Dp) {
    val w = width.px(context)
    val h = height.px(context)
    val density = context.resources.displayMetrics.density
    Image(
        provider = ImageProvider(tintBitmap(w, h) { chart(it, weights, w.toFloat(), h.toFloat(), density) }),
        contentDescription = null,
        modifier = GlanceModifier.size(width, height),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
    )
}

private fun chart(canvas: Canvas, weights: List<Weight>, width: Float, height: Float, density: Float) {
    val min = weights.minOf { it.kg }
    val max = weights.maxOf { it.kg }
    val span = (max - min).coerceAtLeast(1.0)
    val inset = 5f * density
    val first = weights.first().date.toEpochDay()
    val days = (weights.last().date.toEpochDay() - first).coerceAtLeast(1L).toFloat()
    val points = weights.map {
        val x = inset + (it.date.toEpochDay() - first) / days * (width - inset * 2)
        val y = inset + ((max - it.kg) / span).toFloat() * (height - inset * 2)
        x to y
    }
    val path = Path().apply { points.forEachIndexed { index, (x, y) -> if (index == 0) moveTo(x, y) else lineTo(x, y) } }
    canvas.drawPath(path, whitePaint(2.5f * density))
    val (lastX, lastY) = points.last()
    canvas.drawCircle(lastX, lastY, 4f * density, whitePaint())
}
