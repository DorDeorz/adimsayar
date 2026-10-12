package com.dordeorz.adimsayar.data

import java.io.BufferedInputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.zip.ZipInputStream
import kotlin.math.roundToLong

object HistoryImport {

    private const val MAX_STEPS = 1_000_000L
    private const val HEADER_LINES = 3
    private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}""")
    private val JSON_STEPS = Regex(""""steps"\s*:\s*(\d+)""")
    private val DATE_COLUMNS = setOf("date", "tarih", "day", "datum", "fecha", "data", "jour")
    private val STEP_WORDS = listOf("step", "adim", "adım", "schritt", "pasos", "passi", "pas")

    fun read(input: InputStream, zone: ZoneId): Map<LocalDate, Long> {
        val buffered = BufferedInputStream(input)
        buffered.mark(4)
        val magic = ByteArray(2)
        val read = buffered.read(magic)
        buffered.reset()
        if (read == 2 && magic[0] == 'P'.code.toByte() && magic[1] == 'K'.code.toByte()) return readZip(buffered, zone)
        return buffered.bufferedReader().useLines { parse(it, zone) }
    }

    private fun readZip(input: InputStream, zone: ZoneId): Map<LocalDate, Long> {
        val result = HashMap<LocalDate, Long>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory || !entry.name.endsWith(".csv", ignoreCase = true)) continue
                val lines = zip.bufferedReader().lineSequence()
                mergeMax(result, parse(lines, zone))
            }
        }
        return result
    }

    fun parse(lines: Sequence<String>, zone: ZoneId): Map<LocalDate, Long> {
        val iterator = lines.iterator()
        val head = ArrayList<String>()
        while (head.size < HEADER_LINES && iterator.hasNext()) head += iterator.next()
        val headerIndex = head.indexOfFirst { format(splitCsv(it)) != null }
        if (headerIndex < 0) return simple(head.asSequence() + iterator.asSequence())
        val header = splitCsv(head[headerIndex])
        val reader = format(header) ?: return emptyMap()
        val result = HashMap<LocalDate, Long>()
        val rows = head.drop(headerIndex + 1).asSequence() + iterator.asSequence()
        for (line in rows) {
            if (line.isBlank()) continue
            val (date, steps) = reader(splitCsv(line), zone) ?: continue
            if (steps in 0L..MAX_STEPS && steps > (result[date] ?: -1L)) result[date] = steps
        }
        return result
    }

    private fun format(header: List<String>): ((List<String>, ZoneId) -> Pair<LocalDate, Long>?)? {
        val names = header.map { it.trim().trim('"').substringAfterLast('.').lowercase() }
        val dayTime = names.indexOf("day_time")
        val samsungSteps = names.indexOf("step_count").takeIf { it >= 0 } ?: names.indexOf("count")
        if (dayTime >= 0 && samsungSteps >= 0) {
            return { row, _ ->
                val ms = row.getOrNull(dayTime)?.trim()?.toLongOrNull()
                val steps = number(row.getOrNull(samsungSteps))
                if (ms != null && steps != null) Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate() to steps else null
            }
        }
        val key = names.indexOf("key")
        val value = names.indexOf("value")
        val time = names.indexOf("time")
        if (key >= 0 && value >= 0 && time >= 0) {
            val tag = names.indexOf("tag")
            return { row, zone ->
                val isSteps = row.getOrNull(key)?.trim().equals("steps", ignoreCase = true) &&
                    (tag < 0 || row.getOrNull(tag)?.trim()?.let { it.isEmpty() || it.equals("daily_report", ignoreCase = true) } == true)
                val seconds = row.getOrNull(time)?.trim()?.toLongOrNull()
                val steps = row.getOrNull(value)?.let { JSON_STEPS.find(it)?.groupValues?.get(1)?.toLongOrNull() }
                if (isSteps && seconds != null && steps != null) Instant.ofEpochSecond(seconds).atZone(zone).toLocalDate() to steps else null
            }
        }
        val date = names.indexOfFirst { it in DATE_COLUMNS }
        val steps = names.indexOfFirst { name -> STEP_WORDS.any { name.contains(it) } }
        if (date >= 0 && steps >= 0 && date != steps) {
            return { row, _ ->
                val day = parseDate(row.getOrNull(date))
                val count = number(row.getOrNull(steps))
                if (day != null && count != null) day to count else null
            }
        }
        return null
    }

    private fun simple(lines: Sequence<String>): Map<LocalDate, Long> {
        val result = HashMap<LocalDate, Long>()
        for (line in lines) {
            val parts = line.trim().split(',', ';')
            if (parts.size < 2) continue
            val date = parseDate(parts[0]) ?: continue
            val steps = number(parts[1])?.takeIf { it in 0L..MAX_STEPS } ?: continue
            result[date] = steps
        }
        return result
    }

    private fun parseDate(text: String?): LocalDate? {
        val match = text?.trim()?.trim('"')?.let { ISO_DATE.find(it) } ?: return null
        return try {
            LocalDate.parse(match.value)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private fun number(text: String?): Long? =
        text?.trim()?.trim('"')?.replace(',', '.')?.toDoubleOrNull()?.takeIf { it >= 0.0 }?.roundToLong()

    private fun mergeMax(into: HashMap<LocalDate, Long>, from: Map<LocalDate, Long>) {
        for ((date, steps) in from) if (steps > (into[date] ?: -1L)) into[date] = steps
    }

    fun splitCsv(line: String): List<String> {
        val result = ArrayList<String>()
        val current = StringBuilder()
        var quoted = false
        var i = 0
        val separator = if (!line.contains(',') && line.contains(';')) ';' else ','
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> quoted = !quoted
                c == separator && !quoted -> {
                    result += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result += current.toString()
        return result
    }
}
