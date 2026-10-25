package com.dordeorz.adimsayar.band

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Packet framing for Xiaomi watches and bands that talk over classic Bluetooth (RFCOMM/SPP).
 * The band answers a version request; version 2 and later use a different frame with a CRC and
 * acknowledgements. Ported from Gadgetbridge's XiaomiSppProtocolV1/V2.
 */
class XiaomiSppProtocol(
    private val cipher: XiaomiCipher,
    private val write: (ByteArray) -> Unit,
    private val onCommand: (ByteArray) -> Unit,
    private val onActivity: (ByteArray) -> Unit,
    private val onReady: () -> Unit,
) {

    /** 0 until the band said which framing it uses. */
    var version = 0
        private set
    var ready = false
        private set

    private var buffer = ByteArray(0)
    private var frameSerial = 0
    private var packetCounter = 0
    private var sequence = 0

    fun requestVersion() {
        write(encodeV1(CHANNEL_VERSION, FLAG_DEFAULT or FLAG_NEEDS_RESPONSE, OPCODE_READ, 0, DATA_PLAIN, ByteArray(0)))
    }

    /** The band did not answer the version request: talk version 1. */
    fun useV1() {
        if (version != 0) return
        version = 1
        markReady()
    }

    fun send(payload: ByteArray, auth: Boolean) {
        if (version >= 2) {
            val opcode = if (auth) OPCODE_PLAIN else OPCODE_ENCRYPTED
            val body = if (auth) payload else XiaomiCrypto.ctr(checkNotNull(cipher.keys).encryptionKey, payload)
            write(encodeV2(PACKET_DATA, sequence++, byteArrayOf(V2_CHANNEL_PROTOBUF.toByte(), opcode.toByte()) + body))
            return
        }
        val serial = frameSerial++
        if (auth) {
            write(encodeV1(CHANNEL_PROTO_TX, FLAG_DEFAULT, OPCODE_SEND, serial, DATA_AUTH, payload))
            return
        }
        val keys = checkNotNull(cipher.keys)
        val counter = ++packetCounter
        val sealed = XiaomiCrypto.encrypt(keys.encryptionKey, XiaomiCrypto.packetNonce(keys.encryptionNonce, counter), payload)
        val body = ByteBuffer.allocate(2 + sealed.size).order(ByteOrder.LITTLE_ENDIAN).putShort(counter.toShort()).put(sealed).array()
        write(encodeV1(CHANNEL_PROTO_TX, FLAG_DEFAULT, OPCODE_SEND, serial, DATA_ENCRYPTED, body))
    }

    /** One watch face upload part on the data channel: plain on version 2, sealed with counter 0 on version 1. */
    fun sendData(chunk: ByteArray) {
        if (version >= 2) {
            write(encodeV2(PACKET_DATA, sequence++, byteArrayOf(V2_CHANNEL_DATA.toByte(), OPCODE_PLAIN.toByte()) + chunk))
            return
        }
        val keys = checkNotNull(cipher.keys)
        val sealed = XiaomiCrypto.encrypt(keys.encryptionKey, XiaomiCrypto.packetNonce(keys.encryptionNonce, 0), chunk)
        write(encodeV1(CHANNEL_MASS, FLAG_DEFAULT, OPCODE_SEND, frameSerial++, DATA_ENCRYPTED, sealed))
    }

    fun onBytes(data: ByteArray) {
        buffer += data
        while (buffer.isNotEmpty()) {
            val used = if (version >= 2) parseV2(buffer) else parseV1(buffer)
            if (used == 0) return
            buffer = if (used >= buffer.size) ByteArray(0) else buffer.copyOfRange(used, buffer.size)
        }
    }

    private fun markReady() {
        if (ready) return
        ready = true
        onReady()
    }

    /** Returns how many bytes were consumed, 0 when more bytes are needed. */
    private fun parseV1(buf: ByteArray): Int {
        if (!startsWith(buf, V1_PREAMBLE)) return skip(buf, V1_PREAMBLE[0])
        if (buf.size < V1_MIN) return 0
        val payloadSize = u16(buf, 5)
        val size = payloadSize + 8
        if (buf.size < size) return 0
        if (payloadSize < 3 || buf[size - 1] != V1_EPILOGUE) return skip(buf, V1_PREAMBLE[0])
        val rawChannel = buf[3].toInt() and 0xff
        val dataType = buf[9].toInt() and 0xff
        val payload = buf.copyOfRange(10, size - 1)
        val plain = if (dataType == DATA_ENCRYPTED && cipher.keys != null) cipher.decrypt(payload) else payload
        if (plain != null) {
            when (rawChannel) {
                CHANNEL_VERSION -> onVersion(plain)
                CHANNEL_PROTO_RX, CHANNEL_PROTO_TX -> onCommand(plain)
                CHANNEL_FITNESS -> onActivity(plain)
            }
        }
        return size
    }

    private fun parseV2(buf: ByteArray): Int {
        if (!startsWith(buf, V2_PREAMBLE)) return skip(buf, V2_PREAMBLE[0])
        if (buf.size < V2_HEADER) return 0
        val length = u16(buf, 4)
        val size = V2_HEADER + length
        if (buf.size < size) return 0
        val type = buf[2].toInt() and 0x0f
        val seq = buf[3].toInt() and 0xff
        val payload = buf.copyOfRange(V2_HEADER, size)
        if (crc16(payload) != u16(buf, 6)) return size
        when (type) {
            PACKET_SESSION_CONFIG -> markReady()
            PACKET_DATA -> {
                if (payload.size >= 2) {
                    val channel = payload[0].toInt() and 0x0f
                    val opcode = payload[1].toInt() and 0xff
                    val body = payload.copyOfRange(2, payload.size)
                    val keys = cipher.keys
                    val plain = when {
                        opcode != OPCODE_ENCRYPTED -> body
                        keys != null -> XiaomiCrypto.ctr(keys.decryptionKey, body)
                        else -> null
                    }
                    if (plain != null) {
                        when (channel) {
                            V2_CHANNEL_PROTOBUF -> onCommand(plain)
                            V2_CHANNEL_ACTIVITY -> onActivity(plain)
                        }
                    }
                }
                write(encodeV2(PACKET_ACK, seq, ByteArray(0)))
            }
        }
        return size
    }

    private fun onVersion(payload: ByteArray) {
        if (version != 0) return
        if (payload.isNotEmpty() && payload[0] >= 2) {
            version = 2
            write(encodeV2(PACKET_SESSION_CONFIG, 0, SESSION_START))
        } else {
            version = 1
            markReady()
        }
    }

    private fun skip(buf: ByteArray, first: Byte): Int {
        for (i in 1 until buf.size) if (buf[i] == first) return i
        return buf.size
    }

    private fun startsWith(buf: ByteArray, prefix: ByteArray): Boolean {
        for (i in prefix.indices) {
            if (i >= buf.size) return true
            if (buf[i] != prefix[i]) return false
        }
        return true
    }

    companion object {
        private val V1_PREAMBLE = byteArrayOf(0xba.toByte(), 0xdc.toByte(), 0xfe.toByte())
        private const val V1_EPILOGUE = 0xef.toByte()
        private const val V1_MIN = 11
        private val V2_PREAMBLE = byteArrayOf(0xa5.toByte(), 0xa5.toByte())
        private const val V2_HEADER = 8

        const val CHANNEL_VERSION = 0
        const val CHANNEL_PROTO_RX = 1
        const val CHANNEL_PROTO_TX = 2
        const val CHANNEL_FITNESS = 3
        const val CHANNEL_MASS = 5
        const val DATA_PLAIN = 0
        const val DATA_ENCRYPTED = 1
        const val DATA_AUTH = 2
        private const val OPCODE_READ = 0
        private const val OPCODE_SEND = 2
        private const val FLAG_DEFAULT = 0x80
        private const val FLAG_NEEDS_RESPONSE = 0x40

        const val PACKET_ACK = 1
        const val PACKET_SESSION_CONFIG = 2
        const val PACKET_DATA = 3
        const val V2_CHANNEL_PROTOBUF = 1
        const val V2_CHANNEL_DATA = 2
        const val V2_CHANNEL_ACTIVITY = 5
        const val OPCODE_PLAIN = 1
        const val OPCODE_ENCRYPTED = 2

        /** Start-session request as the official app sends it: version 1.0.0, 64512 byte frames, window 32, 10 s timeout. */
        private val SESSION_START = byteArrayOf(
            1,
            1, 3, 0, 1, 0, 0,
            2, 2, 0, 0, 0xfc.toByte(),
            3, 2, 0, 0x20, 0,
            4, 2, 0x10, 0x27,
        )

        fun encodeV1(channel: Int, flags: Int, opcode: Int, serial: Int, dataType: Int, payload: ByteArray): ByteArray =
            ByteBuffer.allocate(V1_MIN + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(V1_PREAMBLE)
                .put((channel and 0x0f).toByte())
                .put(flags.toByte())
                .putShort((payload.size + 3).toShort())
                .put(opcode.toByte())
                .put(serial.toByte())
                .put(dataType.toByte())
                .put(payload)
                .put(V1_EPILOGUE)
                .array()

        fun encodeV2(type: Int, seq: Int, payload: ByteArray): ByteArray =
            ByteBuffer.allocate(V2_HEADER + payload.size).order(ByteOrder.LITTLE_ENDIAN)
                .put(V2_PREAMBLE)
                .put((type and 0x0f).toByte())
                .put(seq.toByte())
                .putShort(payload.size.toShort())
                .putShort(crc16(payload).toShort())
                .put(payload)
                .array()

        /** CRC-16/ARC. */
        fun crc16(data: ByteArray): Int {
            var crc = 0
            for (b in data) {
                crc = crc xor (b.toInt() and 0xff)
                repeat(8) { crc = if (crc and 1 != 0) (crc ushr 1) xor 0xa001 else crc ushr 1 }
            }
            return crc
        }

        private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xff) or ((b[i + 1].toInt() and 0xff) shl 8)
    }
}
