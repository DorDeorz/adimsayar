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
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.background.Reminders
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.Water
import com.dordeorz.adimsayar.ui.Tab
import com.dordeorz.adimsayar.ui.format
import java.time.LocalDate

class WaterWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val initial = Water.today(context)
        provideContent {
            val water by remember { Water.observe(context) }.collectAsState(null)
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            WidgetTheme(settings.dynamicColor) {
                WidgetSurface(Tab.Today, settings.widgetOpacity) {
                    WaterContent(localized, water?.let { it[today] ?: 0 } ?: initial, settings)
                }
            }
        }
    }
}

class WaterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WaterWidget()
}

class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val settings = SettingsStore.get(context).settings.value
        val drunk = Water.add(context, LocalDate.now(), settings.glassMl)
        Reminders.onWaterChanged(context, drunk)
        Widgets.updateWater(context)
    }
}

private val PADDING = 12.dp
private val TINY = 100.dp
private val BUTTON_HEIGHT = 32.dp
private val TEXT_HEIGHT = 62.dp
private const val WIDE_RATIO = 1.3f

@Composable
private fun WaterContent(context: Context, ml: Int, settings: AppSettings) {
    val size = LocalSize.current
    val width = size.width - PADDING * 2
    val height = size.height - PADDING * 2
    val goal = settings.waterGoalMl
    val fraction = (ml.toFloat() / goal).coerceIn(0f, 1f)
    val done = ml >= goal
    when {
        size.width < TINY || size.height < TINY -> Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.water_ml, format(ml.toLong())),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(4.dp))
            AddButton(context, settings.glassMl)
        }
        size.width.value >= size.height.value * WIDE_RATIO -> Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glass(context, fraction, done, minOf(height, width / 3))
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                WaterText(context, ml, goal, done, settings.glassMl)
                Spacer(GlanceModifier.height(6.dp))
                AddButton(context, settings.glassMl)
            }
        }
        else -> Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            WidgetTitle(context.getString(R.string.water_title))
            Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                val glass = minOf(width, height - TEXT_HEIGHT - BUTTON_HEIGHT)
                if (glass >= 24.dp) Glass(context, fraction, done, glass)
            }
            Text(
                text = context.getString(R.string.water_value, format(ml.toLong()), format(goal.toLong())),
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(GlanceModifier.height(6.dp))
            AddButton(context, settings.glassMl)
        }
    }
}

@Composable
private fun WaterText(context: Context, ml: Int, goal: Int, done: Boolean, glassMl: Int) {
    WidgetTitle(context.getString(R.string.water_title))
    Text(
        text = context.getString(R.string.water_value, format(ml.toLong()), format(goal.toLong())),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
    )
    Spacer(GlanceModifier.height(4.dp))
    LinearProgressIndicator(
        progress = (ml.toFloat() / goal).coerceIn(0f, 1f),
        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
        color = if (done) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.secondaryContainer,
    )
    Spacer(GlanceModifier.height(4.dp))
    val left = goal - ml
    Text(
        text = if (done) context.getString(R.string.widget_goal_done) else context.getString(R.string.water_left, format(left.toLong()), (left + glassMl - 1) / glassMl),
        maxLines = 1,
        style = TextStyle(color = if (done) GlanceTheme.colors.tertiary else GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
    )
}

@Composable
private fun AddButton(context: Context, glassMl: Int) {
    Box(
        modifier = GlanceModifier
            .cornerRadius(16.dp)
            .background(GlanceTheme.colors.primary)
            .clickable(actionRunCallback<AddWaterAction>())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = context.getString(R.string.water_add_glass, glassMl),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun Glass(context: Context, fraction: Float, done: Boolean, side: Dp) {
    val px = side.px(context)
    Box(modifier = GlanceModifier.size(side), contentAlignment = Alignment.Center) {
        if (fraction > 0f) {
            Image(
                provider = ImageProvider(tintBitmap(px, px) { glassFill(it, px.toFloat(), fraction) }),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(if (done) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary),
            )
        }
        Image(
            provider = ImageProvider(tintBitmap(px, px) { glassOutline(it, px.toFloat()) }),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

private fun glassPath(size: Float): Path = Path().apply {
    moveTo(size * 0.2f, size * 0.08f)
    lineTo(size * 0.8f, size * 0.08f)
    lineTo(size * 0.7f, size * 0.92f)
    lineTo(size * 0.3f, size * 0.92f)
    close()
}

private fun glassOutline(canvas: Canvas, size: Float) {
    canvas.drawPath(glassPath(size), whitePaint(size * 0.05f))
}

private fun glassFill(canvas: Canvas, size: Float, fraction: Float) {
    val top = size * 0.08f
    val bottom = size * 0.92f
    canvas.save()
    canvas.clipPath(glassPath(size))
    canvas.drawRect(0f, bottom - (bottom - top) * fraction, size, size, whitePaint())
    canvas.restore()
}
