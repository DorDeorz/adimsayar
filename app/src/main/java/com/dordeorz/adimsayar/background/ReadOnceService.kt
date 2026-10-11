package com.dordeorz.adimsayar.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.data.XiaomiSteps
import com.dordeorz.adimsayar.sensor.StepSensors
import kotlinx.coroutines.launch

class ReadOnceService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!enterForeground()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val source = intent?.getStringExtra(EXTRA_SOURCE)?.let { name -> ReadSource.entries.firstOrNull { it.name == name } } ?: ReadSource.Job
        val wakeLock = getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        wakeLock?.acquire(READ_TIMEOUT_MS + WAKE_LOCK_MARGIN_MS)
        AppScope.launch {
            try {
                StepUpdater.refresh(applicationContext, READ_TIMEOUT_MS, source)
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun enterForeground(): Boolean =
        try {
            StepCounterService.createChannel(this)
            val strings = Locales.wrap(this)
            val notification = NotificationCompat.Builder(this, StepCounterService.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(strings.getString(R.string.service_title))
                .setContentText(strings.getString(R.string.service_text_starting))
                .setSilent(true)
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            false
        }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val READ_TIMEOUT_MS = 10_000L
        private const val WAKE_LOCK_MARGIN_MS = 5_000L
        private const val WAKE_LOCK_TAG = "adimsayar:read"
        private const val EXTRA_SOURCE = "source"

        fun start(context: Context, source: ReadSource): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
            if (XiaomiSteps.isEnabled(context) || StepCounterService.isEnabled(context)) return false
            if (!StepSensors.hasPermission(context) || !StepSensors.hasCounter(context)) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) != true
            ) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, ReadOnceService::class.java).putExtra(EXTRA_SOURCE, source.name))
                true
            } catch (e: RuntimeException) {
                false
            }
        }
    }
}
