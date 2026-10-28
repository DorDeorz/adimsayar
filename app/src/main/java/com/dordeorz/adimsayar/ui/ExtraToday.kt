package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.NO_GOAL
import com.dordeorz.adimsayar.data.PeriodGoals
import com.dordeorz.adimsayar.data.PeriodProgress
import java.time.LocalDate

@Composable
internal fun VacationCard(state: MainUiState) {
    if (!state.settings.inVacation(state.today)) return
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_flight),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(stringResource(R.string.vacation_on_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    stringResource(R.string.vacation_on_text, DAY_FORMAT.format(LocalDate.ofEpochDay(state.settings.vacationTo))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
    }
}

@Composable
internal fun PeriodGoalsCard(state: MainUiState) {
    val settings = state.settings
    if (settings.monthlyGoal == NO_GOAL && settings.yearlyGoal == NO_GOAL) return
    val month = remember(state.history, state.today, settings.monthlyGoal) {
        settings.monthlyGoal.takeIf { it != NO_GOAL }?.let { PeriodGoals.month(state.history, state.today, it) }
    }
    val year = remember(state.history, state.today, settings.yearlyGoal) {
        settings.yearlyGoal.takeIf { it != NO_GOAL }?.let { PeriodGoals.year(state.history, state.today, it) }
    }
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            month?.let { PeriodRow(stringResource(R.string.monthly_goal), it) }
            year?.let { PeriodRow(stringResource(R.string.yearly_goal), it) }
        }
    }
}

@Composable
private fun PeriodRow(title: String, progress: PeriodProgress) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                stringResource(R.string.period_value, format(progress.done), formatCompact(progress.goal)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { progress.fraction },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
        )
        val finish = progress.finish
        Text(
            when {
                progress.reached -> stringResource(R.string.period_reached)
                finish == null -> stringResource(R.string.period_no_pace)
                progress.onTrack -> stringResource(R.string.period_on_track, DAY_FORMAT.format(finish))
                else -> stringResource(R.string.period_behind, format(perDayNeeded(progress)))
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (progress.onTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun perDayNeeded(progress: PeriodProgress): Long {
    val left = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), progress.end).coerceAtLeast(0L) + 1
    return (progress.goal - progress.done + left - 1) / left
}
