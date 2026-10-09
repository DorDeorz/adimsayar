package com.dordeorz.adimsayar.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.data.StepRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LiveStepMonitor(context: Context) {

    private val manager = StepSensors.sensorManager(context)
    private val counter = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val detector = manager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val repository = StepRepository.get(context)
    private val freezeDetector = FreezeDetector()
    private val _frozen = MutableStateFlow(false)
    private var running = false
    private var lastCounterValue: Long? = null

    val frozen: StateFlow<Boolean> = _frozen.asStateFlow()

    private val counterListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val value = event.values[0].toLong()
            freezeDetector.onCounter(value, SystemClock.elapsedRealtime())
            val previous = lastCounterValue
            if (previous != null && value > previous) _frozen.value = false
            lastCounterValue = value
            AppScope.launch { repository.record(StepSensors.reading(value)) }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    private val detectorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val now = SystemClock.elapsedRealtime()
            freezeDetector.onDetectorStep(now)
            if (freezeDetector.isFrozen(now)) {
                _frozen.value = true
                reconnectCounter(now)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    fun start() {
        if (running || manager == null || counter == null) return
        running = true
        freezeDetector.reset(SystemClock.elapsedRealtime())
        manager.registerListener(counterListener, counter, SensorManager.SENSOR_DELAY_UI)
        if (detector != null) manager.registerListener(detectorListener, detector, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        if (!running || manager == null) return
        running = false
        manager.unregisterListener(counterListener)
        manager.unregisterListener(detectorListener)
    }

    private fun reconnectCounter(now: Long) {
        if (manager == null || counter == null) return
        manager.unregisterListener(counterListener)
        freezeDetector.reset(now)
        manager.registerListener(counterListener, counter, SensorManager.SENSOR_DELAY_UI)
    }
}
