package com.dordeorz.adimsayar.band

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Syncs an older Huami band or watch (Mi Band 2–6, Amazfit). [since] is where reading starts;
 * after a sync [nextSince] is the start of the hour of the last minute read. Also sends watch faces
 * to the models that take them ([BandModel.huamiFaces]).
 */
@SuppressLint("MissingPermission")
class HuamiBand(
    private val context: Context,
    private val address: String,
    private val model: BandModel,
    private val key: ByteArray,
    private val since: Long,
) {

    private sealed interface Event {
        data class Connection(val connected: Boolean) : Event
        data class Services(val ok: Boolean) : Event
        data class Mtu(val mtu: Int) : Event
        data object Written : Event
        data class Changed(val uuid: UUID, val value: ByteArray) : Event
    }

    private sealed interface Op {
        class Write(val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : Op
        class Descriptor(val descriptor: BluetoothGattDescriptor) : Op
    }

    var nextSince: Long = since
        private set
    var summary: String = ""
        private set

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val ops = ArrayDeque<Op>()
    private var busy = false
    private lateinit var gatt: BluetoothGatt

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            events.trySend(Event.Connection(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED))
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            events.trySend(Event.Services(status == BluetoothGatt.GATT_SUCCESS))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            events.trySend(Event.Mtu(if (status == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU))
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            events.trySend(Event.Written)
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            events.trySend(Event.Written)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            events.trySend(Event.Changed(characteristic.uuid, value.copyOf()))
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                val value = characteristic.value ?: return
                events.trySend(Event.Changed(characteristic.uuid, value.copyOf()))
            }
        }
    }

    suspend fun sync(store: suspend (BandFile) -> Boolean): BandResult {
        val result = connected(TOTAL_TIMEOUT_MS) { run(store) }
        Log.i(XiaomiBand.TAG, "huami ${model.name}: $result $summary")
        return result
    }

    /**
     * Sends a watch face file (see [HuamiFaceUpload]); [onProgress] gets 0–100. Ok only when the band
     * confirmed the whole file; [faceFailure] says why it refused one.
     */
    suspend fun uploadFace(bytes: ByteArray, onProgress: (Int) -> Unit): BandResult {
        val result = connected(UPLOAD_TIMEOUT_MS) { upload(bytes, onProgress) }
        Log.i(XiaomiBand.TAG, "huami ${model.name} face upload ${bytes.size} bytes: $result $faceFailure")
        return result
    }

    var faceFailure: HuamiFaceUpload.Failure? = null
        private set

    private suspend fun connected(timeoutMs: Long, body: suspend () -> BandResult): BandResult {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return BandResult.BluetoothOff
        val adapter = manager.adapter ?: return BandResult.BluetoothOff
        if (!adapter.isEnabled) return BandResult.BluetoothOff
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return BandResult.ConnectFailed
        }
        gatt = try {
            device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            return BandResult.NoPermission
        } ?: return BandResult.ConnectFailed
        return try {
            withTimeoutOrNull(timeoutMs) { body() } ?: BandResult.Timeout
        } catch (e: SecurityException) {
            BandResult.NoPermission
        } catch (e: IllegalStateException) {
            BandResult.ConnectFailed
        } finally {
            runCatching { gatt.disconnect() }
            runCatching { gatt.close() }
            events.close()
        }
    }

    private suspend fun upload(bytes: ByteArray, onProgress: (Int) -> Unit): BandResult {
        if ((withTimeoutOrNull(STEP_TIMEOUT_MS) { next<Event.Connection>() })?.connected != true) return BandResult.ConnectFailed
        delay(SETTLE_MS)
        if (!discover()) return BandResult.ConnectFailed
        val auth = find(CHAR_AUTH) ?: return BandResult.Unsupported
        val control = find(CHAR_FIRMWARE) ?: return BandResult.Unsupported
        val data = find(CHAR_FIRMWARE_DATA) ?: return BandResult.Unsupported
        val config = find(CHAR_CONFIG) ?: return BandResult.Unsupported
        val mtu = if (gatt.requestMtu(WANTED_MTU)) {
            withTimeoutOrNull(STEP_TIMEOUT_MS) { next<Event.Mtu>() }?.mtu ?: DEFAULT_MTU
        } else {
            DEFAULT_MTU
        }

        val face = HuamiFaceUpload(bytes, mtu - 3)
        face.writeControl = { enqueue(Op.Write(control, it)) }
        face.writeData = { enqueue(Op.Write(data, it)) }
        face.writeConfig = { enqueue(Op.Write(config, it)) }
        face.onProgress = onProgress
        val session = HuamiSession(key, model.huamiCrypt, model.huamiSampleSize, since)
        session.writeAuth = { enqueue(Op.Write(auth, it)) }
        session.onAuthenticated = { face.start() }
        for (c in listOf(auth, control, config)) {
            gatt.setCharacteristicNotification(c, true)
            c.getDescriptor(CCCD)?.let { enqueue(Op.Descriptor(it)) }
        }
        session.start()
        onProgress(0)

        while (!face.done) {
            face.failure?.let {
                faceFailure = it
                return BandResult.Unsupported
            }
            if (session.result == BandResult.AuthFailed) return BandResult.AuthFailed
            when (val event = withTimeoutOrNull(UPLOAD_STEP_TIMEOUT_MS) { events.receive() }) {
                null -> return if (session.answered && !session.authenticated) BandResult.AuthFailed else BandResult.Timeout
                is Event.Written -> {
                    busy = false
                    drain()
                }
                is Event.Changed -> when (event.uuid) {
                    CHAR_AUTH -> session.onAuth(event.value)
                    CHAR_FIRMWARE -> face.onControl(event.value)
                }
                is Event.Connection -> if (!event.connected) return BandResult.ConnectFailed
                else -> Unit
            }
        }
        return BandResult.Ok
    }

    private suspend fun run(store: suspend (BandFile) -> Boolean): BandResult {
        if ((withTimeoutOrNull(STEP_TIMEOUT_MS) { next<Event.Connection>() })?.connected != true) return BandResult.ConnectFailed
        delay(SETTLE_MS)
        if (!discover()) return BandResult.ConnectFailed
        val auth = find(CHAR_AUTH) ?: return BandResult.Unsupported
        val fetch = find(CHAR_FETCH) ?: return BandResult.Unsupported
        val data = find(CHAR_DATA) ?: return BandResult.Unsupported

        val session = HuamiSession(key, model.huamiCrypt, model.huamiSampleSize, since)
        session.writeAuth = { enqueue(Op.Write(auth, it)) }
        session.writeFetch = { enqueue(Op.Write(fetch, it)) }
        for (c in listOf(auth, fetch, data)) {
            gatt.setCharacteristicNotification(c, true)
            c.getDescriptor(CCCD)?.let { enqueue(Op.Descriptor(it)) }
        }
        session.start()

        while (session.result == null) {
            when (val event = withTimeoutOrNull(STEP_TIMEOUT_MS) { events.receive() }) {
                null -> {
                    if (session.minutes.isNotEmpty()) break
                    return if (session.answered && !session.authenticated) BandResult.AuthFailed else BandResult.Timeout
                }
                is Event.Written -> {
                    busy = false
                    drain()
                }
                is Event.Changed -> when (event.uuid) {
                    CHAR_AUTH -> session.onAuth(event.value)
                    CHAR_FETCH -> session.onFetch(event.value)
                    CHAR_DATA -> session.onData(event.value)
                }
                is Event.Connection -> if (!event.connected) {
                    if (session.minutes.isEmpty()) return if (session.authenticated) BandResult.Timeout else BandResult.ConnectFailed
                    break
                }
                else -> Unit
            }
        }
        if (session.result == BandResult.AuthFailed) return BandResult.AuthFailed
        val minutes = session.minutes
        summary = "${minutes.size} min"
        if (minutes.isEmpty()) return BandResult.Ok
        val stored = store(BandFile(XiaomiFileId(since, 0, 0, 0), minutes))
        if (stored) {
            nextSince = Instant.ofEpochSecond(minutes.last().epochSeconds).atZone(ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.HOURS).toEpochSecond()
        }
        return BandResult.Ok
    }

    private fun find(uuid: UUID): BluetoothGattCharacteristic? =
        gatt.services.orEmpty().firstNotNullOfOrNull { it.getCharacteristic(uuid) }

    private suspend fun discover(): Boolean {
        repeat(DISCOVER_TRIES) {
            if (gatt.discoverServices()) {
                val event = withTimeoutOrNull(DISCOVER_TIMEOUT_MS) { next<Event.Services>() }
                if (event?.ok == true) return true
            }
            if (find(CHAR_AUTH) != null) return true
            delay(SETTLE_MS)
        }
        return false
    }

    private fun enqueue(op: Op) {
        ops.addLast(op)
        drain()
    }

    private fun drain() {
        while (!busy) {
            val op = ops.removeFirstOrNull() ?: return
            busy = when (op) {
                is Op.Write -> write(op.characteristic, op.value)
                is Op.Descriptor -> writeDescriptor(op.descriptor)
            }
        }
    }

    private fun write(characteristic: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, characteristic.writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }

    private fun writeDescriptor(descriptor: BluetoothGattDescriptor): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }

    private suspend inline fun <reified T : Event> next(): T {
        while (true) {
            val event = events.receive()
            if (event is T) return event
            if (event is Event.Connection && !event.connected) throw IllegalStateException()
        }
    }

    companion object {
        private val CHAR_AUTH = UUID.fromString("00000009-0000-3512-2118-0009af100700")
        private val CHAR_FETCH = UUID.fromString("00000004-0000-3512-2118-0009af100700")
        private val CHAR_DATA = UUID.fromString("00000005-0000-3512-2118-0009af100700")
        private val CHAR_FIRMWARE = UUID.fromString("00001531-0000-3512-2118-0009af100700")
        private val CHAR_FIRMWARE_DATA = UUID.fromString("00001532-0000-3512-2118-0009af100700")
        private val CHAR_CONFIG = UUID.fromString("00000003-0000-3512-2118-0009af100700")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TOTAL_TIMEOUT_MS = 120_000L
        private const val STEP_TIMEOUT_MS = 15_000L
        private const val SETTLE_MS = 600L
        private const val DISCOVER_TIMEOUT_MS = 6_000L
        private const val DISCOVER_TRIES = 3
        private const val UPLOAD_TIMEOUT_MS = 10 * 60_000L
        private const val UPLOAD_STEP_TIMEOUT_MS = 30_000L
        private const val WANTED_MTU = 247
        private const val DEFAULT_MTU = 23
    }
}
