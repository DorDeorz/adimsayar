package com.dordeorz.adimsayar.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.dordeorz.adimsayar.band.WatchfaceFile
import com.dordeorz.adimsayar.band.XiaomiBand
import com.dordeorz.adimsayar.band.XiaomiWatchface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A free face in Xiaomi's watch face store. The links are signed and expire, so they are fetched again each time. */
class StoreFace(
    val id: String,
    val name: String,
    val label: String,
    val intro: String,
    val icon: String,
    val file: String,
    val hash: String,
    val size: Long,
)

/**
 * Xiaomi's watch face store, as Mi Fitness shows it. Only face ids ship with the app; names,
 * pictures and files come from Xiaomi when the store is opened. Nothing is sent but the band
 * model and the face id.
 */
object FaceStore {

    private const val DETAIL_URL = "https://watch-appstore.iot.mi.com/api/watchface/prize/detail"
    private const val TIMEOUT_MS = 20_000
    private const val PAUSE_MS = 250L
    private const val MAX_FILE = 8 * 1024 * 1024
    private const val MAX_FAILURES = 3

    /** Faces known to exist for the Xiaomi Smart Band 8; each lookup also brings four more. */
    private val SEED = listOf(
        "266210115", "266230010", "266210050", "266210111", "266210052", "266230002", "266210029", "266210066",
        "266250006", "266230011", "266210061", "266210046", "266210099", "266210101", "266210062", "266210028",
        "266250037", "266230001", "266210112", "266110010", "266210089", "266250024", "266210123", "266210091",
        "266210060", "266210069", "266150013", "266250027", "266250056", "266250047", "266250001", "266210078",
        "266210033", "266210049", "266210106", "266210011", "266210095", "266210013", "266250016", "266250010",
    )

    /** The store's model id for the saved band, or null when the store has nothing for it. */
    fun model(context: Context): String? = BandDirect.model(context)?.storeModel

    /**
     * Loads the store's faces, calling [onFaces] with the list so far after each lookup. Faces
     * found among the recommendations are looked up too, up to [limit] lookups.
     */
    suspend fun load(model: String, limit: Int = SEED.size + 20, onFaces: (List<StoreFace>) -> Unit) = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, StoreFace>()
        val queue = ArrayDeque(SEED)
        val asked = HashSet<String>()
        var failures = 0
        while (queue.isNotEmpty() && asked.size < limit && failures < MAX_FAILURES) {
            val id = queue.removeFirst()
            if (!asked.add(id)) continue
            val faces = detail(model, id)
            if (faces == null) {
                failures++
                continue
            }
            failures = 0
            var added = false
            for (face in faces) {
                if (found.putIfAbsent(face.id, face) == null) added = true
                if (face.id !in asked && face.id !in queue) queue.addLast(face.id)
            }
            if (added) {
                val list = found.values.toList()
                withContext(Dispatchers.Main) { onFaces(list) }
            }
            delay(PAUSE_MS)
        }
        val list = found.values.toList()
        withContext(Dispatchers.Main) { onFaces(list) }
    }

    /** The face behind [id] and the store's recommendations next to it; null when the store cannot be reached. */
    private fun detail(model: String, id: String): List<StoreFace>? {
        val body = get("$DETAIL_URL?model=$model&id=$id") ?: return null
        return runCatching { parse(JSONObject(body)) }.getOrNull()
    }

    private fun parse(json: JSONObject): List<StoreFace> {
        if (json.optInt("code") != 200) return emptyList()
        val data = json.optJSONObject("data") ?: return emptyList()
        val out = ArrayList<StoreFace>()
        face(data)?.let(out::add)
        val more = data.optJSONArray("recommend_list") ?: JSONArray()
        for (i in 0 until more.length()) more.optJSONObject(i)?.let(::face)?.let(out::add)
        return out
    }

    private fun face(o: JSONObject): StoreFace? {
        val id = o.optString("id")
        val file = o.optString("config_file")
        if (id.isEmpty() || !id.all(Char::isDigit) || file.isEmpty()) return null
        val label = o.optJSONArray("label_list")?.optJSONObject(0)?.optString("label_name").orEmpty()
        return StoreFace(
            id = id,
            name = o.optString("display_name").ifEmpty { id },
            label = label,
            intro = o.optString("introduction"),
            icon = o.optString("icon"),
            file = file,
            hash = o.optString("file_hash").lowercase(),
            size = o.optLong("file_size"),
        )
    }

    /** The face's picture, kept on the phone after the first download. */
    suspend fun icon(context: Context, face: StoreFace): Bitmap? = withContext(Dispatchers.IO) {
        val cached = File(File(context.cacheDir, "facestore"), "${face.id}.img")
        if (!cached.exists() && face.icon.isNotEmpty()) {
            val bytes = download(face.icon, MAX_FILE) ?: return@withContext null
            runCatching {
                cached.parentFile?.mkdirs()
                cached.writeBytes(bytes)
            }
        }
        cached.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
    }

    /** Downloads the face file and checks it against the store's hash; null when that fails. */
    suspend fun file(face: StoreFace): WatchfaceFile? = withContext(Dispatchers.IO) {
        val bytes = download(face.file, MAX_FILE) ?: return@withContext null
        if (face.hash.isNotEmpty() && md5(bytes) != face.hash) {
            Log.w(XiaomiBand.TAG, "face store: hash mismatch for ${face.id}")
            return@withContext null
        }
        XiaomiWatchface.parse(bytes)?.let { WatchfaceFile(it.id, face.name, it.bytes) }
    }

    private fun md5(bytes: ByteArray): String =
        MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun get(url: String): String? = download(url, MAX_FILE)?.toString(Charsets.UTF_8)

    private fun download(url: String, max: Int): ByteArray? {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                connection.inputStream.use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        if (out.size() > max) return null
                    }
                    return out.toByteArray()
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.w(XiaomiBand.TAG, "face store: $e")
            return null
        }
    }
}
