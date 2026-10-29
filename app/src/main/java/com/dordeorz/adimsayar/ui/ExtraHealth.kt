package com.dordeorz.adimsayar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BandPeriod
import com.dordeorz.adimsayar.data.HealthMath
import java.time.LocalDate
import kotlin.math.roundToInt

/** Fitness age from resting heart rate (Uth's VO2max estimate) and the daily steps of the last 30 days. */
object FitnessAge {

    private const val MIN_AGE = 18
    private const val MAX_AGE = 90
    private const val STEP_BASE = 7_500.0
    private const val STEPS_PER_POINT = 2_500.0
    private const val MAX_STEP_POINTS = 3.0

    fun vo2max(age: Int, resting: Long, averageSteps: Long): Double {
        val maxHeart = 208.0 - 0.7 * age
        val fromHeart = 15.3 * maxHeart / resting
        val fromSteps = ((averageSteps - STEP_BASE) / STEPS_PER_POINT).coerceIn(-MAX_STEP_POINTS, MAX_STEP_POINTS)
        return fromHeart + fromSteps
    }

    /** The age whose average VO2max matches: the norm falls about 0.32 a year from 52. */
    fun age(vo2max: Double): Int = ((52.0 - vo2max) / 0.32).roundToInt().coerceIn(MIN_AGE, MAX_AGE)
}

/** Recovery from last night's sleep against the week before, and resting heart rate against its average. */
object Recovery {

    private const val BASE = 60.0
    private const val MAX_PART = 20.0
    private const val POINTS_PER_BPM = 4.0

    fun score(lastSleep: Long?, averageSleep: Long?, lastResting: Long?, averageResting: Long?): Int? {
        if (lastSleep == null && lastResting == null) return null
        val sleep = if (lastSleep != null && averageSleep != null && averageSleep > 0L) {
            ((lastSleep.toDouble() / averageSleep - 1.0) * 100.0).coerceIn(-MAX_PART, MAX_PART)
        } else {
            0.0
        }
        val heart = if (lastResting != null && averageResting != null) {
            ((averageResting - lastResting) * POINTS_PER_BPM).coerceIn(-MAX_PART, MAX_PART)
        } else {
            0.0
        }
        return (BASE + sleep + heart).roundToInt().coerceIn(0, 100)
    }
}

@Composable
internal fun FitnessAgeCard(data: BandPeriod, state: MainUiState) {
    val resting = HealthMath.average(data.resting.mapNotNull { it.second }) ?: return
    val age = state.settings.age
    val averageSteps = remember(state.history, state.today) {
        val from = state.today.minusDays(30)
        val days = state.history.filter { (date, steps) -> date.isAfter(from) && date.isBefore(state.today) && steps > 0L }
        if (days.isEmpty()) 0L else days.values.sum() / days.size
    }
    val vo2 = FitnessAge.vo2max(age, resting, averageSteps)
    val fitnessAge = FitnessAge.age(vo2)
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.fitness_age_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.fitness_age_value, fitnessAge),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                when {
                    fitnessAge < age -> stringResource(R.string.fitness_age_younger, age - fitnessAge)
                    fitnessAge > age -> stringResource(R.string.fitness_age_older, fitnessAge - age)
                    else -> stringResource(R.string.fitness_age_same)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.fitness_age_text, vo2.roundToInt(), resting, format(averageSteps)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun RecoveryCard(data: BandPeriod, today: LocalDate) {
    val lastNightDate = listOf(today, today.minusDays(1)).firstOrNull { it in data.nights }
    val lastSleep = lastNightDate?.let { data.nights[it]?.minutes }
    val averageSleep = HealthMath.average(
        data.nights.filterKeys { it != lastNightDate && it.isAfter(today.minusDays(8)) }.values.map { it.minutes },
    )
    val restingDays = data.resting.mapNotNull { (date, bpm) -> bpm?.let { date to it } }.sortedBy { it.first }
    val lastResting = restingDays.lastOrNull()?.takeIf { !it.first.isBefore(today.minusDays(1)) }
    val averageResting = HealthMath.average(restingDays.filter { it != lastResting }.map { it.second })
    val score = Recovery.score(lastSleep, averageSleep, lastResting?.second, averageResting) ?: return
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.recovery_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.recovery_value, score),
                style = MaterialTheme.typography.headlineMedium,
                color = if (score >= 40) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            LinearProgressIndicator(
                progress = { score / 100f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
            )
            Text(
                stringResource(
                    when {
                        score >= 70 -> R.string.recovery_good
                        score >= 40 -> R.string.recovery_mid
                        else -> R.string.recovery_low
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                stringResource(R.string.recovery_text),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
