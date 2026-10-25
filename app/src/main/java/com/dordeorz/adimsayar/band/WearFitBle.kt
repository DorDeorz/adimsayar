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
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Syncs a WearFit Pro watch (HK series) over its Nordic UART style service. No key and no pairing:
 * it subscribes to reports, asks once for the fitness history since [since] and listens until the
 * watch goes quiet.
 */
@SuppressLint("MissingPermission")
class WearFitBand(
    private val context: Context,
    private val address: String,
    private val since: LocalDateTime,
) {

    private sealed interface Event {
        data class Connection(val connected: Boolean) : Event
        data class Services(val ok: Boolean) : Event
        data object Written : Event
        data class Changed(val uuid: UUID, val value: ByteArray) : Event
    }

    var summary: String = ""
        private set

    private val events = Channel<Event>(Channel.UNLIMITED)
    private lateinit var gatt: BluetoothGatt

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            events.trySend(Event.Connection(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED))
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
        val result = try {
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
        Log.i(XiaomiBand.TAG, "wearfit: $result $summary")
        return result
    }

    private suspend fun run(store: suspend (BandFile) -> Boolean): BandResult {
        if ((withTimeoutOrNull(STEP_TIMEOUT_MS) { next<Event.Connection>() })?.connected != true) return BandResult.ConnectFailed
        delay(SETTLE_MS)
        if (!discover()) return BandResult.ConnectFailed
        Log.i(XiaomiBand.TAG, "wearfit services: " + gatt.services.orEmpty().joinToString { it.uuid.toString() })
        val control = find(CHAR_CONTROL) ?: return BandResult.Unsupported
        val report = find(CHAR_REPORT) ?: return BandResult.Unsupported

        gatt.setCharacteristicNotification(report, true)
        report.getDescriptor(CCCD)?.let { descriptor ->
            if (!writeDescriptor(descriptor)) return BandResult.ConnectFailed
            withTimeoutOrNull(STEP_TIMEOUT_MS) { next<Event.Written>() } ?: return BandResult.Timeout
        }

        val session = WearFitSession()
        if (!write(control, session.request(since))) return BandResult.ConnectFailed

        var heard = false
        while (true) {
            val event = withTimeoutOrNull(if (heard) IDLE_MS else STEP_TIMEOUT_MS) { events.receive() } ?: break
            when (event) {
                is Event.Changed -> if (event.uuid == CHAR_REPORT) {
                    heard = true
                    if (session.reports < LOG_REPORTS) Log.i(XiaomiBand.TAG, "wearfit report " + event.value.joinToString("") { "%02x".format(it) })
                    session.onReport(event.value)
                }
                is Event.Connection -> if (!event.connected) {
                    if (!heard) return BandResult.ConnectFailed
                    break
                }
                else -> Unit
            }
        }
        summary = session.summary
        if (!heard) return BandResult.Timeout
        val minutes = session.minutes()
        if (minutes.isNotEmpty()) store(BandFile(XiaomiFileId(since.atZone(ZoneId.systemDefault()).toEpochSecond(), 0, 0, 0), minutes))
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
            if (find(CHAR_CONTROL) != null) return true
            delay(SETTLE_MS)
        }
        return false
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
        private val CHAR_CONTROL = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        private val CHAR_REPORT = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private const val TOTAL_TIMEOUT_MS = 90_000L
        private const val STEP_TIMEOUT_MS = 15_000L
        private const val IDLE_MS = 3_000L
        private const val SETTLE_MS = 600L
        private const val DISCOVER_TIMEOUT_MS = 6_000L
        private const val DISCOVER_TRIES = 3
        private const val LOG_REPORTS = 40
    }
}
