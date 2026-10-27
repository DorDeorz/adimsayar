package com.dordeorz.adimsayar.band

import java.util.zip.CRC32

/**
 * Sends a watch face file to a Mi Band 6 over its firmware characteristics:
 * ask for the chunk size, describe the file, then send it chunk by chunk while the band reports how
 * far it got. Ported from Gadgetbridge's UpdateFirmwareOperation2020 and MiBand6FirmwareInfo.
 */
class HuamiFaceUpload(private val bytes: ByteArray, private val packetSize: Int) {

    enum class Failure { NoSpace, LowBattery, Refused }

    /** How a whole upload went, as the app tells it. */
    enum class Outcome { Done, NoSpace, LowBattery, Refused, NotReached }

    var writeControl: (ByteArray) -> Unit = {}
    var writeData: (ByteArray) -> Unit = {}
    var writeConfig: (ByteArray) -> Unit = {}
    var onProgress: (Int) -> Unit = {}

    var done = false
        private set
    var failure: Failure? = null
        private set

    private var chunk = 0

    fun start() = writeControl(byteArrayOf(REQUEST_PARAMETERS))

    fun onControl(value: ByteArray) {
        if (done || failure != null) return
        if (value.size < 3 || value[0] != RESPONSE) return
        val progress = value[1] == PROGRESS && value.size >= 6
        if (!progress && value[2] != SUCCESS) {
            failure = when {
                value[1] == START_TRANSFER && value[2] == ERROR_FREE_SPACE -> Failure.NoSpace
                value[1] == SEND_INFO && value[2] == ERROR_LOW_BATTERY -> Failure.LowBattery
                else -> Failure.Refused
            }
            return
        }
        when (value[1]) {
            REQUEST_PARAMETERS -> {
                if (value.size < 6) {
                    failure = Failure.Refused
                    return
                }
                chunk = u16(value, 4).takeIf { it > 0 } ?: DEFAULT_CHUNK
                sendInfo()
            }
            SEND_INFO -> writeControl(byteArrayOf(START_TRANSFER, 1))
            START_TRANSFER -> sendChunk(0)
            PROGRESS -> sendChunk(u32(value, 2))
            COMPLETE_TRANSFER -> writeControl(byteArrayOf(FINALIZE))
            FINALIZE -> {
                done = true
                onProgress(100)
            }
            else -> failure = Failure.Refused
        }
    }

    private fun sendInfo() {
        val size = le32(bytes.size)
        if (isUihh(bytes)) {
            writeConfig(byteArrayOf(0x39, 0x00) + size + bytes.copyOfRange(18, 22))
        }
        val crc = CRC32().apply { update(bytes) }.value.toInt()
        writeControl(
            byteArrayOf(SEND_INFO, TYPE_WATCHFACE) + size + le32(crc) +
                byteArrayOf(chunk.toByte(), (chunk shr 8).toByte(), 0, 0, 1) + size,
        )
    }

    private fun sendChunk(offset: Int) {
        val remaining = bytes.size - offset
        if (remaining <= 0) {
            writeControl(byteArrayOf(COMPLETE_TRANSFER))
            return
        }
        val end = offset + minOf(chunk, remaining)
        var at = offset
        while (at < end) {
            val next = minOf(at + packetSize, end)
            writeData(bytes.copyOfRange(at, next))
            at = next
        }
        onProgress((end.toLong() * 99 / bytes.size).toInt())
    }

    companion object {
        private const val RESPONSE = 0x10.toByte()
        private const val SUCCESS = 0x01.toByte()
        private const val REQUEST_PARAMETERS = 0xd0.toByte()
        private const val SEND_INFO = 0xd2.toByte()
        private const val START_TRANSFER = 0xd3.toByte()
        private const val PROGRESS = 0xd4.toByte()
        private const val COMPLETE_TRANSFER = 0xd5.toByte()
        private const val FINALIZE = 0xd6.toByte()
        private const val ERROR_FREE_SPACE = 0x47.toByte()
        private const val ERROR_LOW_BATTERY = 0x22.toByte()
        private const val TYPE_WATCHFACE = 8.toByte()
        private const val DEFAULT_CHUNK = 2048

        /** Mi Band 6 faces start with "UIHH" and a 1 or 2. */
        fun isFace(bytes: ByteArray): Boolean = isUihh(bytes)

        private fun isUihh(bytes: ByteArray) = bytes.size > 22 && startsWith(bytes, "UIHH") && bytes[4].toInt() in 1..2

        private fun startsWith(bytes: ByteArray, text: String) =
            bytes.size >= text.length && text.indices.all { bytes[it] == text[it].code.toByte() }

        private fun le32(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte())

        private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)

        private fun u32(b: ByteArray, i: Int) = u16(b, i) or (u16(b, i + 2) shl 16)
    }
}
