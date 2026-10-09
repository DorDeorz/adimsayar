package com.dordeorz.adimsayar.background

import android.content.Context
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.data.ReadingLog
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.sensor.StepSensors
import com.dordeorz.adimsayar.widget.Widgets

object StepUpdater {

    suspend fun refresh(context: Context, timeoutMs: Long, source: ReadSource) {
        val log = ReadingLog.get(context)
        when {
            !StepSensors.hasPermission(context) -> log.failure(source, R.string.log_no_permission)
            !StepSensors.hasCounter(context) -> log.failure(source, R.string.log_no_sensor)
            else -> {
                val reading = StepSensors.readCounterOnce(context, timeoutMs)
                if (reading == null) {
                    log.failure(source, R.string.log_timeout)
                } else {
                    log.reading(source, reading, StepRepository.get(context).record(reading))
                }
            }
        }
        Widgets.updateAll(context)
    }
}
