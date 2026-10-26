package com.dordeorz.adimsayar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.dordeorz.adimsayar.band.WatchfaceFile
import com.dordeorz.adimsayar.band.XiaomiWatchface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A FitWren watch face that ships with the app (built from watchface/make_faces.py). */
class OwnFace(val name: String, val title: String, val id: String)

/** FitWren's own faces for the Xiaomi Smart Band 8, kept in assets/faces as <name>.bin and <name>.png. */
object OwnFaces {

    private const val DIR = "faces"
    private const val ID_OFFSET = 0x28
    private const val ID_LENGTH = 16

    private val ALL = listOf(
        OwnFace("fitwren", "FitWren", "166100001"),
        OwnFace("fitwren_gece", "FitWren Gece", "166100002"),
        OwnFace("fitwren_yol", "FitWren Yol", "166100003"),
        OwnFace("fitwren_analog", "FitWren Analog", "166100004"),
        OwnFace("fitwren_neon", "FitWren Neon", "166100005"),
        OwnFace("fitwren_dal", "FitWren Dal", "166100006"),
        OwnFace("fitwren_dal_gece", "FitWren Dal Gece", "166100007"),
        OwnFace("fitwren_halka", "FitWren Halka", "166100008"),
        OwnFace("fitwren_sade", "FitWren Sade", "166100009"),
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

    /**
     * The face file with its own id. Mi Create's compiler writes the same id into every face, so
     * the band would treat them as one face; the id is a fixed-size digit string at [ID_OFFSET].
     */
    suspend fun file(context: Context, face: OwnFace): WatchfaceFile? = withContext(Dispatchers.IO) {
        runCatching { context.assets.open("$DIR/${face.name}.bin").use { it.readBytes() } }.getOrNull()
            ?.let { withId(it, face.id) }
            ?.let(XiaomiWatchface::parse)
            ?.let { WatchfaceFile(it.id, face.title, it.bytes) }
    }

    internal fun withId(bytes: ByteArray, id: String): ByteArray? {
        val digits = id.toByteArray(Charsets.US_ASCII)
        if (bytes.size < ID_OFFSET + ID_LENGTH || digits.size >= ID_LENGTH) return null
        return bytes.copyOf().also { out ->
            out.fill(0, ID_OFFSET, ID_OFFSET + ID_LENGTH)
            digits.copyInto(out, ID_OFFSET)
        }
    }
}
