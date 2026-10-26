package com.dordeorz.adimsayar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import java.io.File

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

    /** The file's preview picture, or null when it has none. */
    suspend fun preview(file: WatchfaceFile): Bitmap? = withContext(Dispatchers.Default) {
        XiaomiWatchface.preview(file.bytes)?.let { Bitmap.createBitmap(it.pixels, it.width, it.height, Bitmap.Config.ARGB_8888) }
    }

    /** The saved preview of a face uploaded from FitWren; the band itself sends no pictures. */
    suspend fun savedPreview(context: Context, id: String): Bitmap? = withContext(Dispatchers.IO) {
        previewFile(context, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    /** The name a face had in the store; the band itself often reports an internal Chinese one. */
    suspend fun savedName(context: Context, id: String): String? = withContext(Dispatchers.IO) {
        runCatching { nameFile(context, id).takeIf { it.exists() }?.readText()?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /** False when the band is not connected or another upload runs. [name] is kept when it came from the store. */
    suspend fun install(context: Context, file: WatchfaceFile, preview: Bitmap?, name: String? = null): Boolean {
        if (upload.value is WatchfaceUpload.Sending) return false
        withContext(Dispatchers.IO) {
            runCatching {
                val out = previewFile(context, file.id)
                out.parentFile?.mkdirs()
                if (preview != null) out.outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
                if (name != null) nameFile(context, file.id).writeText(name)
            }
        }
        return BandLive.installWatchface(file)
    }

    private fun faceDir(context: Context) = File(context.filesDir, "faces")

    private fun previewFile(context: Context, id: String) = File(faceDir(context), "${id.filter(Char::isDigit)}.png")

    private fun nameFile(context: Context, id: String) = File(faceDir(context), "${id.filter(Char::isDigit)}.name")

    fun onDisconnected() {
        faces.value = null
        if (upload.value is WatchfaceUpload.Sending) upload.value = WatchfaceUpload.Done(false)
    }
}
