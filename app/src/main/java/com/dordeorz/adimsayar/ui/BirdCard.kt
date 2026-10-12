package com.dordeorz.adimsayar.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.Avatar
import com.dordeorz.adimsayar.data.BirdItem
import com.dordeorz.adimsayar.data.BirdState
import java.time.LocalTime
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BIRD_SIZE = 104.dp
private val BIG_BIRD_SIZE = 200.dp
private const val BLINK_EVERY_MS = 3_000L
private const val BLINK_JITTER_MS = 2_500L
private const val BLINK_MS = 140L
private const val HOP_UP_MS = 200
private const val FLAP_MS = 70
private const val FLAPS = 3

@Composable
internal fun BirdCard(state: MainUiState) {
    var open by rememberSaveable { mutableStateOf(false) }
    val hour = LocalTime.now().hour
    val bird = remember(state.achievements, state.history, state.freezes, state.today, state.settings.dailyGoal, hour) {
        Avatar.state(state.achievements, state.history, state.freezes, state.today, hour, state.settings.dailyGoal)
    }
    Card(modifier = Modifier.fillMaxWidth().clickable { open = true }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            BirdImage(bird, Modifier.size(BIRD_SIZE))
            Column(modifier = Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(bird.stage.title()), style = MaterialTheme.typography.titleMedium)
                LevelLine(bird)
                Text(stringResource(bird.mood.text()), style = MaterialTheme.typography.bodyMedium)
                StageProgress(bird)
                Text(
                    stringResource(R.string.bird_items, bird.items.size, BirdItem.entries.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    if (open) BirdDialog(bird) { open = false }
}

@Composable
internal fun BirdImage(bird: BirdState, modifier: Modifier) {
    val hop = remember { Animatable(0f) }
    val flap = remember { Animatable(0f) }
    var blink by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(bird.stage) {
        while (true) {
            delay(BLINK_EVERY_MS + Random.nextLong(BLINK_JITTER_MS))
            blink = true
            delay(BLINK_MS)
            blink = false
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Canvas(
        modifier = modifier.clickable(interactionSource = interaction, indication = null) {
            scope.launch {
                hop.animateTo(1f, tween(HOP_UP_MS, easing = FastOutSlowInEasing))
                hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
            }
            scope.launch {
                repeat(FLAPS) {
                    flap.animateTo(1f, tween(FLAP_MS, easing = FastOutSlowInEasing))
                    flap.animateTo(-1f, tween(FLAP_MS * 2, easing = FastOutSlowInEasing))
                }
                flap.animateTo(0f, tween(FLAP_MS, easing = FastOutSlowInEasing))
            }
        },
    ) {
        val pose = BirdPose(hop.value, flap.value, blink)
        drawIntoCanvas { BirdPainter.draw(it.nativeCanvas, size.minDimension, bird, pose) }
    }
}

@Composable
private fun LevelLine(bird: BirdState) {
    Text(
        if (bird.leveledUpToday) stringResource(R.string.bird_level_up) else stringResource(R.string.bird_level, bird.level),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun StageProgress(bird: BirdState) {
    val next = bird.nextStage
    LinearProgressIndicator(
        progress = { bird.stageFraction },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
    )
    Text(
        if (next == null) {
            stringResource(R.string.bird_last)
        } else {
            stringResource(R.string.bird_next, stringResource(next.title()), format(bird.stepsToNext))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun BirdDialog(bird: BirdState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(bird.stage.title())) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BirdImage(bird, Modifier.size(BIG_BIRD_SIZE).align(Alignment.CenterHorizontally))
                LevelLine(bird)
                Text(stringResource(bird.mood.text()), style = MaterialTheme.typography.bodyLarge)
                LinearProgressIndicator(
                    progress = { bird.levelFraction },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                )
                Text(
                    stringResource(R.string.bird_level_next, bird.level + 1, format(bird.stepsToNextLevel)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StageProgress(bird)
                Text(stringResource(R.string.bird_items_title), style = MaterialTheme.typography.titleSmall)
                BirdItem.entries.forEach { item ->
                    val owned = item in bird.items
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(item.title()),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (owned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(item.unlock()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (owned) {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                Text(
                    stringResource(R.string.bird_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
