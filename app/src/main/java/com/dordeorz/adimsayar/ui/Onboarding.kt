package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BirdMood
import com.dordeorz.adimsayar.data.BirdStage
import com.dordeorz.adimsayar.data.BirdState
import com.dordeorz.adimsayar.data.HEIGHT_RANGE
import com.dordeorz.adimsayar.data.WEIGHT_RANGE
import kotlinx.coroutines.launch

private val GOAL_CHOICES = listOf(4_000L, 6_000L, 8_000L, 10_000L, 12_000L, 15_000L)
private const val PAGES = 4

/** The first-run welcome: what the app does, the daily goal, height and weight, and the step permission. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingScreen(state: MainUiState, actions: MainActions) {
    val pager = rememberPagerState { PAGES }
    val scope = rememberCoroutineScope()
    val finish = { actions.extra.update { it.copy(onboardingDone = true) } }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = finish) { Text(stringResource(R.string.onboarding_skip)) }
            }
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    when (page) {
                        0 -> {
                            val bird = remember { BirdState(BirdStage.Chick, BirdMood.Happy, emptySet(), BirdStage.Fledgling, 0L, 0.5f) }
                            BirdImage(bird, Modifier.size(160.dp))
                            PageText(stringResource(R.string.onboarding_welcome_title), stringResource(R.string.onboarding_welcome_text))
                        }
                        1 -> {
                            PageText(stringResource(R.string.onboarding_goal_title), stringResource(R.string.onboarding_goal_text))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                                GOAL_CHOICES.forEach { goal ->
                                    FilterChip(
                                        selected = state.settings.dailyGoal == goal,
                                        onClick = { actions.onDailyGoalChange(goal) },
                                        label = { Text(format(goal)) },
                                    )
                                }
                            }
                        }
                        2 -> {
                            PageText(stringResource(R.string.onboarding_body_title), stringResource(R.string.onboarding_body_text))
                            Stepper(stringResource(R.string.onboarding_height, state.settings.heightCm)) { delta ->
                                actions.onHeightChange((state.settings.heightCm + delta).coerceIn(HEIGHT_RANGE))
                            }
                            Stepper(stringResource(R.string.onboarding_weight, state.settings.weightKg)) { delta ->
                                actions.onWeightChange((state.settings.weightKg + delta).coerceIn(WEIGHT_RANGE))
                            }
                        }
                        else -> {
                            PageText(stringResource(R.string.onboarding_permission_title), stringResource(R.string.onboarding_permission_text))
                            if (state.permission == PermissionState.Granted) {
                                Text(stringResource(R.string.onboarding_permission_done), color = MaterialTheme.colorScheme.primary)
                            } else {
                                FilledTonalButton(onClick = actions.onRequestPermission) { Text(stringResource(R.string.onboarding_permission_button)) }
                            }
                        }
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    repeat(PAGES) { index ->
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (index == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    CircleShape,
                                ),
                        )
                    }
                }
                if (pager.currentPage < PAGES - 1) {
                    Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                        Text(stringResource(R.string.onboarding_next))
                    }
                } else {
                    Button(onClick = finish) { Text(stringResource(R.string.onboarding_start)) }
                }
            }
        }
    }
}

@Composable
private fun PageText(title: String, text: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

@Composable
private fun Stepper(label: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = { onChange(-1) }) { Text("−") }
        Text(label, style = MaterialTheme.typography.titleLarge)
        OutlinedButton(onClick = { onChange(1) }) { Text("+") }
    }
}
