package com.dordeorz.adimsayar.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.data.ReadingLog
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.sensor.StepSensors
import com.dordeorz.adimsayar.widget.Widgets
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class StepCounterService : Service(), SensorEventListener {

    private var registered = false
    @Volatile
    private var lastLogMs = 0L

    @Volatile
    private var lastWidgetMs = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!StepSensors.hasPermission(this) || !showNotification(null)) {
            stopSelf()
            return START_NOT_STICKY
        }
        register()
        return START_STICKY
    }

    override fun onDestroy() {
        StepSensors.sensorManager(this)?.unregisterListener(this)
        registered = false
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val counter = event.values[0].toLong()
        val now = SystemClock.elapsedRealtime()
        val appContext = applicationContext
        AppScope.launch {
            val repository = StepRepository.get(appContext)
            val added = repository.record(StepSensors.reading(counter))
            showNotification(repository.today())
            if (lastLogMs == 0L || now - lastLogMs >= LOG_INTERVAL_MS) {
                lastLogMs = now
                ReadingLog.get(appContext).reading(ReadSource.Service, counter, added)
            }
            if (now - lastWidgetMs >= WIDGET_INTERVAL_MS) {
                lastWidgetMs = now
                Widgets.updateAll(appContext)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun register() {
        if (registered) return
        val manager = StepSensors.sensorManager(this) ?: return
        val counter = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        registered = manager.registerListener(this, counter, SensorManager.SENSOR_DELAY_NORMAL, MAX_REPORT_LATENCY_US)
        if (!registered) ReadingLog.get(this).failure(ReadSource.Service, R.string.log_register_failed)
    }

    private fun showNotification(today: Long?): Boolean =
        try {
            val notification = buildNotification(today)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            false
        }

    private fun buildNotification(today: Long?): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.service_channel), NotificationManager.IMPORTANCE_MIN)
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (today == null) {
            getString(R.string.service_text_starting)
        } else {
            getString(R.string.service_text, NumberFormat.getIntegerInstance(Locale.forLanguageTag("tr-TR")).format(today))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.service_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "sayac"
        private const val NOTIFICATION_ID = 1
        private const val MAX_REPORT_LATENCY_US = 60_000_000
        private const val LOG_INTERVAL_MS = 15 * 60 * 1000L
        private const val WIDGET_INTERVAL_MS = 5 * 60 * 1000L
        private const val SETTINGS_PREFS = "settings"
        private const val KEY_ENABLED = "background_service"

        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_ENABLED, enabled) }
            if (enabled) startIfEnabled(context) else context.stopService(Intent(context, StepCounterService::class.java))
        }

        fun startIfEnabled(context: Context) {
            if (!isEnabled(context) || !StepSensors.hasPermission(context) || !StepSensors.hasCounter(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java))
            } catch (e: RuntimeException) {
                return
            }
        }
    }
}
