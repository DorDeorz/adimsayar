package com.dordeorz.adimsayar.sensor

import android.content.Context
import android.net.Uri
import android.os.Build
import com.dordeorz.adimsayar.R
import com.dordeorz.adimsayar.data.ReadSource
import com.dordeorz.adimsayar.data.ReadingLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object XiaomiSteps {

    private val URI: Uri = Uri.parse("content://com.miui.providers.steps/item")
    private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm")

    fun isXiaomi(): Boolean = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    fun probe(context: Context) {
        if (!isXiaomi()) return
        val log = ReadingLog.get(context)
        val cursor = try {
            context.contentResolver.query(URI, null, null, null, null)
        } catch (e: SecurityException) {
            log.note(ReadSource.Xiaomi, context.getString(R.string.log_xiaomi_denied))
            return
        } catch (e: RuntimeException) {
            log.note(ReadSource.Xiaomi, context.getString(R.string.log_xiaomi_error, e.javaClass.simpleName))
            return
        }
        if (cursor == null) {
            log.note(ReadSource.Xiaomi, context.getString(R.string.log_xiaomi_missing))
            return
        }
        cursor.use {
            val columns = it.columnNames.joinToString(", ")
            val begin = it.getColumnIndex("_begin_time")
            val end = it.getColumnIndex("_end_time")
            val mode = it.getColumnIndex("_mode")
            val steps = it.getColumnIndex("_steps")
            if (begin < 0 || end < 0 || steps < 0) {
                log.note(ReadSource.Xiaomi, context.getString(R.string.log_xiaomi_columns, it.count, columns))
                return
            }
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            var todaySteps = 0L
            var lastEnd = Long.MIN_VALUE
            var last = ""
            while (it.moveToNext()) {
                val endMs = it.getLong(end)
                val count = it.getLong(steps)
                if (Instant.ofEpochMilli(endMs).atZone(zone).toLocalDate() == today) todaySteps += count
                if (endMs > lastEnd) {
                    lastEnd = endMs
                    val modeText = if (mode >= 0) it.getInt(mode).toString() else "-"
                    last = "${format(it.getLong(begin), zone)}–${format(endMs, zone)} mod $modeText +$count"
                }
            }
            log.note(ReadSource.Xiaomi, context.getString(R.string.log_xiaomi_summary, it.count, todaySteps, last))
        }
    }

    private fun format(ms: Long, zone: ZoneId): String = Instant.ofEpochMilli(ms).atZone(zone).format(TIME_FORMAT)
}
