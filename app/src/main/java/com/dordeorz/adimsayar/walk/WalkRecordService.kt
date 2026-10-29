package com.dordeorz.adimsayar.walk

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.StepRepository
import com.dordeorz.adimsayar.data.Walk
import com.dordeorz.adimsayar.data.Walks
import com.dordeorz.adimsayar.sensor.FloorCounter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

/** A walk being recorded with GPS. */
data class WalkSession(val start: Long, val startSteps: Long, val meters: Double)

/**
 * Records a walk with GPS while its notification is showing: distance and the route, saved as a
 * [Walk] when it is stopped. Location is used only while recording.
 */
class WalkRecordService : Service(), LocationListener {

    private var last: Location? = null
    private val path = StringBuilder()
    private var points = 0
    private var lastPoint: Location? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finish()
                return START_NOT_STICKY
            }
        }
        if (session.value != null) return START_NOT_STICKY
        if (!hasPermission(this) || !foreground(0.0)) {
            stopSelf()
            return START_NOT_STICKY
        }
        val startSteps = intent?.getLongExtra(EXTRA_STEPS, 0L) ?: 0L
        _session.value = WalkSession(System.currentTimeMillis(), startSteps, 0.0)
        listen()
        FloorCounter.start(this, FLOOR_OWNER)
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun listen() {
        val manager = getSystemService(LocationManager::class.java) ?: return
        runCatching { manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, INTERVAL_MS, MIN_METERS, this, Looper.getMainLooper()) }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasAccuracy() && location.accuracy > MAX_ACCURACY) return
        val current = _session.value ?: return
        val previous = last
        last = location
        val added = previous?.distanceTo(location)?.toDouble() ?: 0.0
        val pointFrom = lastPoint
        if (points < MAX_POINTS && (pointFrom == null || pointFrom.distanceTo(location) >= POINT_METERS)) {
            if (path.isNotEmpty()) path.append(';')
            path.append(String.format(Locale.US, "%.5f,%.5f", location.latitude, location.longitude))
            points++
            lastPoint = location
        }
        val next = current.copy(meters = current.meters + added)
        _session.value = next
        foreground(next.meters)
    }

    @Deprecated("Needed below API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    override fun onProviderEnabled(provider: String) = Unit

    override fun onProviderDisabled(provider: String) = Unit

    private fun finish() {
        val current = _session.value
        getSystemService(LocationManager::class.java)?.removeUpdates(this)
        FloorCounter.stop(this, FLOOR_OWNER)
        _session.value = null
        if (current != null) {
            val app = applicationContext
            val route = path.toString().ifEmpty { null }
            AppScope.launch {
                val steps = (StepRepository.get(app).today() - current.startSteps).coerceAtLeast(0L)
                Walks.save(app, Walk(kind = Walks.KIND_GPS, start = current.start, end = System.currentTimeMillis(), steps = steps, meters = current.meters, path = route))
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        if (_session.value != null) finish()
        super.onDestroy()
    }

    private fun foreground(meters: Double): Boolean {
        val strings = Locales.wrap(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, strings.getString(R.string.walk_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, WalkRecordService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.walk_gps_recording))
            .setContentText(strings.getString(R.string.walk_gps_distance, String.format(Locales.current, "%.2f", meters / 1_000.0)))
            .setContentIntent(open)
            .addAction(0, strings.getString(R.string.walk_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    companion object {
        private const val CHANNEL_ID = "walk"
        private const val NOTIFICATION_ID = 41
        private const val ACTION_STOP = "com.dordeorz.adimsayar.WALK_STOP"
        private const val EXTRA_STEPS = "steps"
        private const val FLOOR_OWNER = "walk"
        private const val INTERVAL_MS = 3_000L
        private const val MIN_METERS = 5f
        private const val MAX_ACCURACY = 30f
        private const val POINT_METERS = 20f
        private const val MAX_POINTS = 2_000

        private val _session = MutableStateFlow<WalkSession?>(null)

        val session: StateFlow<WalkSession?> = _session.asStateFlow()

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(context: Context, todaySteps: Long) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, WalkRecordService::class.java).putExtra(EXTRA_STEPS, todaySteps))
            }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, WalkRecordService::class.java).setAction(ACTION_STOP)) }
        }
    }
}
