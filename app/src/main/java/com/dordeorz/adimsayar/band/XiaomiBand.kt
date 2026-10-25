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
import android.text.format.DateFormat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

enum class BandResult { Ok, BluetoothOff, NoPermission, ConnectFailed, Unsupported, AuthFailed, Timeout }

class BandFile(val id: XiaomiFileId, val minutes: List<Pair<Long, Int>>)

@SuppressLint("MissingPermission")
class XiaomiBand(private val context: Context, private val address: String, private val secret: ByteArray) {

    private sealed interface Event {
        data class Connection(val connected: Boolean) : Event
        data object Mtu : Event
        data class Services(val ok: Boolean) : Event
        data object Written : Event
        data class Changed(val uuid: UUID, val value: ByteArray) : Event
    }

    private sealed interface Op {
        class Write(val characteristic: BluetoothGattCharacteristic, val value: ByteArray) : Op
        class Descriptor(val descriptor: BluetoothGattDescriptor) : Op
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val ops = ArrayDeque<Op>()
    private var busy = false
    private lateinit var gatt: BluetoothGatt

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            events.trySend(Event.Connection(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED))
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) maxWrite = mtu - 3
            events.trySend(Event.Mtu)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            events.trySend(Event.Services(status == BluetoothGatt.GATT_SUCCESS))
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

    private var maxWrite = XiaomiChannel.DEFAULT_MAX_WRITE
    private val cipher = XiaomiCipher()
    private val phoneNonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
    private val assembler = XiaomiFileAssembler()
    private val fileIds = ArrayList<XiaomiFileId>()
    private val todayIds = HashSet<XiaomiFileId>()
    private var fetching: XiaomiFileId? = null
    private var result: BandResult? = null
    private val pendingStore = ArrayDeque<BandFile>()

    suspend fun sync(store: suspend (BandFile) -> Boolean): BandResult {
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
            withTimeoutOrNull(TOTAL_TIMEOUT_MS) { run(store) } ?: BandResult.Timeout
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

    private suspend fun run(store: suspend (BandFile) -> Boolean): BandResult {
        if ((withTimeoutOrNull(STEP_TIMEOUT_MS) { next<Event.Connection>() })?.connected != true) return BandResult.ConnectFailed
        delay(SETTLE_MS)
        if (!discover()) return BandResult.ConnectFailed
        if (gatt.requestMtu(512)) withTimeoutOrNull(MTU_TIMEOUT_MS) { next<Event.Mtu>() }
        val service = gatt.getService(SERVICE) ?: return BandResult.Unsupported
        val readChar = service.getCharacteristic(CHAR_READ) ?: return BandResult.Unsupported
        val writeChar = service.getCharacteristic(CHAR_WRITE) ?: return BandResult.Unsupported
        val activityChar = service.getCharacteristic(CHAR_ACTIVITY) ?: return BandResult.Unsupported

        val writeChannel = XiaomiChannel(cipher, { enqueue(Op.Write(writeChar, it)) }, {})
        val readChannel = XiaomiChannel(cipher, { enqueue(Op.Write(readChar, it)) }) { handleCommand(it, writeChannel) }
        val activityChannel = XiaomiChannel(cipher, { enqueue(Op.Write(activityChar, it)) }) { chunk ->
            assembler.add(chunk)?.let { onFile(it, writeChannel) }
        }
        val channels = mapOf(readChar.uuid to readChannel, writeChar.uuid to writeChannel, activityChar.uuid to activityChannel)
        channels.values.forEach { it.maxWrite = maxWrite }
        for (c in listOf(writeChar, readChar, activityChar)) {
            gatt.setCharacteristicNotification(c, true)
            c.getDescriptor(CCCD)?.let { enqueue(Op.Descriptor(it)) }
        }
        writeChannel.send(
            command(TYPE_AUTH, AUTH_NONCE).message(3, ProtoWriter().message(30, ProtoWriter().bytes(1, phoneNonce))).toByteArray(),
        )

        while (result == null) {
            val event = withTimeoutOrNull(STEP_TIMEOUT_MS) { events.receive() }
            if (event == null) {
                if (cipher.sending) {
                    fetchNext(writeChannel)
                    continue
                }
                return if (cipher.keys == null) BandResult.Timeout else BandResult.AuthFailed
            }
            when (event) {
                is Event.Written -> {
                    busy = false
                    drain()
                }
                is Event.Changed -> channels[event.uuid]?.onNotification(event.value)
                is Event.Connection -> if (!event.connected) return if (cipher.sending) BandResult.Timeout else BandResult.ConnectFailed
                else -> Unit
            }
            if (pendingStore.isNotEmpty()) {
                val file = pendingStore.removeFirst()
                if (store(file) && file.id !in todayIds) {
                    writeChannel.send(
                        command(TYPE_HEALTH, HEALTH_ACK).message(10, ProtoWriter().bytes(3, file.id.toBytes())).toByteArray(),
                    )
                }
                fetchNext(writeChannel)
            }
        }
        withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
            while (busy || ops.isNotEmpty() || !writeChannel.idle) {
                when (val event = events.receive()) {
                    is Event.Written -> {
                        busy = false
                        drain()
                    }
                    is Event.Changed -> channels[event.uuid]?.onNotification(event.value)
                    is Event.Connection -> if (!event.connected) break
                    else -> Unit
                }
            }
        }
        return result ?: BandResult.Ok
    }

    private suspend fun discover(): Boolean {
        repeat(DISCOVER_TRIES) {
            if (gatt.discoverServices()) {
                val event = withTimeoutOrNull(DISCOVER_TIMEOUT_MS) { next<Event.Services>() }
                if (event?.ok == true) return true
            }
            if (gatt.getService(SERVICE) != null) return true
            delay(SETTLE_MS)
        }
        return false
    }

    private fun handleCommand(bytes: ByteArray, writeChannel: XiaomiChannel) {
        val cmd = ProtoMessage.parse(bytes) ?: return
        val type = cmd.uint(1)?.toInt()
        val subtype = cmd.uint(2)?.toInt()
        when {
            type == TYPE_AUTH && subtype == AUTH_NONCE -> {
                val watch = cmd.message(3)?.message(31)
                val watchNonce = watch?.bytes(1)
                val watchHmac = watch?.bytes(2)
                if (watchNonce == null || watchHmac == null) {
                    result = BandResult.AuthFailed
                    return
                }
                val keys = XiaomiCrypto.deriveKeys(secret, phoneNonce, watchNonce)
                if (!XiaomiCrypto.hmac(keys.decryptionKey, watchNonce + phoneNonce).contentEquals(watchHmac)) {
                    result = BandResult.AuthFailed
                    return
                }
                cipher.keys = keys
                val info = ProtoWriter()
                    .uint(1, 0)
                    .float(2, Build.VERSION.SDK_INT.toFloat())
                    .string(3, Build.MODEL)
                    .uint(4, 224)
                    .string(5, Locale.getDefault().language.take(2).uppercase(Locale.ROOT))
                    .toByteArray()
                val step3 = ProtoWriter()
                    .bytes(1, XiaomiCrypto.hmac(keys.encryptionKey, phoneNonce + watchNonce))
                    .bytes(2, XiaomiCrypto.encrypt(keys.encryptionKey, XiaomiCrypto.packetNonce(keys.encryptionNonce, 0), info))
                writeChannel.send(command(TYPE_AUTH, AUTH_STEP3).message(3, ProtoWriter().message(32, step3)).toByteArray())
            }
            type == TYPE_AUTH && subtype == AUTH_STEP3 -> {
                cipher.sending = true
                writeChannel.reset()
                writeChannel.send(timeCommand())
                writeChannel.send(
                    command(TYPE_HEALTH, HEALTH_TODAY).message(10, ProtoWriter().message(5, ProtoWriter().uint(1, 0))).toByteArray(),
                )
            }
            type == TYPE_HEALTH && subtype == HEALTH_TODAY -> {
                cmd.message(10)?.bytes(2)?.let {
                    val ids = XiaomiFileId.list(it)
                    fileIds += ids
                    todayIds += ids
                }
                writeChannel.send(command(TYPE_HEALTH, HEALTH_PAST).toByteArray())
            }
            type == TYPE_HEALTH && subtype == HEALTH_PAST -> {
                cmd.message(10)?.bytes(2)?.let { fileIds += XiaomiFileId.list(it) }
                fetchNext(writeChannel)
            }
        }
    }

    private fun onFile(data: ByteArray, writeChannel: XiaomiChannel) {
        val requested = fetching ?: return
        val minutes = if (data.size >= XiaomiFileId.SIZE) XiaomiDailyDetails.minuteSteps(XiaomiFileId.from(data), data) else null
        if (minutes == null) {
            fetchNext(writeChannel)
            return
        }
        fetching = null
        pendingStore.addLast(BandFile(requested, minutes))
    }

    private fun fetchNext(writeChannel: XiaomiChannel) {
        val id = fileIds.filter { it.isDailyDetails }.minByOrNull { it.epochSeconds }
        fetching = id
        if (id == null) {
            result = BandResult.Ok
            return
        }
        fileIds.remove(id)
        writeChannel.send(command(TYPE_HEALTH, HEALTH_REQUEST).message(10, ProtoWriter().bytes(2, id.toBytes())).toByteArray())
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
            .bool(4, !DateFormat.is24HourFormat(context))
        return command(TYPE_SYSTEM, SYSTEM_CLOCK).message(4, ProtoWriter().message(4, clock)).toByteArray()
    }

    private fun command(type: Int, subtype: Int) = ProtoWriter().uint(1, type.toLong()).uint(2, subtype.toLong())

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
        private val SERVICE = UUID.fromString("0000fe95-0000-1000-8000-00805f9b34fb")
        private val CHAR_READ = UUID.fromString("00000051-0000-1000-8000-00805f9b34fb")
        private val CHAR_WRITE = UUID.fromString("00000052-0000-1000-8000-00805f9b34fb")
        private val CHAR_ACTIVITY = UUID.fromString("00000053-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TYPE_AUTH = 1
        private const val TYPE_SYSTEM = 2
        private const val TYPE_HEALTH = 8
        private const val AUTH_NONCE = 26
        private const val AUTH_STEP3 = 27
        private const val SYSTEM_CLOCK = 3
        private const val HEALTH_TODAY = 1
        private const val HEALTH_PAST = 2
        private const val HEALTH_REQUEST = 3
        private const val HEALTH_ACK = 5
        private const val TOTAL_TIMEOUT_MS = 120_000L
        private const val STEP_TIMEOUT_MS = 15_000L
        private const val MTU_TIMEOUT_MS = 5_000L
        private const val FLUSH_TIMEOUT_MS = 3_000L
        private const val SETTLE_MS = 600L
        private const val DISCOVER_TIMEOUT_MS = 6_000L
        private const val DISCOVER_TRIES = 3
    }
}
