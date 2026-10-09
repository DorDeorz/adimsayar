package com.dordeorz.adimsayar.background

import android.content.Context
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.sensor.StepSensors
import com.dordeorz.adimsayar.widget.Widgets

object StepUpdater {

    suspend fun refresh(context: Context, timeoutMs: Long) {
        StepSensors.readCounterOnce(context, timeoutMs)?.let { StepRepository.get(context).record(it) }
        Widgets.updateAll(context)
    }
}
