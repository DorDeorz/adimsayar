package com.dordeorz.adimsayar.band

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

@SuppressLint("MissingPermission")
class XiaomiBle(private val context: Context, private val device: BluetoothDevice, private val session: XiaomiSession) {

    private class UuidSet(val service: UUID, val read: UUID, val write: UUID, val activity: UUID, val encrypted: Boolean)

    private sealed interface Event {
        data class Connection(val connected: Boolean) : Event
        data object Mtu : Event
        data class Services(val ok: Boolean) : Event
        data object Written : Event
        data class Changed(val uuid: UUID, val value: ByteArray) : Event
        class Run(val block: () -> Unit) : Event
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

    /** Runs [block] on the connection's own coroutine, where the session may be used. */
    fun post(block: () -> Unit) {
        events.trySend(Event.Run(block))
    }

    suspend fun sync(store: suspend (BandFile) -> Boolean): BandResult = connect(store, null)

    /**
     * Keeps the connection open after the first sync until the band goes away. Uses autoConnect,
     * so Android waits for the band in the background without the app scanning.
     */
    suspend fun live(store: suspend (BandFile) -> Boolean, live: XiaomiLive): BandResult = connect(store, live)

    private suspend fun connect(store: suspend (BandFile) -> Boolean, live: XiaomiLive?): BandResult {
        gatt = try {
            device.connectGatt(context, live != null, callback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            return BandResult.NoPermission
        } ?: return BandResult.ConnectFailed
        return try {
            if (live != null) run(store, live) else withTimeoutOrNull(TOTAL_TIMEOUT_MS) { run(store, null) } ?: BandResult.Timeout
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

    private suspend fun run(store: suspend (BandFile) -> Boolean, live: XiaomiLive?): BandResult {
        val connected = if (live != null) next<Event.Connection>() else withTimeoutOrNull(STEP_TIMEOUT_MS) { next<Event.Connection>() }
        if (connected?.connected != true) return BandResult.ConnectFailed
        delay(SETTLE_MS)
        if (!discover()) return BandResult.ConnectFailed
        if (gatt.requestMtu(512)) withTimeoutOrNull(MTU_TIMEOUT_MS) { next<Event.Mtu>() }
        val set = UUID_SETS.firstOrNull { set ->
            gatt.getService(set.service)?.let { s -> listOf(set.read, set.write, set.activity).all { s.getCharacteristic(it) != null } } == true
        } ?: return BandResult.Unsupported
        val service = gatt.getService(set.service)
        val readChar = service.getCharacteristic(set.read)
        val writeChar = service.getCharacteristic(set.write)
        val activityChar = service.getCharacteristic(set.activity)
        val cipher = session.cipher

        val writeChannel = XiaomiChannel(cipher, { enqueue(Op.Write(writeChar, it)) }, {}, set.encrypted)
        val readChannel = XiaomiChannel(cipher, { enqueue(Op.Write(readChar, it)) }, session::onCommand, set.encrypted)
        val activityChannel = XiaomiChannel(cipher, { enqueue(Op.Write(activityChar, it)) }, session::onActivity, set.encrypted)
        val channels = mapOf(readChar.uuid to readChannel, writeChar.uuid to writeChannel, activityChar.uuid to activityChannel)
        channels.values.forEach { it.maxWrite = maxWrite }
        for (c in listOf(writeChar, readChar, activityChar)) {
            gatt.setCharacteristicNotification(c, true)
            c.getDescriptor(CCCD)?.let { enqueue(Op.Descriptor(it)) }
        }
        session.send = { bytes, _ -> writeChannel.send(bytes) }
        session.onAuthenticated = {
            if (set.encrypted) {
                cipher.sending = true
                writeChannel.reset()
            }
        }
        if (set.encrypted) session.startEncrypted() else session.startPlain()

        val result = session.loop(
            pump = {
                when (val event = withTimeoutOrNull(STEP_TIMEOUT_MS) { events.receive() }) {
                    null -> false
                    is Event.Written -> {
                        busy = false
                        drain()
                        true
                    }
                    is Event.Changed -> {
                        channels[event.uuid]?.onNotification(event.value)
                        true
                    }
                    is Event.Connection -> if (event.connected) true else null
                    is Event.Run -> {
                        event.block()
                        true
                    }
                    else -> true
                }
            },
            store = store,
            live = live,
        )
        if (result != BandResult.Ok || live != null) return result
        withTimeoutOrNull(FLUSH_TIMEOUT_MS) {
            while (busy || ops.isNotEmpty() || !writeChannel.idle) {
                when (val event = events.receive()) {
                    is Event.Written -> {
                        busy = false
                        drain()
                    }
                    is Event.Changed -> channels[event.uuid]?.onNotification(event.value)
                    is Event.Connection -> if (!event.connected) break
                    is Event.Run -> event.block()
                    else -> Unit
                }
            }
        }
        return BandResult.Ok
    }

    private suspend fun discover(): Boolean {
        repeat(DISCOVER_TRIES) {
            if (gatt.discoverServices()) {
                val event = withTimeoutOrNull(DISCOVER_TIMEOUT_MS) { next<Event.Services>() }
                if (event?.ok == true) return true
            }
            if (UUID_SETS.any { gatt.getService(it.service) != null }) return true
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
        private fun uuid(short: String) = UUID.fromString("0000$short-0000-1000-8000-00805f9b34fb")
        private val UUID_SETS = listOf(
            UuidSet(uuid("fe95"), uuid("0051"), uuid("0052"), uuid("0053"), encrypted = true),
            UuidSet(
                UUID.fromString("16186f00-0000-1000-8000-00807f9b34fb"),
                UUID.fromString("16186f01-0000-1000-8000-00807f9b34fb"),
                UUID.fromString("16186f02-0000-1000-8000-00807f9b34fb"),
                UUID.fromString("16186f03-0000-1000-8000-00807f9b34fb"),
                encrypted = false,
            ),
            UuidSet(
                UUID.fromString("16187f00-0000-1000-8000-00807f9b34fb"),
                UUID.fromString("16187f02-0000-1000-8000-00807f9b34fb"),
                UUID.fromString("16187f01-0000-1000-8000-00807f9b34fb"),
                UUID.fromString("16187f03-0000-1000-8000-00807f9b34fb"),
                encrypted = false,
            ),
            UuidSet(
                UUID.fromString("1314f000-1000-9000-7000-301291e21220"),
                UUID.fromString("1314f005-1000-9000-7000-301291e21220"),
                UUID.fromString("1314f001-1000-9000-7000-301291e21220"),
                UUID.fromString("1314f002-1000-9000-7000-301291e21220"),
                encrypted = false,
            ),
            UuidSet(
                UUID.fromString("7495fe00-a7f3-424b-92dd-4a006a3aef56"),
                UUID.fromString("74950002-a7f3-424b-92dd-4a006a3aef56"),
                UUID.fromString("74950001-a7f3-424b-92dd-4a006a3aef56"),
                UUID.fromString("74950003-a7f3-424b-92dd-4a006a3aef56"),
                encrypted = false,
            ),
        )
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TOTAL_TIMEOUT_MS = 120_000L
        const val STEP_TIMEOUT_MS = 15_000L
        private const val MTU_TIMEOUT_MS = 5_000L
        private const val FLUSH_TIMEOUT_MS = 3_000L
        private const val SETTLE_MS = 600L
        private const val DISCOVER_TIMEOUT_MS = 6_000L
        private const val DISCOVER_TRIES = 3
    }
}
