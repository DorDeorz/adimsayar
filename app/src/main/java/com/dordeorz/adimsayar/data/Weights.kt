package com.dordeorz.adimsayar.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import kotlin.math.roundToInt

class Weight(val date: LocalDate, val kg: Double)

object Weights {

    val KG_RANGE = WEIGHT_RANGE.first.toDouble()..WEIGHT_RANGE.last.toDouble()

    fun observe(context: Context): Flow<List<Weight>> =
        StepDatabase.get(context).weights().observeAll().map { list -> list.map { Weight(LocalDate.parse(it.date), it.kg) } }

    suspend fun add(context: Context, date: LocalDate, kg: Double) {
        val dao = StepDatabase.get(context).weights()
        dao.upsert(WeightEntry(date.toString(), kg.coerceIn(KG_RANGE)))
        syncSetting(context)
    }

    suspend fun delete(context: Context, date: LocalDate) {
        StepDatabase.get(context).weights().delete(date.toString())
        syncSetting(context)
    }

    private suspend fun syncSetting(context: Context) {
        val latest = StepDatabase.get(context).weights().latest() ?: return
        SettingsStore.get(context).setWeight(latest.kg.roundToInt())
    }
}
