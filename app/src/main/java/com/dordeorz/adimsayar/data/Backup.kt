package com.dordeorz.adimsayar.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * The full backup: every table (steps, band data, weight, water, medals, notes, walks) and the
 * app's own settings in one JSON file. Band pairing keys are left out; the band is set up again.
 */
object Backup {

    private const val FORMAT = "fitwren-backup"
    private const val VERSION = 1
    private val PREFS = listOf(SettingsStore.PREFS, ExtrasStore.PREFS)

    // Device pairing, sources and sync state stay with this phone.
    private val DEVICE_KEYS = listOf("band_", "wearable_", "xiaomi_", "background_service", "face_design", "battery_card")

    /** Writes the backup; returns the number of rows written. */
    fun write(context: Context, output: OutputStream): Int {
        val database = StepDatabase.get(context)
        val db = database.openHelper.readableDatabase
        val tables = JSONObject()
        var rows = 0
        for (table in StepDatabase.TABLES) {
            val array = JSONArray()
            db.query("SELECT * FROM `$table`").use { cursor ->
                while (cursor.moveToNext()) {
                    array.put(row(cursor))
                    rows++
                }
            }
            tables.put(table, array)
        }
        val prefs = JSONObject()
        for (name in PREFS) prefs.put(name, prefsJson(context, name))
        val root = JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("created", System.currentTimeMillis())
            .put("tables", tables)
            .put("prefs", prefs)
        output.bufferedWriter().use { it.write(root.toString()) }
        return rows
    }

    /** Replaces all data with the backup; returns the number of rows, or null when the file is not a backup. */
    fun restore(context: Context, input: InputStream): Int? {
        val root = try {
            JSONObject(input.bufferedReader().use { it.readText() })
        } catch (e: org.json.JSONException) {
            return null
        }
        if (root.optString("format") != FORMAT) return null
        val tables = root.optJSONObject("tables") ?: return null
        val database = StepDatabase.get(context)
        var rows = 0
        database.runInTransaction {
            val db = database.openHelper.writableDatabase
            for (table in StepDatabase.TABLES) {
                val array = tables.optJSONArray(table) ?: continue
                db.execSQL("DELETE FROM `$table`")
                for (i in 0 until array.length()) {
                    val values = ContentValues()
                    val row = array.getJSONObject(i)
                    for (key in row.keys()) {
                        when (val value = row.get(key)) {
                            JSONObject.NULL -> values.putNull(key)
                            is Int -> values.put(key, value.toLong())
                            is Long -> values.put(key, value)
                            is Double -> values.put(key, value)
                            is Number -> values.put(key, value.toDouble())
                            else -> values.put(key, value.toString())
                        }
                    }
                    db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
                    rows++
                }
            }
        }
        root.optJSONObject("prefs")?.let { prefs ->
            for (name in PREFS) prefs.optJSONObject(name)?.let { restorePrefs(context, name, it) }
        }
        SettingsStore.get(context).reload()
        ExtrasStore.get(context).reload()
        return rows
    }

    private fun row(cursor: Cursor): JSONObject {
        val row = JSONObject()
        for (i in 0 until cursor.columnCount) {
            val name = cursor.getColumnName(i)
            when (cursor.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> row.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> row.put(name, cursor.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> row.put(name, cursor.getDouble(i))
                else -> row.put(name, cursor.getString(i))
            }
        }
        return row
    }

    private fun prefsJson(context: Context, name: String): JSONObject {
        val result = JSONObject()
        for ((key, value) in context.getSharedPreferences(name, Context.MODE_PRIVATE).all) {
            if (DEVICE_KEYS.any { key.startsWith(it) }) continue
            val typed = when (value) {
                is Boolean -> JSONObject().put("b", value)
                is Int -> JSONObject().put("i", value)
                is Long -> JSONObject().put("l", value)
                is Float -> JSONObject().put("f", value.toDouble())
                is String -> JSONObject().put("s", value)
                is Set<*> -> JSONObject().put("set", JSONArray(value.map { it.toString() }))
                else -> continue
            }
            result.put(key, typed)
        }
        return result
    }

    private fun restorePrefs(context: Context, name: String, json: JSONObject) {
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
        if (name == ExtrasStore.PREFS) editor.clear()
        for (key in json.keys()) {
            if (DEVICE_KEYS.any { key.startsWith(it) }) continue
            val typed = json.optJSONObject(key) ?: continue
            when {
                typed.has("b") -> editor.putBoolean(key, typed.getBoolean("b"))
                typed.has("i") -> editor.putInt(key, typed.getInt("i"))
                typed.has("l") -> editor.putLong(key, typed.getLong("l"))
                typed.has("f") -> editor.putFloat(key, typed.getDouble("f").toFloat())
                typed.has("s") -> editor.putString(key, typed.getString("s"))
                typed.has("set") -> typed.getJSONArray("set").let { array ->
                    editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                }
            }
        }
        editor.commit()
    }
}
