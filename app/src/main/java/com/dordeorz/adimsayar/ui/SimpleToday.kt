package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dordeorz.adimsayar.R

/** The simple view: only today's steps and the goal, in big type. */
@Composable
internal fun SimpleTodayScreen(state: MainUiState, actions: MainActions) {
    val steps = state.week.lastOrNull()?.steps ?: 0L
    val goal = state.settings.goalOn(state.today)
    val done = steps >= goal
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.simple_today), style = MaterialTheme.typography.headlineSmall)
        Text(
            format(steps),
            fontSize = 88.sp,
            fontWeight = FontWeight.Bold,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Text(stringResource(R.string.steps_unit), style = MaterialTheme.typography.headlineSmall)
        LinearProgressIndicator(
            progress = { (steps.toFloat() / goal).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp).height(16.dp).clip(RoundedCornerShape(8.dp)),
        )
        Text(
            if (done) stringResource(R.string.goal_reached) else stringResource(R.string.goal_remaining, format(goal - steps)),
            fontSize = 28.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.goal_short, format(goal)),
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedButton(
            onClick = { actions.extra.update { it.copy(simpleView = false) } },
            modifier = Modifier.padding(top = 40.dp),
        ) { Text(stringResource(R.string.simple_view_off)) }
    }
}
