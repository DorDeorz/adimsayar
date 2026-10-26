package com.dordeorz.adimsayar.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dordeorz.adimsayar.band.BandWatchface
import com.dordeorz.adimsayar.band.WatchfaceFile
import com.dordeorz.adimsayar.band.WatchfaceUpload
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.band.XiaomiWatchface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** The band's watch faces and the upload of a new one from a .bin file. */
object BandFaces {

    private val faces = MutableStateFlow<List<BandWatchface>?>(null)
    private val upload = MutableStateFlow<WatchfaceUpload?>(null)

    fun faces(): StateFlow<List<BandWatchface>?> = faces.asStateFlow()

    fun upload(): StateFlow<WatchfaceUpload?> = upload.asStateFlow()

    fun onFaces(list: List<BandWatchface>) {
        Log.i(XiaomiBand.TAG, "watchfaces ${list.size}, active ${list.firstOrNull { it.active }?.id}")
        faces.value = list
    }

    fun onUpload(state: WatchfaceUpload) {
        upload.value = state
    }

    fun clearUpload() {
        if (upload.value is WatchfaceUpload.Done) upload.value = null
    }

    /** False when the band is not connected. */
    fun refresh(): Boolean = BandLive.requestWatchfaces()

    fun select(face: BandWatchface): Boolean = BandLive.setWatchface(face.id)

    fun delete(face: BandWatchface): Boolean = BandLive.deleteWatchface(face.id)

    /** Reads a face file; null when it is not a Xiaomi watch face. */
    suspend fun read(context: Context, uri: Uri): WatchfaceFile? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()?.let(XiaomiWatchface::parse)
    }

    /** False when the band is not connected or another upload runs. */
    fun install(file: WatchfaceFile): Boolean {
        if (upload.value is WatchfaceUpload.Sending) return false
        return BandLive.installWatchface(file)
    }

    fun onDisconnected() {
        faces.value = null
        if (upload.value is WatchfaceUpload.Sending) upload.value = WatchfaceUpload.Done(false)
    }
}
