package com.dordeorz.adimsayar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.Log
import com.dordeorz.adimsayar.band.FacePicture
import com.dordeorz.adimsayar.band.WatchfaceFile
import com.dordeorz.adimsayar.band.WatchfaceUpload
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.band.XiaomiFaceFile
import com.dordeorz.adimsayar.band.XiaomiWatchface
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class DesignBird { Happy, Sleepy, None }

/** What the user picked in the face designer. Colours are ARGB. */
data class FaceDesign(
    val top: Int,
    val bottom: Int,
    val hour: Int,
    val minute: Int,
    val text: Int,
    val accent: Int,
    val bird: DesignBird,
)

/**
 * The face designer: FitWren's white-on-black template face (watchface/make_faces.py, designer())
 * gets the user's background and colours on the phone, and the file is written again with
 * [XiaomiFaceFile]. Smart Band 8 only, like the template.
 */
object FaceDesigner {

    private const val DIR = "designer"
    private const val TEMPLATE = "$DIR/template.bin"
    private const val W = 192
    private const val H = 490
    private const val PREVIEW_W = 122
    private const val PREVIEW_H = 310
    private const val BAR_STEPS = 11
    private const val ID_BASE = 266291000
    private const val ID_COUNT = 100
    private const val PHOTO_MAX = 1024

    private const val SETTINGS_PREFS = "settings"
    private const val KEY_DESIGN = "face_design"
    private const val KEY_NEXT = "face_design_next"
    private const val KEY_LAST = "face_design_last"

    val DEFAULT = FaceDesign(
        top = 0xFF1E172E.toInt(),
        bottom = 0xFF07050D.toInt(),
        hour = 0xFFF2E6D8.toInt(),
        minute = 0xFFFFB06A.toInt(),
        text = 0xFFF2E6D8.toInt(),
        accent = 0xFF81C784.toInt(),
        bird = DesignBird.Happy,
    )

    private class Layer(val file: String, val x: Int, val y: Int)

    private class Layout(val layers: List<Layer>, val lists: List<String>, val bar: Rect, val barSample: Int)

    /** True when the saved band can take the designer's face. */
    fun available(context: Context): Boolean =
        BandDirect.model(context)?.storeModel != null &&
            runCatching { context.assets.list(DIR)?.contains("template.bin") == true }.getOrDefault(false)

    fun saved(context: Context): FaceDesign {
        val text = prefs(context).getString(KEY_DESIGN, null) ?: return DEFAULT
        return runCatching {
            val o = JSONObject(text)
            FaceDesign(
                o.getInt("top"), o.getInt("bottom"), o.getInt("hour"), o.getInt("minute"), o.getInt("text"), o.getInt("accent"),
                DesignBird.valueOf(o.getString("bird")),
            )
        }.getOrDefault(DEFAULT)
    }

    fun save(context: Context, design: FaceDesign) {
        val o = JSONObject()
            .put("top", design.top).put("bottom", design.bottom)
            .put("hour", design.hour).put("minute", design.minute).put("text", design.text).put("accent", design.accent)
            .put("bird", design.bird.name)
        prefs(context).edit().putString(KEY_DESIGN, o.toString()).apply()
    }

    /** A photo from the phone, made small enough to crop from; null when it cannot be read. */
    suspend fun photo(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= PHOTO_MAX && bounds.outHeight / (sample * 2) >= PHOTO_MAX) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

    /** The face as it will look, with sample values; [photo] replaces the gradient. */
    suspend fun picture(context: Context, design: FaceDesign, photo: Bitmap?): Bitmap = withContext(Dispatchers.Default) {
        val layout = layout(context)
        val out = background(context, layout, design, photo)
        val canvas = Canvas(out)
        for ((role, color) in listOf("hour" to design.hour, "minute" to design.minute, "text" to design.text)) {
            asset(context, "sample_$role.png")?.let { canvas.drawBitmap(it, 0f, 0f, tint(color)) }
        }
        val bar = bar(layout, design.accent, layout.barSample)
        canvas.drawBitmap(bar, layout.bar.left.toFloat(), layout.bar.top.toFloat(), null)
        out
    }

    // The upload outlives the designer and the faces list, so it runs here.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Starts [install] in the background; [failed] is shown when it does not work out. */
    fun start(context: Context, design: FaceDesign, photo: Bitmap?, name: String, failed: String) {
        val app = context.applicationContext
        save(app, design)
        scope.launch {
            if (!install(app, design, photo, name)) Toast.makeText(app, failed, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Builds the face and starts sending it. Each design gets a new id, because the band keeps a
     * face it already has; the previous design is deleted once the new one is on the band.
     */
    suspend fun install(context: Context, design: FaceDesign, photo: Bitmap?, name: String): Boolean {
        val prefs = prefs(context)
        val next = prefs.getInt(KEY_NEXT, 0)
        val id = (ID_BASE + 1 + next % (ID_COUNT - 1)).toString()
        val picture = picture(context, design, photo)
        val file = build(context, design, photo, picture, id, name) ?: return false
        prefs.edit().putInt(KEY_NEXT, next + 1).apply()
        BandFaces.clearUpload()
        if (!BandFaces.install(context, file, picture, name)) return false
        val done = BandFaces.upload().first { it is WatchfaceUpload.Done } as WatchfaceUpload.Done
        if (done.success) {
            val last = prefs.getString(KEY_LAST, null)
            if (last != null && last != id) BandLive.deleteWatchface(last)
            prefs.edit().putString(KEY_LAST, id).apply()
            save(context, design)
        }
        return done.success
    }

    private suspend fun build(context: Context, design: FaceDesign, photo: Bitmap?, picture: Bitmap, id: String, name: String): WatchfaceFile? =
        withContext(Dispatchers.Default) {
            runCatching {
                val layout = layout(context)
                val template = context.assets.open(TEMPLATE).use { it.readBytes() }
                val face = XiaomiFaceFile.parse(template) ?: error("template not readable")
                check(face.images == 1 && face.lists == layout.lists.size) { "template does not match layout" }
                face.setImage(0, background(context, layout, design, photo).picture())
                layout.lists.forEachIndexed { index, role ->
                    when (role) {
                        "bar" -> face.setList(index, (0 until BAR_STEPS).map { bar(layout, design.accent, it * 10).picture() })
                        else -> {
                            val color = when (role) {
                                "hour" -> design.hour
                                "minute" -> design.minute
                                else -> design.text
                            }
                            face.setList(index, face.list(index).map { it.tinted(color) })
                        }
                    }
                }
                face.setPreview(Bitmap.createScaledBitmap(picture, PREVIEW_W, PREVIEW_H, true).picture())
                val bytes = OwnFaces.withId(face.bytes(), id) ?: error("id not written")
                XiaomiWatchface.parse(bytes)?.let { WatchfaceFile(it.id, name, it.bytes) }
            }.onFailure { Log.w(XiaomiBand.TAG, "face designer: $it") }.getOrNull()
        }

    /** The background with the bird, the date dot and the icons drawn on it; plain images keep no transparency. */
    private fun background(context: Context, layout: Layout, design: FaceDesign, photo: Bitmap?): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (photo != null) {
            val scale = maxOf(W.toFloat() / photo.width, H.toFloat() / photo.height)
            val w = W / scale
            val h = H / scale
            val left = ((photo.width - w) / 2).toInt()
            val top = ((photo.height - h) / 2).toInt()
            canvas.drawBitmap(photo, Rect(left, top, left + w.toInt(), top + h.toInt()), Rect(0, 0, W, H), Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), design.top, design.bottom, Shader.TileMode.CLAMP) }
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        }
        for (layer in layout.layers) {
            val paint = when (layer.file) {
                "bird.png" -> if (design.bird == DesignBird.Happy) null else continue
                "bird_sleepy.png" -> if (design.bird == DesignBird.Sleepy) null else continue
                "dot.png" -> tint(design.text)
                else -> null
            }
            asset(context, layer.file)?.let { canvas.drawBitmap(it, layer.x.toFloat(), layer.y.toFloat(), paint) }
        }
        return out
    }

    /** The goal bar at [percent]: a faint track and the filled part, both in [accent]. */
    private fun bar(layout: Layout, accent: Int, percent: Int): Bitmap {
        val w = layout.bar.width()
        val h = layout.bar.height()
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = h / 2f
        paint.color = (accent and 0x00FFFFFF) or (0x50 shl 24)
        canvas.drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), r, r, paint)
        if (percent > 0) {
            paint.color = accent
            val fill = maxOf(h.toFloat(), w * minOf(percent, 100) / 100f)
            canvas.drawRoundRect(RectF(0f, 0f, fill, h.toFloat()), r, r, paint)
        }
        return out
    }

    private fun tint(color: Int) = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN) }

    private fun FacePicture.tinted(color: Int): FacePicture =
        FacePicture(width, height, IntArray(pixels.size) { i ->
            val alpha = (pixels[i] ushr 24) * (color ushr 24) / 255
            (alpha shl 24) or (color and 0x00FFFFFF)
        })

    private fun Bitmap.picture(): FacePicture {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        return FacePicture(width, height, pixels)
    }

    private fun asset(context: Context, file: String): Bitmap? =
        runCatching { context.assets.open("$DIR/$file").use(BitmapFactory::decodeStream) }.getOrNull()

    private fun layout(context: Context): Layout {
        val o = JSONObject(context.assets.open("$DIR/layout.json").use { it.readBytes().toString(Charsets.UTF_8) })
        val layers = o.getJSONArray("layers")
        val lists = o.getJSONArray("lists")
        val bar = o.getJSONObject("bar")
        val x = bar.getInt("x")
        val y = bar.getInt("y")
        return Layout(
            (0 until layers.length()).map { layers.getJSONObject(it).let { l -> Layer(l.getString("file"), l.getInt("x"), l.getInt("y")) } },
            (0 until lists.length()).map { lists.getString(it) },
            Rect(x, y, x + bar.getInt("width"), y + bar.getInt("height")),
            bar.getInt("sample"),
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
