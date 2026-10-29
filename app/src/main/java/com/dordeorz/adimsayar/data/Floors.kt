package com.dordeorz.adimsayar.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Floors climbed, from the air pressure sensor. Phones without one never show floors. */
object Floors {

    fun hasSensor(context: Context): Boolean =
        context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    fun observe(context: Context): Flow<Map<LocalDate, Int>> =
        StepDatabase.get(context).extras().observeFloors().map { rows -> rows.associate { LocalDate.parse(it.date) to it.floors } }

    suspend fun add(context: Context, date: LocalDate, floors: Int) {
        if (floors <= 0) return
        val dao = StepDatabase.get(context).extras()
        val current = dao.floors(date.toString())?.floors ?: 0
        dao.upsertFloors(FloorDay(date.toString(), current + floors))
    }
}
