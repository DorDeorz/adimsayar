package com.dordeorz.adimsayar.band

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Protocol for older Huami devices (Mi Band 2–6, Amazfit Bip/GTS/GTR and similar): AES challenge
 * authentication on the auth characteristic, then minute activity data from [since] onwards.
 * Data stays on the band (no delete ack), so the next sync can read the last hour again in full.
 * Ported from Gadgetbridge's InitOperation and FetchActivityOperation.
 */
class HuamiSession(
    private val key: ByteArray,
    private val crypt: Int,
    private val sampleSize: Int,
    private val since: Long,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
) {

    var writeAuth: (ByteArray) -> Unit = {}
    var writeFetch: (ByteArray) -> Unit = {}

    /** Called once authenticated instead of reading activity, for a connection made for something else. */
    var onAuthenticated: (() -> Unit)? = null

    var authenticated = false
        private set
    var answered = false
        private set
    var result: BandResult? = null
        private set
    val minutes = ArrayList<BandMinute>()

    private var keySent = false
    private var fetches = 0
    private var start = 0L
    private var counter = -1
    private var valid = true
    private val buffer = ByteArrayOutputStream()

    fun start() = writeAuth(requestRandom())

    fun onAuth(value: ByteArray) {
        if (value.size < 3 || value[0] != RESPONSE) return
        answered = true
        val ok = value[2] == SUCCESS
        when (value[1].toInt() and 0x0f) {
            AUTH_SEND_KEY -> if (ok) writeAuth(requestRandom()) else result = BandResult.AuthFailed
            AUTH_RANDOM -> when {
                ok && value.size >= 19 -> writeAuth(byteArrayOf((AUTH_ENCRYPTED or crypt).toByte(), AUTH_FLAGS) + aes(value.copyOfRange(3, 19)))
                // First connection of a band without crypt flags: hand over the key (the band asks to tap it).
                !ok && crypt == 0 && !keySent -> {
                    keySent = true
                    writeAuth(byteArrayOf(AUTH_SEND_KEY.toByte(), AUTH_FLAGS) + key)
                }
                else -> result = BandResult.AuthFailed
            }
            AUTH_ENCRYPTED -> if (ok) {
                authenticated = true
                onAuthenticated?.invoke() ?: fetch(since)
            } else {
                result = BandResult.AuthFailed
            }
        }
    }

    fun onFetch(value: ByteArray) {
        if (value.size < 3 || value[0] != RESPONSE || value[2] != SUCCESS) {
            finish()
            return
        }
        when (value[1].toInt()) {
            FETCH_START -> {
                if (value.size < 14 || u32(value, 3) == 0L) {
                    finish()
                    return
                }
                start = parseTime(value, 7)
                writeFetch(byteArrayOf(FETCH_DATA.toByte()))
            }
            FETCH_DATA -> {
                val bytes = buffer.toByteArray()
                if (!valid || bytes.size % sampleSize != 0) {
                    finish()
                    return
                }
                val count = bytes.size / sampleSize
                for (i in 0 until count) {
                    val o = i * sampleSize
                    val heart = bytes[o + 3].toInt() and 0xff
                    minutes += BandMinute(start + i * 60L, bytes[o + 2].toInt() and 0xff, if (heart in 1..254) heart else 0)
                }
                val next = start + count * 60L
                if (count > 0 && fetches < MAX_FETCHES && next < now()) fetch(next) else finish()
            }
            else -> finish()
        }
    }

    fun onData(value: ByteArray) {
        if (value.isEmpty()) return
        if (value[0] == (counter + 1).toByte()) {
            counter++
            buffer.write(value, 1, value.size - 1)
        } else {
            valid = false
        }
    }

    private fun fetch(from: Long) {
        fetches++
        buffer.reset()
        counter = -1
        valid = true
        writeFetch(byteArrayOf(FETCH_START.toByte(), TYPE_ACTIVITY) + timeBytes(from))
    }

    private fun finish() {
        if (result == null) result = BandResult.Ok
    }

    private fun requestRandom(): ByteArray =
        if (crypt == 0) {
            byteArrayOf(AUTH_RANDOM.toByte(), AUTH_FLAGS)
        } else {
            byteArrayOf((crypt or AUTH_RANDOM).toByte(), AUTH_FLAGS, 0x02, 0x01, 0x00)
        }

    private fun aes(challenge: ByteArray): ByteArray =
        Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }.doFinal(challenge)

    /** Year (u16), month, day, hour, minute, then 0 and the UTC offset in quarter hours. */
    private fun timeBytes(epochSeconds: Long): ByteArray {
        val instant = Instant.ofEpochSecond(epochSeconds)
        val t = LocalDateTime.ofInstant(instant, zone)
        val offset = zone.rules.getOffset(instant).totalSeconds / QUARTER_HOUR
        return byteArrayOf(
            t.year.toByte(), (t.year shr 8).toByte(), t.monthValue.toByte(), t.dayOfMonth.toByte(),
            t.hour.toByte(), t.minute.toByte(), 0, offset.toByte(),
        )
    }

    private fun parseTime(b: ByteArray, i: Int): Long {
        val t = LocalDateTime.of(
            (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8),
            b[i + 2].toInt() and 0xff,
            b[i + 3].toInt() and 0xff,
            b[i + 4].toInt() and 0xff,
            b[i + 5].toInt() and 0xff,
            b[i + 6].toInt() and 0xff,
        )
        return if (b.size > i + 7) t.toEpochSecond(ZoneOffset.ofTotalSeconds(b[i + 7] * QUARTER_HOUR)) else t.atZone(zone).toEpochSecond()
    }

    private fun u32(b: ByteArray, i: Int): Long =
        (0 until 4).fold(0L) { acc, k -> acc or ((b[i + k].toLong() and 0xff) shl (8 * k)) }

    companion object {
        private const val RESPONSE = 0x10.toByte()
        private const val SUCCESS = 0x01.toByte()
        private const val AUTH_SEND_KEY = 0x01
        private const val AUTH_RANDOM = 0x02
        private const val AUTH_ENCRYPTED = 0x03
        private const val AUTH_FLAGS = 0x08.toByte()
        private const val FETCH_START = 0x01
        private const val FETCH_DATA = 0x02
        private const val TYPE_ACTIVITY = 0x01.toByte()
        private const val MAX_FETCHES = 10
        private const val QUARTER_HOUR = 15 * 60
    }
}
