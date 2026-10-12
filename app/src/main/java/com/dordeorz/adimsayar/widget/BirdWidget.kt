package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.dordeorz.adimsayar.data.Avatar
import com.dordeorz.adimsayar.data.BirdState
import com.dordeorz.adimsayar.data.KeptMedals
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.StreakFreezes
import com.dordeorz.adimsayar.ui.BirdPainter
import com.dordeorz.adimsayar.ui.formatCompact
import com.dordeorz.adimsayar.ui.text
import com.dordeorz.adimsayar.ui.title
import java.time.LocalDate
import java.time.LocalTime

class BirdWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StepRepository.get(context)
        val today = LocalDate.now()
        val hour = LocalTime.now().hour
        val initial = repository.loadHistory()
        val initialFreezes = StreakFreezes.load(context)
        provideContent {
            val history by remember { repository.observeHistory() }.collectAsState(initial)
            val freezes by remember { StreakFreezes.observe(context) }.collectAsState(initialFreezes)
            val kept by remember { KeptMedals.observe(context) }.collectAsState(emptyMap())
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            val bird = remember(history, freezes, kept, settings) {
                val computed = AchievementMath.compute(history, today, settings.dailyGoal, settings.weeklyGoal, settings.weekStart, freezes = freezes)
                val achievements = computed.copy(medals = KeptMedals.apply(computed.medals, kept))
                Avatar.state(achievements, history, freezes, today, hour, settings.dailyGoal)
            }
            WidgetTheme(settings.dynamicColor) {
                BirdContent(localized, bird)
            }
        }
    }
}

class BirdWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BirdWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val PADDING = 12.dp
private val TEXT_WIDTH = 120.dp
private val INFO_HEIGHT = 72.dp
private val MOOD_HEIGHT = 34.dp
private val MIN_BIRD = 48.dp
private const val WIDE_RATIO = 1.4f
private const val MAX_BIRD_PX = 600

@Composable
private fun BirdContent(context: Context, bird: BirdState) {
    val size = LocalSize.current
    val width = size.width - PADDING * 2
    val height = size.height - PADDING * 2
    val wide = size.width.value >= size.height.value * WIDE_RATIO && width - height >= TEXT_WIDTH
    Box(modifier = widgetSurface(), contentAlignment = Alignment.Center) {
        when {
            wide -> Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                BirdBitmap(context, bird, height)
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    BirdInfo(context, bird, showMood = height >= INFO_HEIGHT + MOOD_HEIGHT)
                }
            }
            height - width >= INFO_HEIGHT - MIN_BIRD || height >= width + INFO_HEIGHT / 2 -> {
                val showMood = height - INFO_HEIGHT - MOOD_HEIGHT >= minOf(width, MIN_BIRD * 2)
                val birdSide = minOf(width, height - INFO_HEIGHT - if (showMood) MOOD_HEIGHT else 0.dp).coerceAtLeast(MIN_BIRD)
                Column(modifier = GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                        BirdBitmap(context, bird, birdSide)
                    }
                    BirdInfo(context, bird, showMood)
                }
            }
            else -> BirdBitmap(context, bird, minOf(width, height))
        }
    }
}

@Composable
private fun BirdInfo(context: Context, bird: BirdState, showMood: Boolean) {
    Text(
        text = context.getString(bird.stage.title()),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
    )
    Text(
        text = if (bird.leveledUpToday) context.getString(R.string.bird_level_up) else context.getString(R.string.bird_level, bird.level),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
    )
    if (showMood) {
        Text(
            text = context.getString(bird.mood.text()),
            maxLines = 2,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
        )
    }
    Spacer(GlanceModifier.height(6.dp))
    LinearProgressIndicator(
        progress = bird.levelFraction,
        modifier = GlanceModifier.fillMaxWidth().height(6.dp),
        color = GlanceTheme.colors.primary,
        backgroundColor = GlanceTheme.colors.secondaryContainer,
    )
    Spacer(GlanceModifier.height(4.dp))
    Text(
        text = context.getString(R.string.bird_level_left, formatCompact(bird.stepsToNextLevel)),
        maxLines = 1,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
    )
}

@Composable
private fun BirdBitmap(context: Context, bird: BirdState, side: Dp) {
    val px = (side.value * context.resources.displayMetrics.density).toInt().coerceIn(1, MAX_BIRD_PX)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    BirdPainter.draw(Canvas(bitmap), px.toFloat(), bird)
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = context.getString(bird.stage.title()),
        modifier = GlanceModifier.size(side),
    )
}
