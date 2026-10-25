package com.dordeorz.adimsayar.band

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/** Classic Bluetooth (RFCOMM) link used by newer Xiaomi bands and watches such as Band 9 and Redmi Watch 4. */
@SuppressLint("MissingPermission")
class XiaomiSpp(private val device: BluetoothDevice, private val session: XiaomiSession) {

    suspend fun sync(store: suspend (BandFile) -> Boolean): BandResult = withContext(Dispatchers.IO) {
        val socket = try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: SecurityException) {
            return@withContext BandResult.NoPermission
        } catch (e: IOException) {
            return@withContext BandResult.ConnectFailed
        }
        try {
            withTimeoutOrNull(TOTAL_TIMEOUT_MS) { run(socket, store) } ?: BandResult.Timeout
        } catch (e: SecurityException) {
            BandResult.NoPermission
        } catch (e: IOException) {
            if (session.authenticated) BandResult.Timeout else BandResult.ConnectFailed
        } finally {
            runCatching { socket.close() }
        }
    }

    private suspend fun run(socket: BluetoothSocket, store: suspend (BandFile) -> Boolean): BandResult {
        runInterruptible { socket.connect() }
        val input = socket.inputStream
        val output = socket.outputStream
        val incoming = Channel<ByteArray>(Channel.UNLIMITED)
        thread(isDaemon = true, name = "FitWrenSpp") {
            val buf = ByteArray(READ_SIZE)
            try {
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) incoming.trySend(buf.copyOf(n))
                }
            } catch (e: IOException) {
                // socket closed
            }
            incoming.close()
        }
        val protocol = XiaomiSppProtocol(
            cipher = session.cipher,
            write = {
                output.write(it)
                output.flush()
            },
            onCommand = session::onCommand,
            onActivity = session::onActivity,
            onReady = session::startEncrypted,
        )
        session.send = protocol::send

        suspend fun pump(timeoutMs: Long): Boolean? = try {
            val data = withTimeoutOrNull(timeoutMs) { incoming.receive() }
            if (data == null) {
                false
            } else {
                protocol.onBytes(data)
                true
            }
        } catch (e: ClosedReceiveChannelException) {
            null
        }

        protocol.requestVersion()
        while (!protocol.ready) {
            when (pump(VERSION_TIMEOUT_MS)) {
                null -> return BandResult.ConnectFailed
                false -> if (protocol.version == 0) protocol.useV1() else return BandResult.Timeout
                true -> Unit
            }
        }
        return session.loop(pump = { pump(XiaomiBle.STEP_TIMEOUT_MS) }, store = store)
    }

    companion object {
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val TOTAL_TIMEOUT_MS = 120_000L
        private const val VERSION_TIMEOUT_MS = 5_000L
        private const val READ_SIZE = 4096
    }
}
