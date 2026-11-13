package com.dordeorz.adimsayar.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.dordeorz.adimsayar.AppScope
import com.dordeorz.adimsayar.data.BirdSound
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/** The bird's songs, made from short frequency sweeps so no sound files are needed. */
object BirdSong {

    private const val RATE = 22_050

    /**
     * Peak level of a note, as a share of full scale. 0.12 is about 10 dB below the old 0.4,
     * which was too loud and shrill on the phone speaker.
     */
    private const val GAIN = 0.12

    /** One note: start and end pitch in Hz and its length in ms. */
    private class Note(val from: Double, val to: Double, val ms: Int, val gapMs: Int = 40)

    // Pitches stay at or below about 2.6 kHz: the 3–4 kHz band, where the ear and small phone
    // speakers are loudest, made the old songs piercing.
    private val SONGS = mapOf(
        BirdSound.Chirp to listOf(Note(1_700.0, 2_500.0, 90, 50), Note(1_850.0, 2_650.0, 90)),
        BirdSound.Trill to List(5) { Note(2_100.0, 2_350.0, 45, 25) },
        BirdSound.Tweet to listOf(Note(2_700.0, 1_600.0, 180, 50), Note(2_000.0, 2_000.0, 80)),
        BirdSound.Coo to listOf(Note(520.0, 440.0, 260, 80), Note(560.0, 420.0, 320)),
    )

    /**
     * Plays at media volume, so the phone's volume keys turn it down while the app is open.
     * Stays quiet when the phone is on silent or vibrate.
     */
    fun play(context: Context, sound: BirdSound) {
        val audio = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        AppScope.launch(Dispatchers.Default) {
            val samples = render(SONGS.getValue(sound))
            val track = runCatching {
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
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
            }.getOrNull() ?: return@launch
            runCatching {
                track.write(samples, 0, samples.size)
                track.play()
                delay(samples.size * 1_000L / RATE + 100L)
            }
            track.release()
        }
    }

    private fun render(notes: List<Note>): ShortArray {
        val out = ArrayList<Short>()
        for (note in notes) {
            val count = RATE * note.ms / 1_000
            var phase = 0.0
            for (i in 0 until count) {
                val t = i.toDouble() / count
                val pitch = note.from + (note.to - note.from) * t
                phase += 2 * PI * pitch / RATE
                // A squared-sine rise and fall starts and ends each note gently, with no click or snap.
                val rise = sin(PI * t)
                out += (sin(phase) * rise * rise * Short.MAX_VALUE * GAIN).toInt().toShort()
            }
            repeat(RATE * note.gapMs / 1_000) { out += 0 }
        }
        return out.toShortArray()
    }
}
