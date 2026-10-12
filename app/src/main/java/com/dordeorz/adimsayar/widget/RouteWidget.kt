package com.dordeorz.adimsayar.widget

import android.appwidget.AppWidgetManager
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
import androidx.glance.appwidget.LinearProgressIndicator
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
import com.dordeorz.adimsayar.background.Schedules
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.Distance
import com.dordeorz.adimsayar.data.DistanceUnit
import com.dordeorz.adimsayar.data.NO_ROUTE_START
import com.dordeorz.adimsayar.data.RouteProgress
import com.dordeorz.adimsayar.data.Routes
import com.dordeorz.adimsayar.data.SettingsStore
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.ui.Tab
import com.dordeorz.adimsayar.ui.format
import com.dordeorz.adimsayar.ui.formatDecimal
import java.time.LocalDate
import kotlin.math.roundToInt

class RouteWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = StepRepository.get(context)
        val today = LocalDate.now()
        val initial = repository.loadHistory()
        provideContent {
            val history by remember { repository.observeHistory() }.collectAsState(initial)
            val settings by SettingsStore.get(context).settings.collectAsState()
            val localized = remember(settings.language) { Locales.wrap(context) }
            val route = Routes.byId(settings.routeId)
            val progress = remember(route, history, settings.routeStartDay, settings.heightCm) {
                if (route == null || settings.routeStartDay == NO_ROUTE_START) {
                    null
                } else {
                    Routes.progress(route, history, LocalDate.ofEpochDay(settings.routeStartDay), today, settings.heightCm)
                }
            }
            WidgetTheme(settings.dynamicColor) {
                WidgetSurface(Tab.Medals, settings.widgetOpacity) {
                    RouteContent(localized, progress, settings)
                }
            }
        }
    }
}

class RouteWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RouteWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        Schedules.requestRead(context)
    }
}

private val PADDING = 12.dp
private val TEXT_HEIGHT = 52.dp
private val NEXT_HEIGHT = 16.dp
private val MIN_MAP = 36.dp

@Composable
private fun RouteContent(context: Context, progress: RouteProgress?, settings: AppSettings) {
    val size = LocalSize.current
    val width = size.width - PADDING * 2
    val height = size.height - PADDING * 2
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (progress == null) {
            WidgetTitle(context.getString(R.string.route_title))
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = context.getString(R.string.widget_route_none),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            return@Column
        }
        val route = progress.route
        val unit = context.getString(if (settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
        WidgetTitle(context.getString(R.string.route_name, route.stops.first().name, route.stops.last().name))
        val showNext = height - TEXT_HEIGHT - NEXT_HEIGHT >= MIN_MAP
        val mapHeight = height - TEXT_HEIGHT - if (showNext) NEXT_HEIGHT else 0.dp
        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            if (mapHeight >= MIN_MAP) RouteMap(context, progress, width, mapHeight)
        }
        Text(
            text = context.getString(
                R.string.route_distance,
                formatDecimal(Distance.inUnit(progress.km.coerceAtMost(route.totalKm.toDouble()), settings.distanceUnit)),
                format(Distance.inUnit(route.totalKm.toDouble(), settings.distanceUnit).roundToInt().toLong()),
                unit,
            ) + " · " + context.getString(R.string.percent_of_goal, (progress.fraction * 100).toInt()),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress.fraction,
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = if (progress.finishedOn != null) GlanceTheme.colors.tertiary else GlanceTheme.colors.primary,
            backgroundColor = GlanceTheme.colors.secondaryContainer,
        )
        val next = progress.next
        if (showNext) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = if (next == null) {
                    context.getString(R.string.widget_goal_done)
                } else {
                    context.getString(R.string.route_next, next.name, formatDecimal(Distance.inUnit(next.km - progress.km, settings.distanceUnit)), unit)
                },
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
            )
        }
    }
}

@Composable
private fun RouteMap(context: Context, progress: RouteProgress, width: Dp, height: Dp) {
    val w = width.px(context)
    val h = height.px(context)
    val density = context.resources.displayMetrics.density
    val project = Routes.projector(progress.route, w.toFloat(), h.toFloat(), 8f * density)
    val points = progress.route.stops.map { project(it.lat, it.lon) }
    val (lat, lon) = Routes.position(progress.route, progress.km)
    val current = project(lat, lon)
    val walked = points.take(progress.reached + 1) + if (progress.next != null) listOf(current) else emptyList()
    Box(modifier = GlanceModifier.size(width, height)) {
        Image(
            provider = ImageProvider(tintBitmap(w, h) { canvas -> track(canvas, points, density) }),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.secondaryContainer),
        )
        Image(
            provider = ImageProvider(tintBitmap(w, h) { canvas -> walkedPath(canvas, walked, current, density) }),
            contentDescription = null,
            modifier = GlanceModifier.fillMaxSize(),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
    }
}

private fun polyline(points: List<Pair<Float, Float>>): Path = Path().apply {
    points.forEachIndexed { index, (x, y) -> if (index == 0) moveTo(x, y) else lineTo(x, y) }
}

private fun track(canvas: Canvas, points: List<Pair<Float, Float>>, density: Float) {
    canvas.drawPath(polyline(points), whitePaint(3f * density))
    val dot = whitePaint()
    points.forEach { (x, y) -> canvas.drawCircle(x, y, 4f * density, dot) }
}

private fun walkedPath(canvas: Canvas, walked: List<Pair<Float, Float>>, current: Pair<Float, Float>, density: Float) {
    if (walked.size > 1) canvas.drawPath(polyline(walked), whitePaint(4f * density))
    val dot = whitePaint()
    walked.dropLast(1).forEach { (x, y) -> canvas.drawCircle(x, y, 4f * density, dot) }
    canvas.drawCircle(current.first, current.second, 6f * density, dot)
}
