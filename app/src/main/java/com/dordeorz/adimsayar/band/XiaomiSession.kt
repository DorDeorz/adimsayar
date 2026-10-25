package com.dordeorz.adimsayar.band

import java.security.SecureRandom
import java.util.Calendar
import java.util.TimeZone

enum class BandResult { Ok, BluetoothOff, NoPermission, ConnectFailed, Unsupported, AuthFailed, Timeout }

class BandFile(val id: XiaomiFileId, val minutes: List<BandMinute>, val sleep: BandSleepRecord? = null, val samplesOnly: Boolean = false)

class XiaomiPhoneInfo(val apiLevel: Int, val model: String, val region: String, val use12Hour: Boolean)

/** A phone notification as the band shows it. [id] is ours; [key] is the Android notification key. */
class BandNotice(val id: Int, val packageName: String, val appName: String, val title: String, val body: String, val whenMs: Long, val key: String?)

/** Now-playing state sent to the band. [state] is 0 nothing, 1 playing, 2 paused; [volume] is 0–100. */
class BandMusic(val state: Int, val volume: Int, val track: String = "", val artist: String = "", val positionSec: Int = 0, val durationSec: Int = 0)

/** Something the user did on the band while the connection stays open. */
sealed interface BandEvent {
    class Dismiss(val ids: List<Int>) : BandEvent
    data object RejectCall : BandEvent
    data object IgnoreCall : BandEvent
    data object MusicRequest : BandEvent
    class MusicButton(val button: Int, val volume: Int) : BandEvent
    /** Find phone pressed on the band: [start] true to ring, false to stop. */
    class FindPhone(val start: Boolean) : BandEvent
    /** "Found it" pressed on the band while it was ringing for us. */
    data object BandFound : BandEvent
    /** The band asks for fresh weather. */
    data object WeatherRequest : BandEvent
    /** Reject the call with this text message, chosen on the band. */
    class CallReply(val number: String, val message: String) : BandEvent

    companion object {
        const val PLAY = 0
        const val PAUSE = 1
        const val PREVIOUS = 3
        const val NEXT = 4
        const val VOLUME = 5
    }
}

/** Watch face upload progress: [percent] while sending, then [done] with the outcome. */
sealed interface WatchfaceUpload {
    class Sending(val percent: Int) : WatchfaceUpload
    class Done(val success: Boolean) : WatchfaceUpload
}

/** Hooks for a connection that stays open after the first sync. */
class XiaomiLive(val shouldSync: () -> Boolean, val onSynced: suspend (BandResult) -> Unit)

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

    /** Band-side actions (dismissed notification, music button, call reject) on a live connection. */
    var onEvent: (BandEvent) -> Unit = {}

    /** The band's quick replies for rejected calls, with how many it needs and allows. */
    var onReplies: (replies: List<String>, min: Int, max: Int) -> Unit = { _, _, _ -> }

    /** The band's alarms and how many it can hold, after a get or any change. */
    var onAlarms: (alarms: List<BandAlarm>, max: Int) -> Unit = { _, _ -> }

    /** Battery level 0–100 and whether it is charging (null when the band did not say). */
    var onBattery: (level: Int, charging: Boolean?) -> Unit = { _, _ -> }

    /**
     * Sends one data upload part on the link's upload channel and reports whether the band took it.
     * Null when the link has no upload channel.
     */
    var sendData: ((ByteArray, (Boolean) -> Unit) -> Unit)? = null

    /** The band's watch faces, after a list request or any change. */
    var onWatchfaces: (List<BandWatchface>) -> Unit = {}

    var onUpload: (WatchfaceUpload) -> Unit = {}

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
    // Weather places on the band (key to name); null until it answered, empty when it keeps only one.
    private var weatherPlaces: List<Pair<String, String>>? = null
    private var reported = false
    private var installing: WatchfaceFile? = null
    private var uploadParts: List<ByteArray> = emptyList()
    private var nextPart = 0
    private var waitingPart = false
    private var pumpingParts = false
    private var lastPercent = -1

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
    suspend fun loop(pump: suspend () -> Boolean?, store: suspend (BandFile) -> Boolean, live: XiaomiLive? = null): BandResult {
        while (true) {
            val done = result
            if (done != null) {
                if (live == null || done != BandResult.Ok) return done
                if (!reported) {
                    reported = true
                    live.onSynced(done)
                }
                if (pump() == null) return BandResult.Ok
                if (live.shouldSync()) requestSync()
                continue
            }
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
    }

    /** Starts another file list/fetch cycle on an open connection; no-op while one runs. */
    fun requestSync() {
        if (!authenticated || result == null) return
        result = null
        reported = false
        fileIds.clear()
        todayIds.clear()
        fetching = null
        requestBattery()
        send(command(TYPE_HEALTH, HEALTH_TODAY).message(10, ProtoWriter().message(5, ProtoWriter().uint(1, 0))).toByteArray(), false)
    }

    fun requestBattery() {
        if (!authenticated) return
        send(command(TYPE_SYSTEM, SYSTEM_BATTERY).toByteArray(), false)
    }

    /** Makes the band vibrate until "found" is pressed on it, or stops that. */
    fun findBand(start: Boolean) {
        if (!authenticated) return
        send(command(TYPE_SYSTEM, SYSTEM_FIND_BAND).message(4, ProtoWriter().uint(5, if (start) 0 else 1)).toByteArray(), false)
    }

    /** Sends current weather and the daily and hourly forecast for one place. */
    fun sendWeather(report: BandWeatherReport) {
        if (!authenticated) return
        val places = weatherPlaces.orEmpty()
        // Other places (for example left by Mi Fitness) would have no data and show "turn on Bluetooth".
        val others = places.filter { it.first != report.key }
        if (others.isNotEmpty()) {
            send(command(XiaomiWeather.TYPE, XiaomiWeather.REMOVE_LOCATIONS).message(12, XiaomiWeather.locationList(others)).toByteArray(), false)
        }
        if (places.none { it.first == report.key }) {
            send(command(XiaomiWeather.TYPE, XiaomiWeather.ADD_LOCATION).message(12, ProtoWriter().message(5, XiaomiWeather.location(report))).toByteArray(), false)
        }
        if (weatherPlaces != null && weatherPlaces != listOf(report.key to report.place)) {
            send(command(XiaomiWeather.TYPE, XiaomiWeather.SET_LOCATIONS).message(12, XiaomiWeather.locationList(listOf(report.key to report.place))).toByteArray(), false)
        }
        weatherPlaces = listOf(report.key to report.place)
        send(command(XiaomiWeather.TYPE, XiaomiWeather.CURRENT).message(12, XiaomiWeather.current(report)).toByteArray(), false)
        send(command(XiaomiWeather.TYPE, XiaomiWeather.DAILY).message(12, XiaomiWeather.daily(report)).toByteArray(), false)
        send(command(XiaomiWeather.TYPE, XiaomiWeather.HOURLY).message(12, XiaomiWeather.hourly(report)).toByteArray(), false)
        log("weather sent: ${report.key} removed ${others.map { it.first }}")
    }

    fun requestAlarms() {
        if (!authenticated) return
        send(command(XiaomiSchedule.TYPE, XiaomiSchedule.ALARMS_GET).toByteArray(), false)
    }

    /** Creates the alarm when its id is 0, otherwise changes it; the list is read again after. */
    fun saveAlarm(alarm: BandAlarm) {
        if (!authenticated) return
        val (subtype, body) = if (alarm.id == 0) XiaomiSchedule.ALARMS_CREATE to XiaomiSchedule.create(alarm) else XiaomiSchedule.ALARMS_EDIT to XiaomiSchedule.edit(alarm)
        send(command(XiaomiSchedule.TYPE, subtype).message(19, body).toByteArray(), false)
        requestAlarms()
    }

    fun deleteAlarms(ids: List<Int>) {
        if (!authenticated || ids.isEmpty()) return
        send(command(XiaomiSchedule.TYPE, XiaomiSchedule.ALARMS_DELETE).message(19, XiaomiSchedule.delete(ids)).toByteArray(), false)
        requestAlarms()
    }

    /** Replaces the band's calendar with [events]; null turns the band's calendar off. */
    fun sendCalendar(events: List<BandCalendarEvent>?) {
        if (!authenticated) return
        send(command(XiaomiSchedule.CALENDAR_TYPE, XiaomiSchedule.CALENDAR_SET).message(14, XiaomiSchedule.calendar(events)).toByteArray(), false)
    }

    fun requestWatchfaces() {
        if (!authenticated) return
        send(command(XiaomiWatchface.TYPE, XiaomiWatchface.LIST).toByteArray(), false)
    }

    fun setWatchface(id: String) {
        if (!authenticated) return
        send(command(XiaomiWatchface.TYPE, XiaomiWatchface.SET).message(6, XiaomiWatchface.byId(id)).toByteArray(), false)
    }

    fun deleteWatchface(id: String) {
        if (!authenticated) return
        send(command(XiaomiWatchface.TYPE, XiaomiWatchface.DELETE).message(6, XiaomiWatchface.byId(id)).toByteArray(), false)
    }

    /** Starts installing [file]; false when the link cannot upload or another upload runs. */
    fun installWatchface(file: WatchfaceFile): Boolean {
        if (!authenticated || sendData == null || installing != null) return false
        installing = file
        lastPercent = -1
        onUpload(WatchfaceUpload.Sending(0))
        log("watchface install ${file.id} (${file.bytes.size} bytes)")
        send(command(XiaomiWatchface.TYPE, XiaomiWatchface.INSTALL).message(6, XiaomiWatchface.installStart(file)).toByteArray(), false)
        return true
    }

    /** Replaces the band's quick replies; padded to [min] and cut to [max] as the band requires. */
    fun setReplies(replies: List<String>, min: Int, max: Int) {
        if (!authenticated || max <= 0) return
        val list = replies.filter { it.isNotBlank() }.take(max).toMutableList()
        while (list.size < min) list += "-"
        val canned = ProtoWriter().uint(1, min.toLong())
        list.forEach { canned.string(2, it) }
        canned.uint(3, max.toLong())
        sendNotificationCommand(NOTIFICATION_REPLIES_SET, ProtoWriter().message(9, canned))
    }

    /** Tells the band whether the text message for a rejected call went out. */
    fun replySent(success: Boolean) {
        if (!authenticated) return
        sendNotificationCommand(NOTIFICATION_REPLY_ACK, ProtoWriter().uint(13, if (success) 0 else 1))
    }

    /** Sets the band's menu language, e.g. "tr_tr". */
    fun setLanguage(code: String) {
        if (!authenticated) return
        send(command(TYPE_SYSTEM, SYSTEM_LANGUAGE).message(4, ProtoWriter().message(20, ProtoWriter().string(1, code))).toByteArray(), false)
    }

    /** Tells the band the phone stopped ringing, so its find phone screen closes. */
    fun findPhoneStopped() {
        if (!authenticated) return
        send(command(TYPE_SYSTEM, SYSTEM_FIND_PHONE).message(4, ProtoWriter().uint(5, 1)).toByteArray(), false)
    }

    fun sendNotification(notice: BandNotice) {
        if (!authenticated) return
        val n3 = ProtoWriter()
            .string(1, notice.packageName)
            .string(2, notice.appName.ifEmpty { notice.packageName })
        if (notice.title.isNotEmpty()) n3.string(3, notice.title)
        n3.string(4, "")
        if (notice.body.isNotEmpty()) n3.string(5, notice.body)
        n3.string(6, timestamp(notice.whenMs)).uint(7, notice.id.toLong())
        notice.key?.let { n3.string(12, it) }
        sendNotificationCommand(NOTIFICATION_SEND, ProtoWriter().message(3, ProtoWriter().message(1, n3)))
    }

    fun dismissNotification(id: Int, packageName: String, key: String?) {
        if (!authenticated) return
        val nid = ProtoWriter().uint(1, id.toLong()).string(2, packageName)
        key?.let { nid.string(4, it) }
        sendNotificationCommand(NOTIFICATION_DISMISS, ProtoWriter().message(4, ProtoWriter().message(1, nid)))
    }

    fun sendCall(name: String, number: String) {
        if (!authenticated) return
        val n3 = ProtoWriter()
            .string(1, CALL_PACKAGE)
            .string(2, CALL_PACKAGE)
            .string(3, name.ifEmpty { "?" })
            .string(4, "")
            .string(5, number.ifEmpty { " " })
            .string(6, timestamp(System.currentTimeMillis()))
            .uint(7, 0)
            .bool(8, true)
            .bool(11, false)
        sendNotificationCommand(NOTIFICATION_SEND, ProtoWriter().message(3, ProtoWriter().message(1, n3)))
    }

    fun endCall() = dismissNotification(0, CALL_PACKAGE, null)

    fun sendMusic(music: BandMusic) {
        if (!authenticated) return
        val info = ProtoWriter().uint(1, music.state.toLong()).uint(2, music.volume.toLong())
        if (music.state != 0) {
            info.string(4, music.track).string(5, music.artist).uint(6, music.positionSec.toLong()).uint(7, music.durationSec.toLong())
        }
        send(command(TYPE_MUSIC, MUSIC_SEND).message(20, ProtoWriter().message(1, info)).toByteArray(), false)
    }

    private fun sendNotificationCommand(subtype: Int, notification: ProtoWriter) {
        send(command(TYPE_NOTIFICATION, subtype).message(9, notification).toByteArray(), false)
    }

    private fun timestamp(ms: Long): String = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss", java.util.Locale.ROOT).format(java.util.Date(ms))

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
            type == TYPE_NOTIFICATION && subtype == NOTIFICATION_DISMISS -> {
                val ids = cmd.message(9)?.message(4)?.messages(1).orEmpty().mapNotNull { it.uint(1)?.toInt() }
                onEvent(BandEvent.Dismiss(ids))
            }
            type == TYPE_NOTIFICATION && subtype == NOTIFICATION_CALL_REJECT -> onEvent(BandEvent.RejectCall)
            type == TYPE_NOTIFICATION && subtype == NOTIFICATION_CALL_IGNORE -> onEvent(BandEvent.IgnoreCall)
            type == TYPE_NOTIFICATION && subtype == NOTIFICATION_REPLIES_GET -> cmd.message(9)?.message(9)?.let { canned ->
                onReplies(canned.strings(2), canned.uint(1)?.toInt() ?: 0, canned.uint(3)?.toInt() ?: 0)
            }
            type == TYPE_NOTIFICATION && subtype == NOTIFICATION_CALL_REPLY -> cmd.message(9)?.message(12)?.let { reply ->
                onEvent(BandEvent.CallReply(reply.string(4).orEmpty(), reply.string(2).orEmpty()))
            }
            type == TYPE_SYSTEM && subtype == SYSTEM_BATTERY -> cmd.message(4)?.message(2)?.message(1)?.let { battery ->
                val level = battery.uint(1)?.toInt() ?: return@let
                val charging = when (battery.uint(2)?.toInt()) {
                    1 -> true
                    2, 3 -> false
                    else -> null
                }
                onBattery(level.coerceIn(0, 100), charging)
            }
            type == TYPE_SYSTEM && subtype == SYSTEM_DEVICE_STATE_GET -> {
                val state = cmd.message(4)?.message(48)
                val level = state?.uint(2)?.toInt()
                if (level != null) onBattery(level.coerceIn(0, 100), state?.uint(1) == 1L) else requestBattery()
            }
            type == TYPE_SYSTEM && subtype == SYSTEM_DEVICE_STATE -> requestBattery()
            type == TYPE_SYSTEM && subtype == SYSTEM_FIND_PHONE -> cmd.message(4)?.uint(5)?.let { onEvent(BandEvent.FindPhone(it == 0L)) }
            type == TYPE_SYSTEM && subtype == SYSTEM_FIND_BAND -> if (cmd.message(4)?.uint(5) == 1L) onEvent(BandEvent.BandFound)
            type == XiaomiWeather.TYPE && subtype == XiaomiWeather.REQUEST -> onEvent(BandEvent.WeatherRequest)
            type == XiaomiWeather.TYPE && subtype == XiaomiWeather.GET_LOCATIONS -> {
                weatherPlaces = if (cmd.uint(STATUS) == 1L) emptyList() else XiaomiWeather.locations(cmd.message(12))
                log("weather places: $weatherPlaces")
            }
            type == XiaomiWatchface.TYPE -> onWatchfaceCommand(subtype, cmd)
            type == XiaomiWatchface.UPLOAD_TYPE && subtype == XiaomiWatchface.UPLOAD_START -> onUploadStart(cmd)
            type == XiaomiWeather.TYPE -> log("weather $subtype status ${cmd.uint(STATUS)}")
            type == XiaomiSchedule.TYPE && subtype == XiaomiSchedule.ALARMS_GET -> {
                val (alarms, max) = XiaomiSchedule.alarms(cmd.message(19))
                onAlarms(alarms, max)
            }
            type == TYPE_MUSIC && subtype == MUSIC_GET -> onEvent(BandEvent.MusicRequest)
            type == TYPE_MUSIC && subtype == MUSIC_BUTTON -> {
                val key = cmd.message(20)?.message(2)
                key?.uint(1)?.let { onEvent(BandEvent.MusicButton(it.toInt(), key.uint(2)?.toInt() ?: 0)) }
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

    private fun onWatchfaceCommand(subtype: Int?, cmd: ProtoMessage) {
        when (subtype) {
            XiaomiWatchface.LIST -> onWatchfaces(XiaomiWatchface.list(cmd))
            XiaomiWatchface.SET, XiaomiWatchface.DELETE -> requestWatchfaces()
            XiaomiWatchface.INSTALL -> {
                val file = installing ?: return
                when (val status = cmd.message(6)?.uint(5)?.toInt()) {
                    0 -> send(command(XiaomiWatchface.UPLOAD_TYPE, XiaomiWatchface.UPLOAD_START).message(24, XiaomiWatchface.uploadRequest(file.bytes)).toByteArray(), false)
                    // Already on the band: just switch to it.
                    2 -> finishUpload(true)
                    else -> {
                        log("watchface install status $status")
                        finishUpload(false)
                    }
                }
            }
        }
    }

    private fun onUploadStart(cmd: ProtoMessage) {
        val file = installing ?: return
        val ack = cmd.message(24)?.message(2)
        if (ack == null || (ack.uint(2) ?: 0L) != 0L || (ack.uint(4) ?: 0L) != 0L) {
            log("upload refused: ${ack?.uint(2)} ${ack?.uint(4)}")
            finishUpload(false)
            return
        }
        val chunk = ack.uint(5)?.toInt()?.takeIf { it > 4 } ?: XiaomiWatchface.DEFAULT_CHUNK
        uploadParts = XiaomiWatchface.parts(XiaomiWatchface.UPLOAD_WATCHFACE, file.bytes, chunk)
        nextPart = 0
        waitingPart = false
        log("upload ${uploadParts.size} parts of $chunk")
        pumpParts()
    }

    // A link may report a part as sent right away, so parts go out in a loop rather than recursively.
    private fun pumpParts() {
        val sender = sendData ?: return finishUpload(false)
        if (pumpingParts) return
        pumpingParts = true
        while (installing != null && !waitingPart && nextPart < uploadParts.size) {
            val index = nextPart++
            waitingPart = true
            sender(uploadParts[index]) { ok -> onPartSent(index, ok) }
        }
        pumpingParts = false
    }

    private fun onPartSent(index: Int, ok: Boolean) {
        waitingPart = false
        if (installing == null) return
        if (!ok) {
            log("upload part ${index + 1} refused")
            finishUpload(false)
            return
        }
        val percent = (index + 1) * 100 / uploadParts.size
        if (percent != lastPercent) {
            lastPercent = percent
            onUpload(WatchfaceUpload.Sending(percent))
        }
        if (index == uploadParts.lastIndex) finishUpload(true) else pumpParts()
    }

    private fun finishUpload(success: Boolean) {
        val file = installing ?: return
        installing = null
        uploadParts = emptyList()
        nextPart = 0
        waitingPart = false
        log("watchface ${file.id} ${if (success) "installed" else "failed"}")
        if (success) setWatchface(file.id)
        onUpload(WatchfaceUpload.Done(success))
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
        send(command(TYPE_SYSTEM, SYSTEM_DEVICE_STATE_GET).toByteArray(), false)
        sendNotificationCommand(NOTIFICATION_REPLIES_GET, ProtoWriter().uint(8, 1))
        send(command(XiaomiWeather.TYPE, XiaomiWeather.GET_LOCATIONS).toByteArray(), false)
        requestAlarms()
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
        private const val TYPE_NOTIFICATION = 7
        private const val TYPE_HEALTH = 8
        private const val TYPE_MUSIC = 18
        private const val NOTIFICATION_SEND = 0
        private const val NOTIFICATION_DISMISS = 1
        private const val NOTIFICATION_CALL_REJECT = 2
        private const val NOTIFICATION_CALL_IGNORE = 5
        private const val NOTIFICATION_REPLIES_GET = 9
        private const val NOTIFICATION_REPLIES_SET = 12
        private const val NOTIFICATION_CALL_REPLY = 13
        private const val NOTIFICATION_REPLY_ACK = 14
        private const val MUSIC_GET = 0
        private const val MUSIC_SEND = 1
        private const val MUSIC_BUTTON = 2
        private const val CALL_PACKAGE = "phone"
        private const val AUTH_USER_ID = 5
        private const val AUTH_NONCE = 26
        private const val AUTH_STEP3 = 27
        private const val SYSTEM_BATTERY = 1
        private const val SYSTEM_CLOCK = 3
        private const val SYSTEM_LANGUAGE = 6
        private const val SYSTEM_FIND_PHONE = 17
        private const val SYSTEM_FIND_BAND = 18
        private const val SYSTEM_DEVICE_STATE_GET = 78
        private const val SYSTEM_DEVICE_STATE = 79
        private const val HEALTH_TODAY = 1
        private const val HEALTH_PAST = 2
        private const val HEALTH_REQUEST = 3
        private const val HEALTH_ACK = 5
        private const val HEX_BYTES = 160
        private const val STATUS = 100
        private const val DEFAULT_USER_ID = "0000000000"
    }
}
