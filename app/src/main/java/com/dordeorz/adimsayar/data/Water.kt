package com.dordeorz.adimsayar.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.roundToInt

object Water {

    const val MAX_ML = 10_000
    private const val ML_PER_KG = 35
    private const val ROUNDING_ML = 250

    fun observe(context: Context): Flow<Map<LocalDate, Int>> =
        StepDatabase.get(context).water().observeAll().map { rows -> rows.associate { LocalDate.parse(it.date) to it.ml } }

    suspend fun today(context: Context): Int = StepDatabase.get(context).water().get(LocalDate.now().toString())?.ml ?: 0

    suspend fun add(context: Context, date: LocalDate, ml: Int): Int {
        val dao = StepDatabase.get(context).water()
        val next = ((dao.get(date.toString())?.ml ?: 0) + ml).coerceIn(0, MAX_ML)
        dao.upsert(WaterDay(date.toString(), next))
        return next
    }

    fun suggestedGoal(weightKg: Int): Int =
        ((weightKg * ML_PER_KG / ROUNDING_ML.toDouble()).roundToInt() * ROUNDING_ML).coerceIn(WATER_GOAL_RANGE)
}
