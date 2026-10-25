package com.dordeorz.adimsayar.background

import android.annotation.SuppressLint
import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.dordeorz.adimsayar.band.BandEvent
import com.dordeorz.adimsayar.band.BandMusic
import com.dordeorz.adimsayar.band.BandNotice
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.data.BandLive
import com.dordeorz.adimsayar.data.BandNotify
import com.dordeorz.adimsayar.data.BandReplies

/**
 * Forwards phone notifications, incoming calls and the playing track to the band, and carries out
 * what the user does on the band. Holding notification access also keeps the app process alive,
 * which is what keeps [BandLive]'s connection open without an ongoing notification.
 */
class BandNotificationListener : NotificationListenerService() {

    private class Sent(val id: Int, val packageName: String, val text: String)

    private val main = Handler(Looper.getMainLooper())
    private val sent = LinkedHashMap<String, Sent>()
    private var nextId = 1
    private var callKey: String? = null
    private var controller: MediaController? = null
    private var sessions: MediaSessionManager? = null

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list.orEmpty()) }

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pushMusic()
        override fun onMetadataChanged(metadata: MediaMetadata?) = pushMusic()
        override fun onSessionDestroyed() {
            controller = null
            pushMusic()
        }
    }

    override fun onListenerConnected() {
        BandLive.onEvent = { event -> main.post { onBandEvent(event) } }
        BandLive.onConnected = { main.post { pushMusic() } }
        BandLive.refresh(this)
        startMusic()
    }

    override fun onListenerDisconnected() {
        stopMusic()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!BandLive.connected) return
        val n = sbn.notification
        if (sbn.packageName == packageName) {
            // FitWren's own reminders go to the band as well, so the band vibrates for them.
            if (!BandNotify.reminders(this) || NotificationCompat.getChannelId(n) !in BandNotify.REMINDER_CHANNELS) return
        } else {
            if (n.category == Notification.CATEGORY_CALL) {
                onCall(sbn)
                return
            }
            if (!BandNotify.apps(this) || !BandNotify.allowed(this, sbn.packageName)) return
        }
        if (BandNotify.quietNow(this)) return
        if (sbn.isOngoing || n.flags and (Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_LOCAL_ONLY or Notification.FLAG_FOREGROUND_SERVICE) != 0) return
        if (!loud(sbn)) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty() && body.isEmpty()) return
        val text = "$title\n$body"
        val previous = sent[sbn.key]
        if (previous?.text == text) return
        // A silent update of our own reminder (water added) should not vibrate the band again.
        if (previous != null && sbn.packageName == packageName && n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) {
            sent[sbn.key] = Sent(previous.id, previous.packageName, text)
            return
        }
        val id = previous?.id ?: nextId++
        BandLive.notify(BandNotice(id, sbn.packageName, appName(sbn.packageName), title, body.take(MAX_BODY), sbn.postTime, sbn.key))
        sent.remove(sbn.key)
        sent[sbn.key] = Sent(id, sbn.packageName, text)
        while (sent.size > MAX_TRACKED) sent.remove(sent.keys.first())
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.key == callKey) {
            callKey = null
            BandLive.endCall()
            return
        }
        val s = sent.remove(sbn.key) ?: return
        BandLive.dismiss(s.id, s.packageName, sbn.key)
    }

    private fun onCall(sbn: StatusBarNotification) {
        if (!BandNotify.calls(this)) return
        val n = sbn.notification
        val ringing = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && n.extras.containsKey(Notification.EXTRA_CALL_TYPE)) {
            n.extras.getInt(Notification.EXTRA_CALL_TYPE) == Notification.CallStyle.CALL_TYPE_INCOMING
        } else {
            n.fullScreenIntent != null
        }
        when {
            ringing && callKey == null -> {
                callKey = sbn.key
                val name = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val number = BandReplies.ringingNumber ?: n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
                BandLive.call(name, number)
            }
            !ringing && callKey == sbn.key -> {
                callKey = null
                BandLive.endCall()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun onBandEvent(event: BandEvent) {
        when (event) {
            is BandEvent.Dismiss -> event.ids.forEach { id ->
                val key = sent.entries.firstOrNull { it.value.id == id }?.key ?: return@forEach
                sent.remove(key)
                runCatching { cancelNotification(key) }
            }
            BandEvent.RejectCall -> {
                callKey = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && BandNotify.canRejectCalls(this)) {
                    @Suppress("DEPRECATION")
                    runCatching { getSystemService(TelecomManager::class.java)?.endCall() }
                }
            }
            BandEvent.IgnoreCall -> callKey = null
            is BandEvent.CallReply -> {
                val sent = BandReplies.send(this, event.number, event.message)
                BandLive.replySent(sent)
                callKey = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && BandNotify.canRejectCalls(this)) {
                    @Suppress("DEPRECATION")
                    runCatching { getSystemService(TelecomManager::class.java)?.endCall() }
                }
            }
            BandEvent.MusicRequest -> pushMusic()
            is BandEvent.MusicButton -> onMusicButton(event)
            is BandEvent.FindPhone, BandEvent.BandFound -> Unit
        }
    }

    private fun loud(sbn: StatusBarNotification): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return sbn.notification.priority >= Notification.PRIORITY_DEFAULT
        val ranking = Ranking()
        if (!currentRanking.getRanking(sbn.key, ranking)) return true
        return ranking.importance >= android.app.NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun appName(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (e: Exception) {
        packageName
    }

    private fun startMusic() {
        val manager = getSystemService(MediaSessionManager::class.java) ?: return
        val component = ComponentName(this, BandNotificationListener::class.java)
        try {
            manager.addOnActiveSessionsChangedListener(sessionsChanged, component, main)
            pickController(manager.getActiveSessions(component))
            sessions = manager
        } catch (e: SecurityException) {
            Log.w(XiaomiBand.TAG, "media sessions: $e")
        }
    }

    private fun stopMusic() {
        sessions?.removeOnActiveSessionsChangedListener(sessionsChanged)
        sessions = null
        controller?.unregisterCallback(controllerCallback)
        controller = null
    }

    private fun pickController(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) return
        controller?.unregisterCallback(controllerCallback)
        controller = best
        best?.registerCallback(controllerCallback, main)
        pushMusic()
    }

    private fun pushMusic() {
        if (!BandLive.connected || !BandNotify.music(this)) return
        val c = controller
        val state = c?.playbackState
        val metadata = c?.metadata
        val music = if (c == null || state == null || metadata == null) {
            BandMusic(STATE_NOTHING, volumePercent())
        } else {
            BandMusic(
                state = if (state.state == PlaybackState.STATE_PLAYING) STATE_PLAYING else STATE_PAUSED,
                volume = volumePercent(),
                track = metadata.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = (metadata.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)).orEmpty(),
                positionSec = (state.position.coerceAtLeast(0L) / 1000).toInt(),
                durationSec = (metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L) / 1000).toInt(),
            )
        }
        BandLive.music(music)
    }

    private fun onMusicButton(event: BandEvent.MusicButton) {
        if (!BandNotify.music(this)) return
        val controls = controller?.transportControls
        when (event.button) {
            BandEvent.PLAY -> controls?.play()
            BandEvent.PAUSE -> controls?.pause()
            BandEvent.PREVIOUS -> controls?.skipToPrevious()
            BandEvent.NEXT -> controls?.skipToNext()
            BandEvent.VOLUME -> {
                val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val direction = if (event.volume > volumePercent()) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
                pushMusic()
            }
        }
    }

    private fun volumePercent(): Int {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    companion object {
        private const val MAX_BODY = 500
        private const val MAX_TRACKED = 128
        private const val STATE_NOTHING = 0
        private const val STATE_PLAYING = 1
        private const val STATE_PAUSED = 2
    }
}
