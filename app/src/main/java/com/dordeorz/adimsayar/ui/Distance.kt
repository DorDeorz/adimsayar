package com.dordeorz.adimsayar.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.AppSettings
import com.dordeorz.adimsayar.data.Distance
import com.dordeorz.adimsayar.data.DistanceUnit

@Composable
internal fun distanceAndCalories(steps: Long, settings: AppSettings): String {
    val distance = Distance.inUnit(Distance.km(steps, settings.heightCm), settings.distanceUnit)
    val unit = stringResource(if (settings.distanceUnit == DistanceUnit.Mile) R.string.unit_mile else R.string.unit_km)
    val kcal = Distance.kcal(steps, settings.heightCm, settings.weightKg)
    return stringResource(R.string.distance_calories, formatDecimal(distance), unit, format(kcal))
}
