package com.dordeorz.adimsayar.walk

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Plays a click at the chosen steps per minute, so walking keeps a steady pace; runs with a notification. */
class MetronomeService : Service() {

    private var track: AudioTrack? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val bpm = intent?.getIntExtra(EXTRA_BPM, 0)?.takeIf { it > 0 } ?: running.value ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!foreground(bpm)) {
            stopSelf()
            return START_NOT_STICKY
        }
        play(bpm)
        _running.value = bpm
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
        _running.value = null
        super.onDestroy()
    }

    private fun play(bpm: Int) {
        track?.let { runCatching { it.stop() }; it.release() }
        val beat = beat(bpm)
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(beat.size * 2)
                .build()
                .also {
                    it.write(beat, 0, beat.size)
                    it.setLoopPoints(0, beat.size, -1)
                    it.play()
                }
        }.getOrNull()
    }

    /** One beat: a short wooden click followed by silence until the next step. */
    private fun beat(bpm: Int): ShortArray {
        val frames = RATE * 60 / bpm
        val click = RATE * CLICK_MS / 1_000
        return ShortArray(frames) { i ->
            if (i >= click) {
                0
            } else {
                val t = i.toDouble() / RATE
                (sin(2 * PI * CLICK_HZ * t) * exp(-t * DECAY) * Short.MAX_VALUE * VOLUME).toInt().toShort()
            }
        }
    }

    private fun foreground(bpm: Int): Boolean {
        val strings = Locales.wrap(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, strings.getString(R.string.metronome_channel), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MetronomeService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.metronome_title))
            .setContentText(strings.getString(R.string.metronome_bpm, bpm))
            .setContentIntent(open)
            .addAction(0, strings.getString(R.string.walk_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            false
        }
    }

    companion object {
        private const val CHANNEL_ID = "metronome"
        private const val NOTIFICATION_ID = 40
        private const val ACTION_STOP = "com.dordeorz.adimsayar.METRONOME_STOP"
        private const val EXTRA_BPM = "bpm"
        private const val RATE = 22_050
        private const val CLICK_MS = 30
        private const val CLICK_HZ = 1_600.0
        private const val DECAY = 160.0
        private const val VOLUME = 0.6

        private val _running = MutableStateFlow<Int?>(null)

        /** The pace being played, or null when the metronome is off. */
        val running: StateFlow<Int?> = _running.asStateFlow()

        fun start(context: Context, bpm: Int) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MetronomeService::class.java).putExtra(EXTRA_BPM, bpm))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MetronomeService::class.java))
        }
    }
}
