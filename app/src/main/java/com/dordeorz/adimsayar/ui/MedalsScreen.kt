package com.dordeorz.adimsayar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.Level
import com.dordeorz.adimsayar.data.Medal
import com.dordeorz.adimsayar.data.MedalGroup
import com.dordeorz.adimsayar.ui.theme.MedalColors

private const val MEDALS_PER_ROW = 3

@StringRes
private fun MedalGroup.title(): Int = when (this) {
    MedalGroup.DailySteps -> R.string.medal_group_daily
    MedalGroup.Streak -> R.string.medal_group_streak
    MedalGroup.WeeklyGoal -> R.string.medal_group_weekly
    MedalGroup.Total -> R.string.medal_group_total
    MedalGroup.EarlyBird -> R.string.medal_group_early
}

@StringRes
private fun MedalGroup.description(): Int = when (this) {
    MedalGroup.DailySteps -> R.string.medal_group_daily_text
    MedalGroup.Streak -> R.string.medal_group_streak_text
    MedalGroup.WeeklyGoal -> R.string.medal_group_weekly_text
    MedalGroup.Total -> R.string.medal_group_total_text
    MedalGroup.EarlyBird -> R.string.medal_group_early_text
}

@Composable
fun MedalsScreen(state: MainUiState) {
    val medals = state.achievements.medals
    val groups = medals.groupBy { it.group }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            MedalSummary(
                level = state.achievements.level,
                total = state.achievements.records.total,
                earned = medals.count { it.earned },
                count = medals.size,
            )
        }
        items(groups.keys.toList()) { group -> MedalGroupCard(group, groups[group].orEmpty()) }
        item {
            Text(
                stringResource(R.string.medals_retroactive),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MedalSummary(level: Level, total: Long, earned: Int, count: Int) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            ProgressRing(
                fraction = (total - level.from).toFloat() / (level.to - level.from),
                stroke = 10.dp,
                modifier = Modifier.fillMaxSize(),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.level),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(level.number.toString(), style = MaterialTheme.typography.displayLarge)
            }
        }
        Text(
            stringResource(R.string.level_next, format(level.to - total)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_trophy),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(stringResource(R.string.medals_earned, earned, count), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MedalGroupCard(group: MedalGroup, medals: List<Medal>) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(stringResource(group.title()), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(group.description()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            medals.chunked(MEDALS_PER_ROW).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEach { medal -> MedalBadge(medal, Modifier.weight(1f)) }
                    repeat(MEDALS_PER_ROW - row.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun MedalBadge(medal: Medal, modifier: Modifier) {
    val color = MedalColors[medal.tier % MedalColors.size]
    val muted = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (medal.earned) {
                        Modifier.background(color.copy(alpha = 0.18f), CircleShape).border(3.dp, color, CircleShape)
                    } else {
                        Modifier.border(2.dp, muted, CircleShape)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_trophy),
                contentDescription = null,
                tint = if (medal.earned) color else muted,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            targetLabel(medal),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = medal.earnedOn?.let { DAY_FORMAT.format(it) + " " + it.year }
                ?: stringResource(R.string.percent_of_goal, percent(medal.progress, medal.target).coerceAtMost(99)),
            style = MaterialTheme.typography.labelSmall,
            color = if (medal.earned) MaterialTheme.colorScheme.onSurfaceVariant else muted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun targetLabel(medal: Medal): String = when (medal.group) {
    MedalGroup.DailySteps -> stringResource(R.string.steps_value, format(medal.target))
    MedalGroup.Streak, MedalGroup.EarlyBird -> stringResource(R.string.days_count, medal.target.toInt())
    MedalGroup.WeeklyGoal -> stringResource(R.string.weeks_count, medal.target.toInt())
    MedalGroup.Total -> if (medal.target >= 1_000_000L) {
        stringResource(R.string.million_steps, formatMillions(medal.target))
    } else {
        stringResource(R.string.steps_value, format(medal.target))
    }
}

private fun formatMillions(steps: Long): String {
    val millions = steps / 1_000_000.0
    return if (steps % 1_000_000L == 0L) (steps / 1_000_000L).toString() else String.format(appLocale, "%.1f", millions)
}
