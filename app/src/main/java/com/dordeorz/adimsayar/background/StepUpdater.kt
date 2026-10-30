package com.dordeorz.adimsayar.background

import android.content.Context
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.BandDirect
import com.dordeorz.adimsayar.data.BandLive
import com.dordeorz.adimsayar.data.HealthWrite
import com.dordeorz.adimsayar.data.StepPace
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.data.ReadingLog
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.WearableSteps
import com.dordeorz.adimsayar.data.XiaomiSteps
import com.dordeorz.adimsayar.sensor.StepSensors
import com.dordeorz.adimsayar.widget.Widgets

object StepUpdater {

    /**
     * Reads the phone's counter (waiting at most [sensorTimeoutMs]) and updates the widgets, then
     * syncs the band and Health Connect unless [phoneOnly]. The band can take a while, so the
     * phone's steps reach the widgets first.
     */
    suspend fun refresh(context: Context, sensorTimeoutMs: Long, source: ReadSource, phoneOnly: Boolean = false) {
        val log = ReadingLog.get(context)
        when {
            XiaomiSteps.isEnabled(context) -> XiaomiSteps.sync(context, source)
            !StepSensors.hasPermission(context) -> log.failure(source, R.string.log_no_permission)
            !StepSensors.hasCounter(context) -> log.failure(source, R.string.log_no_sensor)
            else -> {
                val reading = StepSensors.readCounterOnce(context, sensorTimeoutMs)
                if (reading == null) {
                    log.failure(source, R.string.log_timeout)
                } else {
                    log.reading(source, reading, StepRepository.get(context).record(reading))
                }
            }
        }
        Widgets.updateAll(context)
        if (phoneOnly) return
        if (WearableSteps.isEnabled(context)) WearableSteps.sync(context, source)
        if (BandDirect.isEnabled(context)) {
            BandLive.refresh(context)
            BandDirect.sync(context, source)
        }
        HealthWrite.syncIfDue(context)
        ExtraReminders.recordCheck(context)
        StepPace.cadence(context)
        Widgets.updateAll(context)
    }
}
