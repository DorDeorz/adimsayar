package com.dordeorz.adimsayar.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.data.BandLive
import com.dordeorz.adimsayar.ui.Tab

/** Phone-side alerts about the band: low battery, lost connection, and ringing to find the phone. */
object BandAlerts {

    private const val CHANNEL_ID = "bileklik"
    private const val FIND_CHANNEL_ID = "telefonu_bul"
    private const val ID_BATTERY = 7101
    private const val ID_LOST = 7102
    private const val ID_FIND = 7103
    private const val FIND_MAX_MS = 60_000L
    const val ACTION_FOUND = "com.dordeorz.adimsayar.PHONE_FOUND"

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var savedVolume = -1
    private val autoStop = Runnable { stopRinging(null, tellBand = true) }

    fun batteryLow(context: Context, level: Int) {
        val strings = Locales.wrap(context)
        notify(context, ID_BATTERY, strings.getString(R.string.band_battery_low_title), strings.getString(R.string.band_battery_low_text, level))
    }

    fun cancelBatteryLow(context: Context) = NotificationManagerCompat.from(context).cancel(ID_BATTERY)

    fun lost(context: Context) {
        val strings = Locales.wrap(context)
        notify(context, ID_LOST, strings.getString(R.string.band_lost_title), strings.getString(R.string.band_lost_text))
    }

    fun cancelLost(context: Context) = NotificationManagerCompat.from(context).cancel(ID_LOST)

    /** Rings at alarm volume and vibrates until the user taps "Found it", the band stops it, or a minute passes. */
    fun startRinging(context: Context) {
        val app = context.applicationContext
        main.post {
            if (player != null) return@post
            val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            runCatching {
                savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
                audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
            }
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            player = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    setDataSource(app, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }.onFailure { Log.w(XiaomiBand.TAG, "find phone sound: $it") }.getOrNull()
            vibrator(app)?.let(::vibrate)
            showRinging(app)
            main.postDelayed(autoStop, FIND_MAX_MS)
        }
    }

    /** Stops ringing; [tellBand] closes the find phone screen on the band too. */
    fun stopRinging(context: Context?, tellBand: Boolean) {
        main.post {
            main.removeCallbacks(autoStop)
            val p = player
            player = null
            p?.let { runCatching { it.stop() }; it.release() }
            val app = context?.applicationContext ?: appContext
            if (app != null) {
                vibrator(app)?.cancel()
                if (savedVolume >= 0) {
                    runCatching { (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0) }
                }
                NotificationManagerCompat.from(app).cancel(ID_FIND)
            }
            savedVolume = -1
            if (tellBand && p != null) BandLive.findPhoneStopped()
        }
    }

    private var appContext: Context? = null

    private fun showRinging(context: Context) {
        appContext = context
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val strings = Locales.wrap(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(FIND_CHANNEL_ID, strings.getString(R.string.find_phone_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false)
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val found = PendingIntent.getBroadcast(
            context,
            ID_FIND,
            Intent(context, PhoneFoundReceiver::class.java).setAction(ACTION_FOUND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, FIND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(strings.getString(R.string.find_phone_title))
            .setContentText(strings.getString(R.string.find_phone_text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(found)
            .setDeleteIntent(found)
            .addAction(0, strings.getString(R.string.find_phone_found), found)
            .setOngoing(true)
            .build()
        try {
            manager.notify(ID_FIND, notification)
        } catch (e: SecurityException) {
            return
        }
    }

    private fun notify(context: Context, id: Int, title: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, Locales.wrap(context).getString(R.string.band_alert_channel), NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Tab.Settings.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(id, notification)
        } catch (e: SecurityException) {
            return
        }
    }

    private fun vibrate(vibrator: Vibrator) {
        val pattern = longArrayOf(0, 600, 400)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, 0)
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrator(context: Context): Vibrator? = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
}

/** "Found it" on the find phone notification. */
class PhoneFoundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BandAlerts.ACTION_FOUND) BandAlerts.stopRinging(context, tellBand = true)
    }
}
