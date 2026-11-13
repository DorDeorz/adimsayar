package com.dordeorz.adimsayar.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/** Steps hour by hour: the phone's record, with the band's count in the hours the band sent data. */
object DayHours {

    suspend fun day(context: Context, date: LocalDate): LongArray = detail(context, date).hours

    /**
     * The hours of [date] with the phone's walk and run split. The split is left out on a day the band sent
     * hours, because the band's steps have no split and the two numbers would not add up to the day.
     */
    suspend fun detail(context: Context, date: LocalDate): DayDetail {
        val xiaomi = XiaomiSteps.isEnabled(context)
        val phone = if (xiaomi) XiaomiSteps.dayDetail(context, date) else null
        val phoneHours = when {
            phone != null -> phone.hours
            xiaomi -> LongArray(HOURS)
            else -> StepRepository.get(context).loadPhoneHours(date) ?: LongArray(HOURS)
        }
        val phoneMap = HashMap<LocalDateTime, Long>()
        for (hour in 0 until HOURS) if (phoneHours[hour] > 0L) phoneMap[date.atTime(hour, 0)] = phoneHours[hour]
        val band = bandHours(context, date).filterKeys { it.toLocalDate() == date }
        val hours = LongArray(HOURS)
        for ((hour, steps) in StepMath.shownHours(band, phoneMap, WearableSteps.isBandOnly(context))) hours[hour.hour] = steps
        val bandDay = StepMath.bandDays(band).isNotEmpty()
        return if (phone == null || bandDay) DayDetail(hours, 0L, 0L) else DayDetail(hours, phone.walking, phone.running)
    }

    /** Every hour from [from] on, for the active hours map, the walking pace and the steps written to Health Connect. */
    suspend fun since(context: Context, from: LocalDate): Map<LocalDateTime, Long> {
        // The phone's own record keeps its hours itself; they are not in our table.
        val recorded = if (XiaomiSteps.isEnabled(context)) XiaomiSteps.hourlyTotals(context, from) else null
        val phone = recorded ?: StepRepository.get(context).loadPhoneHourMap(from)
        return StepMath.shownHours(bandHours(context, from), phone, WearableSteps.isBandOnly(context))
    }

    private suspend fun bandHours(context: Context, from: LocalDate): Map<LocalDateTime, Long> =
        if (BandDirect.isEnabled(context)) StepRepository.get(context).loadBandHours(from) else emptyMap()

    const val HOURS = 24
}
