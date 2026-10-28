package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BPM_RANGE
import com.dordeorz.adimsayar.data.Walk
import com.dordeorz.adimsayar.data.Walks
import com.dordeorz.adimsayar.walk.DogWalk
import com.dordeorz.adimsayar.walk.MetronomeService
import com.dordeorz.adimsayar.walk.WalkRecordService
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

private const val BPM_STEP = 5
private const val TICK_MS = 1_000L

/** Tools for a walk: the pace metronome, a dog walk and a GPS-recorded walk. */
@Composable
internal fun WalkToolsCard(state: MainUiState, actions: MainActions) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DogWalk.load(context) }
    val metronome by MetronomeService.running.collectAsState()
    val dogStart by DogWalk.started.collectAsState()
    val gps by WalkRecordService.session.collectAsState()
    val steps = state.week.lastOrNull()?.steps ?: 0L
    val now by produceState(System.currentTimeMillis(), dogStart, gps) {
        while (dogStart != null || gps != null) {
            value = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    var walksOpen by rememberSaveable { mutableStateOf(false) }
    var bpm by remember(state.extra.extras.metronomeBpm) { mutableStateOf(state.extra.extras.metronomeBpm) }
    Card {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                stringResource(R.string.walk_tools_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ToolRow(R.drawable.ic_music, stringResource(R.string.metronome_title), stringResource(R.string.metronome_bpm, metronome ?: bpm)) {
                IconButton(onClick = { bpm = (bpm - BPM_STEP).coerceIn(BPM_RANGE); if (metronome != null) actions.extra.startMetronome(bpm) }) {
                    Text("−", style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = { bpm = (bpm + BPM_STEP).coerceIn(BPM_RANGE); if (metronome != null) actions.extra.startMetronome(bpm) }) {
                    Text("+", style = MaterialTheme.typography.titleLarge)
                }
                PlayStop(metronome != null, onStart = { actions.extra.startMetronome(bpm) }, onStop = actions.extra::stopMetronome)
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            val dog = dogStart
            ToolRow(
                R.drawable.ic_pets,
                stringResource(R.string.walk_dog_title),
                if (dog == null) stringResource(R.string.walk_dog_hint) else stringResource(R.string.walk_running, elapsed(now - dog)),
            ) {
                PlayStop(dog != null, onStart = { actions.extra.startDogWalk(steps) }, onStop = { actions.extra.stopDogWalk(steps) })
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            val session = gps
            ToolRow(
                R.drawable.ic_place,
                stringResource(R.string.walk_gps_title),
                if (session == null) {
                    stringResource(R.string.walk_gps_hint)
                } else {
                    stringResource(R.string.walk_gps_running, formatDecimal(session.meters / 1_000.0), elapsed(now - session.start))
                },
            ) {
                PlayStop(session != null, onStart = { actions.extra.startGpsWalk(steps) }, onStop = actions.extra::stopGpsWalk)
            }
            if (state.extra.walks.isNotEmpty()) {
                TextButton(onClick = { walksOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.walk_list_button, state.extra.walks.size))
                }
            }
        }
    }
    if (walksOpen) WalksDialog(state.extra.walks, actions) { walksOpen = false }
}

@Composable
private fun ToolRow(icon: Int, title: String, text: String, buttons: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        buttons()
    }
}

@Composable
private fun PlayStop(running: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    FilledTonalButton(onClick = if (running) onStop else onStart) {
        Icon(
            painterResource(if (running) R.drawable.ic_stop else R.drawable.ic_play),
            contentDescription = stringResource(if (running) R.string.walk_stop else R.string.walk_start),
            modifier = Modifier.size(18.dp),
        )
    }
}

private fun elapsed(ms: Long): String {
    val seconds = (ms / 1_000L).coerceAtLeast(0L)
    return if (seconds >= 3_600L) {
        String.format(appLocale, "%d:%02d:%02d", seconds / 3_600L, seconds / 60L % 60L, seconds % 60L)
    } else {
        String.format(appLocale, "%d:%02d", seconds / 60L, seconds % 60L)
    }
}

@Composable
private fun WalksDialog(walks: List<Walk>, actions: MainActions, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.walk_list_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                walks.sortedByDescending { it.start }.forEach { walk ->
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(if (walk.kind == Walks.KIND_DOG) R.drawable.ic_pets else R.drawable.ic_place),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                                Text(
                                    TIME_FORMAT.format(Instant.ofEpochMilli(walk.start).atZone(ZoneId.systemDefault())),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    walkSummary(walk),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { actions.extra.deleteWalk(walk.id) }) {
                                Icon(painterResource(R.drawable.ic_delete), contentDescription = stringResource(R.string.walk_delete))
                            }
                        }
                        walk.path?.let { RouteShape(it) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun walkSummary(walk: Walk): String {
    val time = elapsed(walk.end - walk.start)
    return if (walk.kind == Walks.KIND_GPS) {
        stringResource(R.string.walk_summary_gps, formatDecimal(walk.meters / 1_000.0), time, format(walk.steps))
    } else {
        stringResource(R.string.walk_summary_dog, time, format(walk.steps))
    }
}

/** The recorded route as a line, scaled to fit; latitude and longitude are close enough to flat for a walk. */
@Composable
private fun RouteShape(path: String) {
    val points = remember(path) {
        path.split(';').mapNotNull { part ->
            val lat = part.substringBefore(',').toDoubleOrNull() ?: return@mapNotNull null
            val lon = part.substringAfter(',').toDoubleOrNull() ?: return@mapNotNull null
            lat to lon
        }
    }
    if (points.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(120.dp).padding(top = 6.dp)) {
        val minLat = points.minOf { it.first }
        val maxLat = points.maxOf { it.first }
        val minLon = points.minOf { it.second }
        val maxLon = points.maxOf { it.second }
        val cos = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2))
        val spanX = ((maxLon - minLon) * cos).coerceAtLeast(1e-6)
        val spanY = (maxLat - minLat).coerceAtLeast(1e-6)
        val scale = minOf(size.width / spanX, size.height / spanY).toFloat() * 0.9f
        val offsetX = (size.width - spanX.toFloat() * scale) / 2f
        val offsetY = (size.height - spanY.toFloat() * scale) / 2f
        fun at(point: Pair<Double, Double>) = Offset(
            offsetX + ((point.second - minLon) * cos).toFloat() * scale,
            offsetY + (maxLat - point.first).toFloat() * scale,
        )
        val line = Path()
        points.forEachIndexed { index, point ->
            val o = at(point)
            if (index == 0) line.moveTo(o.x, o.y) else line.lineTo(o.x, o.y)
        }
        drawPath(line, color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 4.dp.toPx(), at(points.first()))
        drawCircle(color.copy(alpha = 0.5f), 4.dp.toPx(), at(points.last()))
    }
}
