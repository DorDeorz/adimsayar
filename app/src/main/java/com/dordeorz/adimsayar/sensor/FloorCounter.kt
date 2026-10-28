package com.dordeorz.adimsayar.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.data.Floors
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Counts floors from the air pressure sensor while the app, the background counter or a walk
 * recording is running. A floor is a 3 m rise within two minutes while steps are being taken, so a
 * lift or the weather does not count. Phones without the sensor do nothing.
 */
object FloorCounter {

    private const val FLOOR_METERS = 3.0
    private const val SMOOTHING = 0.1
    private const val CLIMB_WINDOW_MS = 120_000L
    private const val WALKING_MS = 90_000L

    private val owners = HashSet<String>()
    private var context: Context? = null
    private var smooth = Double.NaN
    private var base = Double.NaN
    private var baseAt = 0L

    @Volatile
    private var lastStepMs = 0L

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = onPressure(event.values[0].toDouble(), SystemClock.elapsedRealtime())

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    @Synchronized
    fun start(context: Context, owner: String) {
        val app = context.applicationContext
        val manager = app.getSystemService(SensorManager::class.java) ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return
        if (owners.isEmpty()) {
            this.context = app
            smooth = Double.NaN
            base = Double.NaN
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
        owners += owner
    }

    @Synchronized
    fun stop(context: Context, owner: String) {
        if (!owners.remove(owner) || owners.isNotEmpty()) return
        context.applicationContext.getSystemService(SensorManager::class.java)?.unregisterListener(listener)
    }

    /** Called on step events, so climbing is told apart from a lift or the weather. */
    fun onStep() {
        lastStepMs = SystemClock.elapsedRealtime()
    }

    @Synchronized
    private fun onPressure(hPa: Double, now: Long) {
        val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hPa.toFloat()).toDouble()
        smooth = if (smooth.isNaN()) altitude else smooth + SMOOTHING * (altitude - smooth)
        if (base.isNaN() || smooth < base || now - baseAt > CLIMB_WINDOW_MS) {
            base = smooth
            baseAt = now
            return
        }
        if (smooth - base < FLOOR_METERS) return
        if (now - lastStepMs < WALKING_MS) {
            val app = context ?: return
            AppScope.launch { Floors.add(app, LocalDate.now(), 1) }
            base += FLOOR_METERS
        } else {
            base = smooth
        }
        baseAt = now
    }
}
