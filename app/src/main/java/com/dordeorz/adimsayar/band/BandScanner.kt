package com.dordeorz.adimsayar.band

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context

class FoundBand(val address: String, val name: String)

@SuppressLint("MissingPermission")
class BandScanner(private val context: Context) {

    private var callback: ScanCallback? = null

    fun start(onFound: (FoundBand) -> Unit): Boolean {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        if (!adapter.isEnabled) return false
        val scanner = adapter.bluetoothLeScanner ?: return false
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: return
                if (NAMES.any { name.startsWith(it) }) onFound(FoundBand(result.device.address, name))
            }
        }
        return try {
            scanner.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), cb)
            callback = cb
            true
        } catch (e: SecurityException) {
            false
        }
    }

    fun stop() {
        val cb = callback ?: return
        callback = null
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (e: SecurityException) {
            return
        } catch (e: IllegalStateException) {
            return
        }
    }

    companion object {
        private val NAMES = listOf("Xiaomi", "Redmi", "Mi ")
    }
}
