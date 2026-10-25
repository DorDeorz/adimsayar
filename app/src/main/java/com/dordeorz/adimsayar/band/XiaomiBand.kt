package com.dordeorz.adimsayar.band

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import com.dordeorz.adimsayar.data.BandBattery
import java.util.Locale

/**
 * Syncs one Xiaomi band or watch. The model (from its Bluetooth name) decides whether to talk BLE,
 * classic Bluetooth, or BLE first and classic as a fallback.
 */
class XiaomiBand(private val context: Context, private val address: String, private val name: String?, private val secret: ByteArray?) {

    var fileSummary: String = ""
        private set

    suspend fun sync(store: suspend (BandFile) -> Boolean): BandResult {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return BandResult.BluetoothOff
        val adapter = manager.adapter ?: return BandResult.BluetoothOff
        if (!adapter.isEnabled) return BandResult.BluetoothOff
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return BandResult.ConnectFailed
        }
        val links = when (BandModels.find(name)?.link) {
            BandLink.Ble -> listOf(BandLink.Ble)
            BandLink.Classic -> listOf(BandLink.Classic)
            else -> listOf(BandLink.Ble, BandLink.Classic)
        }
        var result = BandResult.Unsupported
        for (link in links) {
            val session = newSession()
            result = when (link) {
                BandLink.Classic -> XiaomiSpp(device, session).sync(store)
                else -> XiaomiBle(context, device, session).sync(store)
            }
            fileSummary = session.fileSummary
            Log.i(TAG, "$link: $result")
            if (result != BandResult.Unsupported && !(link == BandLink.Ble && links.size > 1 && result == BandResult.ConnectFailed)) break
        }
        return result
    }

    /**
     * Connects and stays connected after the first sync until the band goes away. [attach] gets the
     * session and a way to run code on the connection, for notifications and music.
     */
    suspend fun live(
        store: suspend (BandFile) -> Boolean,
        hooks: XiaomiLive,
        autoConnect: Boolean,
        attach: (XiaomiSession, post: (() -> Unit) -> Unit) -> Unit,
    ): BandResult {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return BandResult.BluetoothOff
        val adapter = manager.adapter ?: return BandResult.BluetoothOff
        if (!adapter.isEnabled) return BandResult.BluetoothOff
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return BandResult.ConnectFailed
        }
        val session = newSession()
        val result = if (BandModels.find(name)?.link == BandLink.Classic) {
            val link = XiaomiSpp(device, session)
            attach(session, link::post)
            link.live(store, hooks)
        } else {
            val link = XiaomiBle(context, device, session)
            attach(session, link::post)
            link.live(store, hooks, autoConnect)
        }
        fileSummary = session.fileSummary
        Log.i(TAG, "live: $result")
        return result
    }

    private fun newSession() = XiaomiSession(
        secret = secret,
        userId = secret?.joinToString("") { "%02x".format(it) },
        phone = XiaomiPhoneInfo(
            apiLevel = Build.VERSION.SDK_INT,
            model = Build.MODEL,
            region = Locale.getDefault().language.take(2).uppercase(Locale.ROOT),
            use12Hour = !DateFormat.is24HourFormat(context),
        ),
        log = { Log.i(TAG, it) },
    ).also { session ->
        session.onBattery = { level, charging ->
            Log.i(TAG, "battery $level% charging=$charging")
            BandBattery.save(context, level, charging)
        }
    }

    companion object {
        const val TAG = "FitWrenBand"
    }
}
