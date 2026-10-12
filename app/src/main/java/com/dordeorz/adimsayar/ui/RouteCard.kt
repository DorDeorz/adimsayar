package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.Distance
import com.dordeorz.adimsayar.data.DistanceUnit
import com.dordeorz.adimsayar.data.NO_ROUTE_START
import com.dordeorz.adimsayar.data.RouteProgress
import com.dordeorz.adimsayar.data.Routes
import com.dordeorz.adimsayar.data.VirtualRoute
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

private val MAP_HEIGHT = 180.dp
private val MAP_PADDING = 24.dp

@Composable
internal fun RouteCard(state: MainUiState, onRouteChange: (String?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val settings = state.settings
    val route = Routes.byId(settings.routeId)
    val progress = remember(route, state.history, settings.routeStartDay, state.today, settings.heightCm) {
        if (route == null || settings.routeStartDay == NO_ROUTE_START) {
            null
        } else {
            Routes.progress(route, state.history, LocalDate.ofEpochDay(settings.routeStartDay), state.today, settings.heightCm)
        }
    }
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.route_title), style = MaterialTheme.typography.titleMedium)
            if (progress == null) {
                Text(
                    stringResource(R.string.route_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = { picking = true }) { Text(stringResource(R.string.route_choose)) }
            } else {
                RouteDetail(progress, settings)
                OutlinedButton(onClick = { picking = true }) { Text(stringResource(R.string.route_change)) }
            }
        }
    }
    if (picking) {
        RoutePicker(
            settings = settings,
            onPick = {
                onRouteChange(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun RouteDetail(progress: RouteProgress, settings: AppSettings) {
    val route = progress.route
    val unit = stringResource(if (settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
    Text(routeName(route), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    RouteMap(progress)
    LinearProgressIndicator(
        progress = { progress.fraction },
        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(
                R.string.route_distance,
                formatDecimal(Distance.inUnit(progress.km.coerceAtMost(route.totalKm.toDouble()), settings.distanceUnit)),
                format(Distance.inUnit(route.totalKm.toDouble(), settings.distanceUnit).roundToInt().toLong()),
                unit,
            ),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            stringResource(R.string.percent_of_goal, (progress.fraction * 100).toInt()),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    val finishedOn = progress.finishedOn
    val next = progress.next
    if (finishedOn != null) {
        val days = ChronoUnit.DAYS.between(progress.start, finishedOn).toInt() + 1
        Text(stringResource(R.string.route_done, days), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    } else if (next != null) {
        Text(
            stringResource(R.string.route_next, next.name, formatDecimal(Distance.inUnit(next.km - progress.km, settings.distanceUnit)), unit),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        stringResource(R.string.route_started, FULL_DATE_FORMAT.format(progress.start)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        stringResource(R.string.route_approx),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RouteMap(progress: RouteProgress) {
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val label = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val labelStrong = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    Canvas(modifier = Modifier.fillMaxWidth().height(MAP_HEIGHT)) {
        val stops = progress.route.stops
        val project = projector(progress.route, size.width, size.height, MAP_PADDING.toPx())
        val points = stops.map { project(it.lat, it.lon) }
        val width = 4.dp.toPx()
        drawPath(polyline(points), line, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val reached = progress.reached
        val (lat, lon) = Routes.position(progress.route, progress.km)
        val current = project(lat, lon)
        val walked = points.take(reached + 1) + if (progress.next != null) listOf(current) else emptyList()
        if (walked.size > 1) {
            drawPath(polyline(walked), primary, style = Stroke(width * 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        points.forEachIndexed { index, point ->
            val radius = 5.dp.toPx()
            if (index <= reached) {
                drawCircle(primary, radius, point)
            } else {
                drawCircle(surface, radius, point)
                drawCircle(line, radius, point, style = Stroke(2.dp.toPx()))
            }
        }
        drawCircle(surface, 10.dp.toPx(), current)
        drawCircle(primary, 7.dp.toPx(), current)
        val nextIndex = stops.indexOfFirst { it.km > progress.km }
        val labelled = setOf(0, stops.lastIndex, nextIndex).filter { it >= 0 }
        labelled.forEach { index ->
            drawLabel(measurer, stops[index].name, points[index], if (index == nextIndex) labelStrong else label)
        }
    }
}

private fun projector(route: VirtualRoute, width: Float, height: Float, padding: Float): (Double, Double) -> Offset {
    val project = Routes.projector(route, width, height, padding)
    return { lat, lon -> project(lat, lon).let { (x, y) -> Offset(x, y) } }
}

private fun polyline(points: List<Offset>): Path = Path().apply {
    points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) }
}

private fun DrawScope.drawLabel(measurer: TextMeasurer, text: String, at: Offset, style: TextStyle) {
    val layout = measurer.measure(text, style)
    val x = (at.x - layout.size.width / 2f).coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f))
    val below = at.y + 10.dp.toPx()
    val y = if (below + layout.size.height <= size.height) below else at.y - 10.dp.toPx() - layout.size.height
    drawText(layout, topLeft = Offset(x, y.coerceAtLeast(0f)))
}

@Composable
private fun routeName(route: VirtualRoute): String = stringResource(R.string.route_name, route.stops.first().name, route.stops.last().name)

@Composable
private fun RoutePicker(settings: AppSettings, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val unit = stringResource(if (settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.route_choose)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.route_pick_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Routes.all.forEach { route ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(route.id) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            routeName(route),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (route.id == settings.routeId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            stringResource(
                                R.string.route_item,
                                format(Distance.inUnit(route.totalKm.toDouble(), settings.distanceUnit).roundToInt().toLong()),
                                unit,
                                route.stops.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        dismissButton = {
            if (settings.routeId.isNotEmpty()) {
                TextButton(onClick = { onPick(null) }) { Text(stringResource(R.string.route_stop)) }
            }
        },
    )
}
