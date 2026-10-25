package com.dordeorz.adimsayar.band

import kotlin.math.abs

/** Weather as the band shows it. Temperatures are °C, condition codes are the band's own. */
class BandWeatherReport(
    val place: String,
    val publishedIso: String,
    val condition: Int,
    val temperature: Int,
    val humidity: Int,
    val windBeaufort: Int,
    val windDirection: Int,
    val uv: Int,
    val pressureHpa: Float,
    val days: List<Day>,
    val hours: List<Hour>,
) {
    class Day(val min: Int, val max: Int, val condition: Int, val sunriseIso: String, val sunsetIso: String)
    class Hour(val temperature: Int, val condition: Int, val windBeaufort: Int, val windDirection: Int)

    /** Stable key for the place; the band lists weather places by it. */
    val key: String get() = "accu:" + abs(place.hashCode() % 1_000_000)
}

/** Builds the Xiaomi weather commands (type 10), following Gadgetbridge's XiaomiWeatherService. */
object XiaomiWeather {

    const val TYPE = 10
    const val CURRENT = 0
    const val DAILY = 1
    const val HOURLY = 2
    const val REQUEST = 3
    const val GET_LOCATIONS = 5
    const val ADD_LOCATION = 7
    private const val MAX_DAYS = 7
    private const val MAX_HOURS = 23
    private const val CELSIUS = "℃"

    fun location(report: BandWeatherReport): ProtoWriter = ProtoWriter().string(1, report.key).string(2, report.place)

    fun current(report: BandWeatherReport): ProtoWriter = ProtoWriter().message(
        1,
        ProtoWriter()
            .message(1, metadata(report))
            .uint(2, report.condition.toLong())
            .message(3, unit(CELSIUS, report.temperature))
            .message(4, unit("%", report.humidity))
            .message(5, unit(report.windDirection.toString(), report.windBeaufort))
            .message(6, unit("", report.uv))
            .message(7, unit("Unknown", 0))
            .message(8, ProtoWriter())
            .float(9, report.pressureHpa * 100f),
    )

    fun daily(report: BandWeatherReport): ProtoWriter {
        val entries = ProtoWriter()
        report.days.take(MAX_DAYS).forEach { day ->
            entries.message(
                1,
                ProtoWriter()
                    .message(1, unit("Unknown", 0))
                    .message(2, range(day.condition, day.condition))
                    .message(3, range(day.min, day.max))
                    .string(4, CELSIUS)
                    .message(5, ProtoWriter().string(1, day.sunriseIso).string(2, day.sunsetIso)),
            )
        }
        return forecast(report, entries)
    }

    fun hourly(report: BandWeatherReport): ProtoWriter {
        val entries = ProtoWriter()
        report.hours.take(MAX_HOURS).forEach { hour ->
            entries.message(
                1,
                ProtoWriter()
                    .message(1, unit("Unknown", 0))
                    .message(2, range(0, hour.condition))
                    .message(3, range(0, hour.temperature))
                    .string(4, CELSIUS)
                    .message(6, unit(hour.windDirection.toString(), hour.windBeaufort)),
            )
        }
        return forecast(report, entries)
    }

    /** Place keys the band already has, from a get-locations answer; null when it keeps only one place. */
    fun locationKeys(weather: ProtoMessage?): Set<String> =
        weather?.message(4)?.messages(1).orEmpty().mapNotNull { it.string(1) }.toSet()

    /** WMO weather code (Open-Meteo) to the band's condition code. */
    fun fromWmo(code: Int): Int = when (code) {
        0, 1 -> 0
        2 -> 1
        3 -> 2
        45, 48 -> 18
        51, 53, 55, 61 -> 7
        56, 57, 66, 67 -> 19
        63 -> 8
        65 -> 10
        71, 77 -> 14
        73 -> 15
        75, 86 -> 16
        80, 81 -> 3
        82 -> 9
        85 -> 13
        95, 96, 99 -> 4
        else -> 1
    }

    /** Wind speed in km/h to the Beaufort scale. */
    fun beaufort(kmh: Double): Int {
        val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
        return limits.indexOfFirst { kmh < it }.let { if (it < 0) 12 else it }
    }

    private fun forecast(report: BandWeatherReport, entries: ProtoWriter): ProtoWriter =
        ProtoWriter().message(2, ProtoWriter().message(1, metadata(report)).message(2, entries))

    private fun metadata(report: BandWeatherReport) = ProtoWriter()
        .string(1, report.publishedIso)
        .string(2, "")
        .string(3, report.place)
        .string(4, report.key)
        .bool(5, true)

    private fun unit(unit: String, value: Int) = ProtoWriter().string(1, unit).sint(2, value)

    private fun range(from: Int, to: Int) = ProtoWriter().sint(1, from).sint(2, to)
}
