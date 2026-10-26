package com.dordeorz.adimsayar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dordeorz.adimsayar.band.WatchfaceFile
import com.dordeorz.adimsayar.band.XiaomiWatchface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A FitWren watch face that ships with the app (built from watchface/make_faces.py). */
class OwnFace(val name: String, val title: String)

/** FitWren's own faces for the Xiaomi Smart Band 8, kept in assets/faces as <name>.bin and <name>.png. */
object OwnFaces {

    private const val DIR = "faces"

    private val ALL = listOf(
        OwnFace("fitwren", "FitWren"),
        OwnFace("fitwren_gece", "FitWren Gece"),
        OwnFace("fitwren_yol", "FitWren Yol"),
        OwnFace("fitwren_analog", "FitWren Analog"),
        OwnFace("fitwren_neon", "FitWren Neon"),
        OwnFace("fitwren_dal", "FitWren Dal"),
        OwnFace("fitwren_dal_gece", "FitWren Dal Gece"),
        OwnFace("fitwren_halka", "FitWren Halka"),
        OwnFace("fitwren_sade", "FitWren Sade"),
    )

    /** The faces whose file is in the app; empty for any band but the Xiaomi Smart Band 8. */
    fun available(context: Context): List<OwnFace> {
        if (BandDirect.model(context)?.storeModel == null) return emptyList()
        val files = runCatching { context.assets.list(DIR)?.toSet() }.getOrNull().orEmpty()
        return ALL.filter { "${it.name}.bin" in files }
    }

    suspend fun preview(context: Context, face: OwnFace): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { context.assets.open("$DIR/${face.name}.png").use(BitmapFactory::decodeStream) }.getOrNull()
    }

    suspend fun file(context: Context, face: OwnFace): WatchfaceFile? = withContext(Dispatchers.IO) {
        runCatching { context.assets.open("$DIR/${face.name}.bin").use { it.readBytes() } }.getOrNull()
            ?.let(XiaomiWatchface::parse)
            ?.let { WatchfaceFile(it.id, face.title, it.bytes) }
    }
}
