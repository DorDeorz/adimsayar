package com.dordeorz.adimsayar.ui

import android.media.AudioAttributes
import android.media.AudioFormat
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

    /** One note: start and end pitch in Hz and its length in ms. */
    private class Note(val from: Double, val to: Double, val ms: Int, val gapMs: Int = 40)

    private val SONGS = mapOf(
        BirdSound.Chirp to listOf(Note(2_600.0, 3_800.0, 70), Note(2_800.0, 4_000.0, 70)),
        BirdSound.Trill to List(6) { Note(3_200.0, 3_600.0, 35, 15) },
        BirdSound.Tweet to listOf(Note(4_200.0, 2_400.0, 160), Note(3_000.0, 3_000.0, 60)),
        BirdSound.Coo to listOf(Note(520.0, 440.0, 260, 80), Note(560.0, 420.0, 320)),
    )

    fun play(sound: BirdSound) {
        AppScope.launch(Dispatchers.Default) {
            val samples = render(SONGS.getValue(sound))
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
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
                // A soft rise and fall keeps the note from clicking.
                val envelope = sin(PI * t)
                out += (sin(phase) * envelope * Short.MAX_VALUE * 0.4).toInt().toShort()
            }
            repeat(RATE * note.gapMs / 1_000) { out += 0 }
        }
        return out.toShortArray()
    }
}
