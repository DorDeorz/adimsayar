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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

private val BIRD_SIZE = 104.dp
private val BIG_BIRD_SIZE = 200.dp

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
    Canvas(modifier = modifier) {
        drawIntoCanvas { BirdPainter.draw(it.nativeCanvas, size.minDimension, bird) }
    }
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
                Text(stringResource(bird.mood.text()), style = MaterialTheme.typography.bodyLarge)
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
