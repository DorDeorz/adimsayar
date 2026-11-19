package com.dordeorz.adimsayar.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The band's and the phone's steps put together minute by minute (see [StepMath.splitHours]): in a minute the
 * band was worn its count stands, in the other minutes the phone's. Used for the day totals of a band synced
 * over Bluetooth and for the hour charts.
 */
object StepSources {

    suspend fun hours(context: Context, from: LocalDate): Map<LocalDateTime, HourSplit> {
        val zone = ZoneId.systemDefault()
        val repository = StepRepository.get(context)
        val band = BandDirect.isEnabled(context)
        val bandMinutes = if (band) repository.loadBandMinuteMap(from) else emptyMap()
        val bandHourly = if (band) repository.loadBandHourly(from) else emptyMap()
        val phone = phoneIntervals(context, from, zone)
        val split = StepMath.splitHours(bandMinutes, bandHourly, phone, zone)
        if (!WearableSteps.isBandOnly(context)) return split
        val bandDays = split.filterValues { it.band > 0L }.keys.map { it.toLocalDate() }.toSet()
        return split.mapValues { (hour, s) -> if (hour.toLocalDate() in bandDays) s.copy(phone = 0L) else s }
    }

    /** Totals of the days the band sent steps; a day with no phone hours (imported) keeps the larger count. */
    suspend fun bandDays(context: Context, from: LocalDate): Map<LocalDate, Long> {
        val split = hours(context, from)
        val bandDays = split.filterValues { it.band > 0L }.keys.map { it.toLocalDate() }.toSet()
        val zone = ZoneId.systemDefault()
        val phoneDays = phoneIntervals(context, from, zone).map { StepMath.dayOf(it.fromWallMs, zone) }.toSet()
        val totals = HashMap<LocalDate, Long>()
        for ((hour, s) in split) {
            val day = hour.toLocalDate()
            if (day in bandDays) totals[day] = (totals[day] ?: 0L) + s.total
        }
        val counted = StepRepository.get(context).loadPhoneDays(from)
        for (day in bandDays - phoneDays) {
            val phone = counted[day] ?: continue
            if (!WearableSteps.isBandOnly(context)) totals[day] = maxOf(totals[day] ?: 0L, phone)
        }
        return totals
    }

    /** Minutes the band was worn on [date]. */
    suspend fun wornMinutes(context: Context, date: LocalDate): Int {
        if (!BandDirect.isEnabled(context)) return 0
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toEpochSecond()
        val to = date.plusDays(1).atStartOfDay(zone).toEpochSecond()
        return StepRepository.get(context).loadBandMinuteMap(date).keys.count { it in from until to }
    }

    private suspend fun phoneIntervals(context: Context, from: LocalDate, zone: ZoneId): List<StepInterval> {
        if (XiaomiSteps.isEnabled(context)) return XiaomiSteps.intervals(context, from) ?: emptyList()
        return StepRepository.get(context).loadPhoneHourMap(from).map { (hour, steps) ->
            val start = hour.atZone(zone).toInstant().toEpochMilli()
            StepInterval(steps, start, hour.plusHours(1).atZone(zone).toInstant().toEpochMilli())
        }
    }
}
