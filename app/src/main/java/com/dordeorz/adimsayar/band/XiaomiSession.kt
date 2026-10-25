package com.dordeorz.adimsayar.band

import java.security.SecureRandom
import java.util.Calendar
import java.util.TimeZone

enum class BandResult { Ok, BluetoothOff, NoPermission, ConnectFailed, Unsupported, AuthFailed, Timeout }

class BandFile(val id: XiaomiFileId, val minutes: List<BandMinute>, val sleep: BandSleepRecord? = null, val samplesOnly: Boolean = false)

class XiaomiPhoneInfo(val apiLevel: Int, val model: String, val region: String, val use12Hour: Boolean)

/**
 * Transport-independent part of the Xiaomi protocol: authentication, clock, and the activity file
 * list/fetch/ack cycle. A link (BLE or classic Bluetooth) feeds it decoded commands and activity
 * chunks and sends what it asks for.
 */
class XiaomiSession(
    private val secret: ByteArray?,
    private val userId: String?,
    private val phone: XiaomiPhoneInfo,
    private val log: (String) -> Unit = {},
) {

    /** Sends one protobuf command; [auth] is true for authentication commands (type 1). */
    var send: (bytes: ByteArray, auth: Boolean) -> Unit = { _, _ -> }

    /** Called once the band accepted us, before any other command goes out. */
    var onAuthenticated: () -> Unit = {}

    val cipher = XiaomiCipher()
    var authenticated = false
        private set
    var answered = false
        private set
    var result: BandResult? = null
        private set
    var fileSummary: String = ""
        private set

    private val phoneNonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val assembler = XiaomiFileAssembler()
    private val fileIds = ArrayList<XiaomiFileId>()
    private val todayIds = HashSet<XiaomiFileId>()
    private var fetching: XiaomiFileId? = null
    private val pendingStore = ArrayDeque<BandFile>()
    private var plainTries = 0

    fun startEncrypted() {
        if (secret == null) {
            result = BandResult.AuthFailed
            return
        }
        send(command(TYPE_AUTH, AUTH_NONCE).message(3, ProtoWriter().message(30, ProtoWriter().bytes(1, phoneNonce))).toByteArray(), true)
    }

    fun startPlain() {
        val id = if (plainTries == 0 && !userId.isNullOrEmpty()) userId else DEFAULT_USER_ID
        plainTries++
        send(command(TYPE_AUTH, AUTH_USER_ID).message(3, ProtoWriter().string(7, id)).toByteArray(), true)
    }

    /**
     * Drives the session until every wanted file is stored. [pump] waits for and handles the next
     * incoming event: true when something arrived, false on a quiet period, null when the link dropped.
     */
    suspend fun loop(pump: suspend () -> Boolean?, store: suspend (BandFile) -> Boolean): BandResult {
        while (result == null) {
            when (pump()) {
                null -> return if (authenticated) BandResult.Timeout else BandResult.ConnectFailed
                false -> {
                    if (authenticated) {
                        fetchNext()
                        continue
                    }
                    return if (answered) BandResult.AuthFailed else BandResult.Timeout
                }
                true -> Unit
            }
            val file = pendingStore.removeFirstOrNull() ?: continue
            if (store(file) && file.id !in todayIds) {
                send(command(TYPE_HEALTH, HEALTH_ACK).message(10, ProtoWriter().bytes(3, file.id.toBytes())).toByteArray(), false)
            }
            fetchNext()
        }
        return result ?: BandResult.Ok
    }

    fun onCommand(bytes: ByteArray) {
        val cmd = ProtoMessage.parse(bytes) ?: return
        val type = cmd.uint(1)?.toInt()
        val subtype = cmd.uint(2)?.toInt()
        when {
            type == TYPE_AUTH && subtype == AUTH_NONCE -> onWatchNonce(cmd)
            type == TYPE_AUTH && subtype == AUTH_STEP3 -> onAccepted()
            type == TYPE_AUTH && subtype == AUTH_USER_ID -> {
                answered = true
                when {
                    cmd.message(3)?.uint(8) == 1L -> onAccepted()
                    plainTries < 2 && !userId.isNullOrEmpty() -> startPlain()
                    else -> result = BandResult.AuthFailed
                }
            }
            type == TYPE_HEALTH && subtype == HEALTH_TODAY -> {
                cmd.message(10)?.bytes(2)?.let {
                    val ids = XiaomiFileId.list(it)
                    fileIds += ids
                    todayIds += ids
                }
                send(command(TYPE_HEALTH, HEALTH_PAST).toByteArray(), false)
            }
            type == TYPE_HEALTH && subtype == HEALTH_PAST -> {
                cmd.message(10)?.bytes(2)?.let { fileIds += XiaomiFileId.list(it) }
                fileSummary = fileIds.groupBy { "${it.flags}v${it.version}" }.entries.joinToString(" ") { "${it.key}×${it.value.size}" }
                log("files: $fileSummary")
                fetchNext()
            }
        }
    }

    fun onActivity(chunk: ByteArray) {
        assembler.add(chunk)?.let(::onFile)
    }

    private fun onWatchNonce(cmd: ProtoMessage) {
        answered = true
        val watch = cmd.message(3)?.message(31)
        val watchNonce = watch?.bytes(1)
        val watchHmac = watch?.bytes(2)
        val key = secret
        if (watchNonce == null || watchHmac == null || key == null) {
            result = BandResult.AuthFailed
            return
        }
        val keys = XiaomiCrypto.deriveKeys(key, phoneNonce, watchNonce)
        if (!XiaomiCrypto.hmac(keys.decryptionKey, watchNonce + phoneNonce).contentEquals(watchHmac)) {
            result = BandResult.AuthFailed
            return
        }
        cipher.keys = keys
        val info = ProtoWriter()
            .uint(1, 0)
            .float(2, phone.apiLevel.toFloat())
            .string(3, phone.model)
            .uint(4, 224)
            .string(5, phone.region)
            .toByteArray()
        val step3 = ProtoWriter()
            .bytes(1, XiaomiCrypto.hmac(keys.encryptionKey, phoneNonce + watchNonce))
            .bytes(2, XiaomiCrypto.encrypt(keys.encryptionKey, XiaomiCrypto.packetNonce(keys.encryptionNonce, 0), info))
        send(command(TYPE_AUTH, AUTH_STEP3).message(3, ProtoWriter().message(32, step3)).toByteArray(), true)
    }

    private fun onAccepted() {
        if (authenticated) return
        authenticated = true
        onAuthenticated()
        send(timeCommand(), false)
        send(command(TYPE_HEALTH, HEALTH_TODAY).message(10, ProtoWriter().message(5, ProtoWriter().uint(1, 0))).toByteArray(), false)
    }

    private fun onFile(data: ByteArray) {
        val requested = fetching ?: return
        val id = if (data.size >= XiaomiFileId.SIZE) XiaomiFileId.from(data) else null
        if (id != null && !id.isDailyDetails) {
            log("file ${id.flags}v${id.version} ${data.size}B: " + data.take(HEX_BYTES).joinToString("") { "%02x".format(it) })
        }
        val file = when {
            id == null -> null
            id.isSleep -> XiaomiSleepDetails.parse(id, data)?.let { BandFile(requested, emptyList(), it) }
            id.isManualSamples -> BandFile(requested, XiaomiManualSamples.parse(id, data), samplesOnly = true)
            else -> XiaomiDailyDetails.minutes(id, data)?.let { BandFile(requested, it) }
        }
        if (file == null) {
            fetchNext()
            return
        }
        fetching = null
        pendingStore.addLast(file)
    }

    private fun fetchNext() {
        val id = fileIds.filter { it.isWanted }.minByOrNull { it.epochSeconds }
        fetching = id
        if (id == null) {
            result = BandResult.Ok
            return
        }
        fileIds.remove(id)
        send(command(TYPE_HEALTH, HEALTH_REQUEST).message(10, ProtoWriter().bytes(2, id.toBytes())).toByteArray(), false)
    }

    private fun timeCommand(): ByteArray {
        val now = Calendar.getInstance()
        val zone = TimeZone.getDefault()
        val clock = ProtoWriter()
            .message(1, ProtoWriter().uint(1, now.get(Calendar.YEAR).toLong()).uint(2, now.get(Calendar.MONTH) + 1L).uint(3, now.get(Calendar.DATE).toLong()))
            .message(
                2,
                ProtoWriter()
                    .uint(1, now.get(Calendar.HOUR_OF_DAY).toLong())
                    .uint(2, now.get(Calendar.MINUTE).toLong())
                    .uint(3, now.get(Calendar.SECOND).toLong())
                    .uint(4, now.get(Calendar.MILLISECOND).toLong()),
            )
            .message(
                3,
                ProtoWriter()
                    .sint(1, now.get(Calendar.ZONE_OFFSET) / 1000 / 60 / 15)
                    .sint(2, now.get(Calendar.DST_OFFSET) / 1000 / 60 / 15)
                    .string(3, zone.id),
            )
            .bool(4, phone.use12Hour)
        return command(TYPE_SYSTEM, SYSTEM_CLOCK).message(4, ProtoWriter().message(4, clock)).toByteArray()
    }

    private fun command(type: Int, subtype: Int) = ProtoWriter().uint(1, type.toLong()).uint(2, subtype.toLong())

    companion object {
        const val TYPE_AUTH = 1
        private const val TYPE_SYSTEM = 2
        private const val TYPE_HEALTH = 8
        private const val AUTH_USER_ID = 5
        private const val AUTH_NONCE = 26
        private const val AUTH_STEP3 = 27
        private const val SYSTEM_CLOCK = 3
        private const val HEALTH_TODAY = 1
        private const val HEALTH_PAST = 2
        private const val HEALTH_REQUEST = 3
        private const val HEALTH_ACK = 5
        private const val HEX_BYTES = 160
        private const val DEFAULT_USER_ID = "0000000000"
    }
}
