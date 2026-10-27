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
import android.widget.Toast
import com.dordeorz.adimsayar.band.FacePicture
import com.dordeorz.adimsayar.band.WatchfaceFile
import com.dordeorz.adimsayar.band.WatchfaceUpload
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.band.XiaomiFaceFile
import com.dordeorz.adimsayar.band.XiaomiWatchface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

enum class DesignBird { Happy, Sleepy, None }

/** Where the time sits; the bird and the stats move around it. */
enum class DesignPlace { Top, Middle, Bottom }

/**
 * What the user picked in the face designer. Colours are ARGB. With [photo] the saved photo is
 * the background, scaled by [zoom] (1 = just covers the face) and moved by [panX], [panY] face pixels.
 */
data class FaceDesign(
    val top: Int,
    val bottom: Int,
    val hour: Int,
    val minute: Int,
    val text: Int,
    val accent: Int,
    val bird: DesignBird,
    val style: String = "bold",
    val place: DesignPlace = DesignPlace.Top,
    val showDate: Boolean = true,
    val showSteps: Boolean = true,
    val showBar: Boolean = true,
    val showHealth: Boolean = true,
    val dim: Int = 0,
    val photo: Boolean = false,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
)

/**
 * The face designer: FitWren's white-on-black template face (watchface/make_faces.py, designer())
 * gets the user's background, digits and colours on the phone, its parts are moved or hidden, and
 * the file is written again with [XiaomiFaceFile]. Smart Band 8 only, like the template.
 */
object FaceDesigner {

    const val W = 192
    const val H = 490
    private const val DIR = "designer"
    private const val TEMPLATE = "$DIR/template.bin"
    private const val PREVIEW_W = 122
    private const val PREVIEW_H = 310
    private const val BAR_STEPS = 11
    private const val ID_BASE = 266291000
    private const val ID_COUNT = 100
    private const val PHOTO_MAX = 1024
    const val ZOOM_MAX = 4f

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

    /** Ready colour sets: background top and bottom, hour, minute, text, goal bar. */
    val THEMES: List<FaceDesign> = listOf(
        DEFAULT,
        theme(0xFF000000, 0xFF000000, 0xFFFFFFFF, 0xFFFFC93C, 0xFF9E9E9E, 0xFFFFC93C),
        theme(0xFF0B102E, 0xFF02030A, 0xFFFFFFFF, 0xFF9CB4FF, 0xFF9CB4FF, 0xFFFFD54F),
        theme(0xFF000000, 0xFF120018, 0xFF29E6FF, 0xFFFF4FC3, 0xFFB388FF, 0xFFFF4FC3),
        theme(0xFFFFF1D6, 0xFFFFD3C2, 0xFF3B2A20, 0xFFE07A2F, 0xFF3B2A20, 0xFF43A047),
        theme(0xFF12324A, 0xFF0A1420, 0xFFB3E5FC, 0xFF29E6FF, 0xFFB3E5FC, 0xFF4FC3F7),
        theme(0xFF1B3A2A, 0xFF07130C, 0xFFE8F5E9, 0xFF9CE65A, 0xFFC8E6C9, 0xFF9CE65A),
        theme(0xFF4A1A2C, 0xFF12060B, 0xFFFFE0E6, 0xFFEF5350, 0xFFFFCDD2, 0xFFEF5350),
    )

    private fun theme(top: Long, bottom: Long, hour: Long, minute: Long, text: Long, accent: Long) =
        DEFAULT.copy(top = top.toInt(), bottom = bottom.toInt(), hour = hour.toInt(), minute = minute.toInt(), text = text.toInt(), accent = accent.toInt())

    private class Layer(val file: String, val x: Int, val y: Int, val part: String, val block: String)

    private class Layout(
        val layers: List<Layer>,
        val lists: List<String>,
        val widgetBlocks: List<String>,
        val timeX: Int,
        val hourY: Int,
        val minuteY: Int,
        val cell: Pair<Int, Int>,
        val bar: Rect,
        val barSample: Int,
        val styles: List<String>,
    )

    /** True when the saved band can take the designer's face. */
    fun available(context: Context): Boolean =
        BandDirect.model(context)?.storeModel != null &&
            runCatching { context.assets.list(DIR)?.contains("template.bin") == true }.getOrDefault(false)

    /** The digit styles in the template, in the order the generator wrote them. */
    fun styles(context: Context): List<String> = runCatching { layout(context).styles }.getOrDefault(listOf("bold"))

    /** How far each block moves for [place]: the time, the bird and the stats. */
    private fun shift(place: DesignPlace, block: String): Int = when (place) {
        DesignPlace.Top -> 0
        DesignPlace.Middle -> when (block) {
            "bird" -> -230
            "time" -> 96
            else -> 0
        }
        DesignPlace.Bottom -> when (block) {
            "bird" -> -226
            "stats" -> -224
            "time" -> 236
            else -> 0
        }
    }

    private fun FaceDesign.shows(part: String): Boolean = when (part) {
        "date" -> showDate
        "steps" -> showSteps
        "bar" -> showBar
        "health" -> showHealth
        "bird" -> bird != DesignBird.None
        else -> true
    }

    fun saved(context: Context): FaceDesign {
        val text = prefs(context).getString(KEY_DESIGN, null) ?: return DEFAULT
        return runCatching {
            val o = JSONObject(text)
            FaceDesign(
                o.getInt("top"), o.getInt("bottom"), o.getInt("hour"), o.getInt("minute"), o.getInt("text"), o.getInt("accent"),
                DesignBird.valueOf(o.getString("bird")),
                style = o.optString("style", "bold"),
                place = runCatching { DesignPlace.valueOf(o.optString("place")) }.getOrDefault(DesignPlace.Top),
                showDate = o.optBoolean("showDate", true),
                showSteps = o.optBoolean("showSteps", true),
                showBar = o.optBoolean("showBar", true),
                showHealth = o.optBoolean("showHealth", true),
                dim = o.optInt("dim", 0),
                photo = o.optBoolean("photo", false) && photoFile(context).exists(),
                zoom = o.optDouble("zoom", 1.0).toFloat(),
                panX = o.optDouble("panX", 0.0).toFloat(),
                panY = o.optDouble("panY", 0.0).toFloat(),
            )
        }.getOrDefault(DEFAULT)
    }

    fun save(context: Context, design: FaceDesign) {
        val o = JSONObject()
            .put("top", design.top).put("bottom", design.bottom)
            .put("hour", design.hour).put("minute", design.minute).put("text", design.text).put("accent", design.accent)
            .put("bird", design.bird.name).put("style", design.style).put("place", design.place.name)
            .put("showDate", design.showDate).put("showSteps", design.showSteps)
            .put("showBar", design.showBar).put("showHealth", design.showHealth)
            .put("dim", design.dim).put("photo", design.photo)
            .put("zoom", design.zoom.toDouble()).put("panX", design.panX.toDouble()).put("panY", design.panY.toDouble())
        prefs(context).edit().putString(KEY_DESIGN, o.toString()).apply()
    }

    /** Reads a photo from the phone, keeps a small copy for later designs and returns it; null when it cannot be read. */
    suspend fun takePhoto(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= PHOTO_MAX && bounds.outHeight / (sample * 2) >= PHOTO_MAX) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val photo = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching null
            val file = photoFile(context)
            file.parentFile?.mkdirs()
            file.outputStream().use { photo.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            photo
        }.getOrNull()
    }

    /** The photo kept by [takePhoto], or null. */
    suspend fun savedPhoto(context: Context): Bitmap? = withContext(Dispatchers.IO) {
        photoFile(context).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    /**
     * Where the photo lies on the face: at [zoom] 1 it just covers it, centred; [panX] and [panY]
     * move it, but never so far that an edge shows. Returns the rectangle and the pan actually used.
     */
    fun photoPlace(photo: Bitmap, zoom: Float, panX: Float, panY: Float): Triple<RectF, Float, Float> {
        val scale = maxOf(W.toFloat() / photo.width, H.toFloat() / photo.height) * zoom.coerceIn(1f, ZOOM_MAX)
        val w = photo.width * scale
        val h = photo.height * scale
        val slackX = (w - W) / 2
        val slackY = (h - H) / 2
        val x = panX.coerceIn(-slackX, slackX)
        val y = panY.coerceIn(-slackY, slackY)
        val left = (W - w) / 2 + x
        val top = (H - h) / 2 + y
        return Triple(RectF(left, top, left + w, top + h), x, y)
    }

    /** The face as it will look, with sample values. */
    suspend fun picture(context: Context, design: FaceDesign, photo: Bitmap?): Bitmap = withContext(Dispatchers.Default) {
        val layout = layout(context)
        val out = background(context, layout, design, photo)
        val canvas = Canvas(out)
        val digits = digits(context, layout, design.style)
        val dy = shift(design.place, "time")
        for ((sample, y, color) in listOf(Triple("09", layout.hourY, design.hour), Triple("41", layout.minuteY, design.minute))) {
            sample.forEachIndexed { i, ch ->
                digits.getOrNull(ch - '0')?.let { canvas.drawBitmap(it, (layout.timeX + i * layout.cell.first).toFloat(), (y + dy).toFloat(), tint(color)) }
            }
        }
        for ((part, block) in listOf("date" to "time", "steps" to "stats", "health" to "stats")) {
            if (!design.shows(part)) continue
            asset(context, "sample_$part.png")?.let { canvas.drawBitmap(it, 0f, shift(design.place, block).toFloat(), tint(design.text)) }
        }
        if (design.showBar) {
            canvas.drawBitmap(bar(layout, design.accent, layout.barSample), layout.bar.left.toFloat(), (layout.bar.top + shift(design.place, "stats")).toFloat(), null)
        }
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
        }
        return done.success
    }

    private suspend fun build(context: Context, design: FaceDesign, photo: Bitmap?, picture: Bitmap, id: String, name: String): WatchfaceFile? =
        withContext(Dispatchers.Default) {
            runCatching {
                val layout = layout(context)
                val template = context.assets.open(TEMPLATE).use { it.readBytes() }
                val face = XiaomiFaceFile.parse(template) ?: error("template not readable")
                check(face.images == 1 && face.lists == layout.lists.size && face.widgets == layout.widgetBlocks.size) {
                    "template does not match layout"
                }
                face.setImage(0, background(context, layout, design, photo).picture())
                val digits = digits(context, layout, design.style).map { it.picture() }
                layout.lists.forEachIndexed { index, part ->
                    val frames = when (part) {
                        "bar" -> (0 until BAR_STEPS).map { bar(layout, design.accent, it * 10).picture() }
                        "hour" -> digits.map { it.tinted(design.hour) }
                        "minute" -> digits.map { it.tinted(design.minute) }
                        else -> face.list(index).map { it.tinted(design.text) }
                    }
                    face.setList(index, if (design.shows(part)) frames else frames.map { it.cleared() })
                }
                layout.widgetBlocks.forEachIndexed { index, block ->
                    val dy = shift(design.place, block)
                    if (dy != 0) face.moveWidget(index, dy)
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
        if (design.photo && photo != null) {
            val (rect, _, _) = photoPlace(photo, design.zoom, design.panX, design.panY)
            canvas.drawBitmap(photo, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val paint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, H.toFloat(), design.top, design.bottom, Shader.TileMode.CLAMP) }
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
        }
        if (design.dim > 0) canvas.drawColor((design.dim.coerceIn(0, 90) * 255 / 100) shl 24)
        for (layer in layout.layers) {
            if (!design.shows(layer.part)) continue
            val paint = when (layer.file) {
                "bird.png" -> if (design.bird == DesignBird.Happy) null else continue
                "bird_sleepy.png" -> if (design.bird == DesignBird.Sleepy) null else continue
                "dot.png" -> tint(design.text)
                else -> null
            }
            asset(context, layer.file)?.let { canvas.drawBitmap(it, layer.x.toFloat(), (layer.y + shift(design.place, layer.block)).toFloat(), paint) }
        }
        return out
    }

    /** The ten digits of [style], white, each the size of the template's hour digits. */
    private fun digits(context: Context, layout: Layout, style: String): List<Bitmap> {
        val strip = asset(context, "digits_$style.png") ?: asset(context, "digits_bold.png") ?: return emptyList()
        val (w, h) = layout.cell
        return (0 until 10).map { Bitmap.createBitmap(strip, it * w, 0, w, h) }
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

    private fun FacePicture.cleared(): FacePicture = FacePicture(width, height, IntArray(pixels.size))

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
        val widgets = o.getJSONArray("widgets")
        val time = o.getJSONObject("time")
        val cell = time.getJSONArray("cell")
        val bar = o.getJSONObject("bar")
        val styles = o.getJSONArray("styles")
        val x = bar.getInt("x")
        val y = bar.getInt("y")
        return Layout(
            (0 until layers.length()).map {
                layers.getJSONObject(it).let { l -> Layer(l.getString("file"), l.getInt("x"), l.getInt("y"), l.getString("part"), l.getString("block")) }
            },
            (0 until lists.length()).map { lists.getString(it) },
            (0 until widgets.length()).map { widgets.getJSONObject(it).getString("block") },
            time.getInt("x"),
            time.getInt("hour"),
            time.getInt("minute"),
            cell.getInt(0) to cell.getInt(1),
            Rect(x, y, x + bar.getInt("width"), y + bar.getInt("height")),
            bar.getInt("sample"),
            (0 until styles.length()).map { styles.getString(it) },
        )
    }

    private fun photoFile(context: Context) = File(File(context.filesDir, DIR), "photo.jpg")

    private fun prefs(context: Context) = context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
}
