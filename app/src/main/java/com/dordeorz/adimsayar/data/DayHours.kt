package com.dordeorz.adimsayar.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/** Steps hour by hour: the phone's record, with the band's count in the hours the band sent data. */
object DayHours {

    suspend fun day(context: Context, date: LocalDate): LongArray {
        val hours = if (XiaomiSteps.isEnabled(context)) {
            XiaomiSteps.dayDetail(context, date)?.hours ?: LongArray(HOURS)
        } else {
            StepRepository.get(context).loadPhoneHours(date) ?: LongArray(HOURS)
        }
        if (BandDirect.isEnabled(context)) {
            for ((hour, steps) in StepRepository.get(context).loadBandHours(date)) {
                if (hour.toLocalDate() == date) hours[hour.hour] = steps
            }
        }
        return hours
    }

    /** Every hour from [from] on, for the active hours map and the walking pace. */
    suspend fun since(context: Context, from: LocalDate): Map<LocalDateTime, Long> {
        val repository = StepRepository.get(context)
        val result = HashMap(repository.loadPhoneHourMap(from))
        if (BandDirect.isEnabled(context)) result.putAll(repository.loadBandHours(from))
        return result
    }

    const val HOURS = 24
}
