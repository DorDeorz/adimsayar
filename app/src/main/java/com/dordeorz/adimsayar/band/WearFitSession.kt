package com.dordeorz.adimsayar.band

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * WearFit protocol (WearFit Pro watches such as the HK series, and the Makibes HR3), ported from
 * Gadgetbridge's MakibesHR3 support. Commands are `ab 00 [len] ff [cmd] 80 [args]`, reports come back
 * in the same frame. Only the fitness history request is ever sent: no settings, clock or firmware commands.
 */
class WearFitSession(private val zone: ZoneId = ZoneId.systemDefault(), private val now: () -> LocalDateTime = { LocalDateTime.now(zone) }) {

    /** Step value per watch hour (start of the hour), as the watch sends it. */
    private val stepSamples = sortedMapOf<LocalDateTime, Int>()
    private val heartSamples = ArrayList<BandMinute>()

    /** Today's total from the fitness report, if one arrived. */
    var todayTotal: Int? = null
        private set
    var reports: Int = 0
        private set

    fun request(since: LocalDateTime): ByteArray {
        val year = (since.year - 2000).coerceIn(0, 255)
        return frame(
            CMD_REQUEST_FITNESS,
            byteArrayOf(
                0, year.toByte(), since.monthValue.toByte(), since.dayOfMonth.toByte(), since.hour.toByte(), since.minute.toByte(),
                year.toByte(), since.monthValue.toByte(), since.dayOfMonth.toByte(), since.hour.toByte(), since.minute.toByte(),
            ),
        )
    }

    fun onReport(value: ByteArray) {
        if (value.size < 6 || value[0] != HEADER) return
        reports++
        val args = value.copyOfRange(6, value.size)
        val group = value[4]
        val sub = value[5]
        if (group != CMD_REQUEST_FITNESS) return
        when (sub) {
            SUB_FITNESS -> if (args.size >= 3) todayTotal = u(args[1]) * 0x100 + u(args[2])
            SUB_STEPS -> if (args.size >= 7) {
                val hour = time(args, 0) ?: return
                stepSamples[hour] = u(args[5]) * 0x100 + u(args[6])
            }
            SUB_HEART -> if (args.size >= 6) {
                val time = time(args, 0, withMinute = true) ?: return
                val heart = u(args[5])
                if (heart in 30..220) heartSamples += BandMinute(time.atZone(zone).toEpochSecond(), 0, heart)
            }
        }
    }

    /**
     * Steps per hour. Gadgetbridge reads the step value as the day's running total at that hour;
     * a day whose values go down is read as per-hour counts instead. Today's total from the
     * fitness report tops up the current hour, so the count is live even before the hour closes.
     */
    fun hours(): Map<LocalDateTime, Long> {
        val result = HashMap<LocalDateTime, Long>()
        for ((_, samples) in stepSamples.entries.groupBy { it.key.toLocalDate() }) {
            val values = samples.map { it.value }
            val cumulative = values.zipWithNext().all { (a, b) -> b >= a } && !perHour(samples.first().key.toLocalDate(), values)
            var previous = 0
            for ((hour, value) in samples) {
                val steps = if (cumulative) value - previous else value
                if (cumulative) previous = value
                if (steps > 0) result[hour] = steps.toLong()
            }
        }
        val total = todayTotal ?: return result
        val current = now().withMinute(0).withSecond(0).withNano(0)
        val today = current.toLocalDate()
        val counted = result.filterKeys { it.toLocalDate() == today }.values.sum()
        if (total > counted) result[current] = (result[current] ?: 0L) + total - counted
        return result
    }

    /** With today's total known, rising values that add up to it are per-hour counts, not a running total. */
    private fun perHour(day: LocalDate, values: List<Int>): Boolean {
        val total = todayTotal ?: return false
        if (day != now().toLocalDate() || values.size < 2) return false
        return abs(values.sum() - total) <= maxOf(10, total / 100)
    }

    fun minutes(): List<BandMinute> {
        val steps = hours().map { (hour, steps) -> BandMinute(hour.atZone(zone).toEpochSecond(), steps.toInt(), 0) }
        return (steps + heartSamples).sortedBy { it.epochSeconds }
    }

    val hasData: Boolean get() = stepSamples.isNotEmpty() || heartSamples.isNotEmpty() || (todayTotal ?: 0) > 0

    val summary: String get() = "${stepSamples.size} h, ${heartSamples.size} hr, today ${todayTotal ?: "-"}"

    private fun time(args: ByteArray, at: Int, withMinute: Boolean = false): LocalDateTime? = runCatching {
        LocalDateTime.of(u(args[at]) + 2000, u(args[at + 1]), u(args[at + 2]), u(args[at + 3]), if (withMinute) u(args[at + 4]) else 0)
    }.getOrNull()

    companion object {
        const val HEADER: Byte = 0xab.toByte()
        const val CMD_REQUEST_FITNESS: Byte = 0x51
        const val SUB_FITNESS: Byte = 0x08
        const val SUB_HEART: Byte = 0x11
        const val SUB_STEPS: Byte = 0x20

        private fun u(b: Byte) = b.toInt() and 0xff

        fun frame(command: Byte, args: ByteArray): ByteArray =
            byteArrayOf(HEADER, 0, (args.size + 3).toByte(), 0xff.toByte(), command, 0x80.toByte()) + args
    }
}
