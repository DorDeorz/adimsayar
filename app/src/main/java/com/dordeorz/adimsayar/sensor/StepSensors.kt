package com.dordeorz.adimsayar.sensor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.dordeorz.adimsayar.data.CounterReading
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

object StepSensors {

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun sensorManager(context: Context): SensorManager? = context.getSystemService(SensorManager::class.java)

    fun hasCounter(context: Context): Boolean = sensorManager(context)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun reading(context: Context, event: SensorEvent): CounterReading {
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val wallNowMs = System.currentTimeMillis()
        val eventNs = if (event.timestamp in 1..nowNs) event.timestamp else nowNs
        return CounterReading(
            counter = event.values[0].toLong(),
            elapsedMs = eventNs / 1_000_000,
            wallMs = wallNowMs - (nowNs - eventNs) / 1_000_000,
            bootCount = bootCount(context),
        )
    }

    private fun bootCount(context: Context): Int? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }

    suspend fun readCounterOnce(context: Context, timeoutMs: Long): CounterReading? {
        if (!hasPermission(context)) return null
        val manager = sensorManager(context) ?: return null
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<CounterReading?> { continuation ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        manager.unregisterListener(this)
                        if (continuation.isActive) continuation.resume(reading(context, event))
                    }

                    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
                }
                continuation.invokeOnCancellation { manager.unregisterListener(listener) }
                if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
                    continuation.resume(null)
                }
            }
        }
    }
}
