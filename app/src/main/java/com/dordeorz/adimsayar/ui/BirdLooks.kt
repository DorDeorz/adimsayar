package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BirdColor
import com.dordeorz.adimsayar.data.BirdSound
import com.dordeorz.adimsayar.data.BirdSpecies
import com.dordeorz.adimsayar.data.BirdState
import com.dordeorz.adimsayar.data.Seeds

private const val FEED_COST = 1L

/** Seeds and the feed button: one seed for every 1,000 steps. */
@Composable
internal fun FeedRow(state: MainUiState, actions: MainActions, onFed: () -> Unit) {
    val extras = state.extra.extras
    val seeds = Seeds.available(state.achievements.records.total, extras)
    var fedNow by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.seeds_count, seeds), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(if (fedNow) R.string.bird_fed else R.string.seeds_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (fedNow) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalButton(
            onClick = {
                actions.extra.update { it.copy(seedsSpent = it.seedsSpent + FEED_COST, fedOn = state.today.toEpochDay()) }
                BirdSong.play(BirdSound.entries.firstOrNull { it.name == extras.birdSound } ?: BirdSound.Chirp)
                fedNow = true
                onFed()
            },
            enabled = seeds >= FEED_COST,
        ) { Text(stringResource(R.string.bird_feed)) }
    }
}

/** Kind, feather colour and song; colours and songs are bought with seeds. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BirdLooks(bird: BirdState, state: MainUiState, actions: MainActions) {
    val extras = state.extra.extras
    val seeds = Seeds.available(state.achievements.records.total, extras)
    val owned = Seeds.owned(extras)
    Text(stringResource(R.string.bird_species_title), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BirdSpecies.entries.forEach { species ->
            val open = bird.level >= species.minLevel
            FilterChip(
                selected = bird.species == species,
                onClick = { actions.extra.update { it.copy(birdSpecies = species.name) } },
                enabled = open,
                label = {
                    Text(
                        if (open) stringResource(species.title()) else stringResource(R.string.bird_locked_level, stringResource(species.title()), species.minLevel),
                    )
                },
            )
        }
    }
    Text(stringResource(R.string.bird_color_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BirdColor.entries.forEach { color ->
            val have = color.name in owned
            FilterChip(
                selected = bird.color == color,
                onClick = {
                    actions.extra.update {
                        when {
                            have -> it.copy(birdColor = color.name)
                            seeds >= color.cost -> it.copy(
                                birdColor = color.name,
                                unlockedLooks = it.unlockedLooks + color.name,
                                seedsSpent = it.seedsSpent + color.cost,
                            )
                            else -> it
                        }
                    }
                },
                enabled = have || seeds >= color.cost,
                label = { Text(if (have) stringResource(color.title()) else stringResource(R.string.bird_price, stringResource(color.title()), color.cost)) },
            )
        }
    }
    Text(stringResource(R.string.bird_sound_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BirdSound.entries.forEach { sound ->
            val have = sound.name in owned
            FilterChip(
                selected = extras.birdSound == sound.name,
                onClick = {
                    if (have || seeds >= sound.cost) {
                        actions.extra.update {
                            if (have) {
                                it.copy(birdSound = sound.name)
                            } else {
                                it.copy(birdSound = sound.name, unlockedLooks = it.unlockedLooks + sound.name, seedsSpent = it.seedsSpent + sound.cost)
                            }
                        }
                        BirdSong.play(sound)
                    }
                },
                enabled = have || seeds >= sound.cost,
                label = { Text(if (have) stringResource(sound.title()) else stringResource(R.string.bird_price, stringResource(sound.title()), sound.cost)) },
            )
        }
    }
}

internal fun BirdSpecies.title(): Int = when (this) {
    BirdSpecies.Sparrow -> R.string.bird_species_sparrow
    BirdSpecies.Owl -> R.string.bird_species_owl
    BirdSpecies.Parrot -> R.string.bird_species_parrot
    BirdSpecies.Eagle -> R.string.bird_species_eagle
}

internal fun BirdColor.title(): Int = when (this) {
    BirdColor.Default -> R.string.bird_color_default
    BirdColor.Sky -> R.string.bird_color_sky
    BirdColor.Rose -> R.string.bird_color_rose
    BirdColor.Mint -> R.string.bird_color_mint
    BirdColor.Plum -> R.string.bird_color_plum
    BirdColor.Night -> R.string.bird_color_night
}

internal fun BirdSound.title(): Int = when (this) {
    BirdSound.Chirp -> R.string.bird_sound_chirp
    BirdSound.Trill -> R.string.bird_sound_trill
    BirdSound.Tweet -> R.string.bird_sound_tweet
    BirdSound.Coo -> R.string.bird_sound_coo
}
