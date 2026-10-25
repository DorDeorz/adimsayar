package com.dordeorz.adimsayar.band

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context

class FoundBand(val address: String, val name: String)

@SuppressLint("MissingPermission")
class BandScanner(private val context: Context) {

    private var callback: ScanCallback? = null

    fun start(onFound: (FoundBand) -> Unit): Boolean {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return false
        val adapter = manager.adapter ?: return false
        if (!adapter.isEnabled) return false
        val scanner = adapter.bluetoothLeScanner ?: return false
        try {
            val known = manager.getConnectedDevices(BluetoothProfile.GATT) + adapter.bondedDevices.orEmpty()
            known.forEach { device -> report(device, null, onFound) }
        } catch (e: SecurityException) {
            return false
        }
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                report(result.device, result.scanRecord?.deviceName, onFound)
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

    private fun report(device: BluetoothDevice, advertised: String?, onFound: (FoundBand) -> Unit) {
        val name = advertised ?: try {
            device.name
        } catch (e: SecurityException) {
            null
        } ?: return
        if (NAMES.any { name.startsWith(it, ignoreCase = true) }) onFound(FoundBand(device.address, name))
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
        private val NAMES = listOf("Xiaomi", "Redmi", "Mi ", "Smart Band")
    }
}
