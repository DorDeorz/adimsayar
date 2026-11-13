package com.dordeorz.adimsayar.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period

/** Age and birthday dates from a birth date. A 29 February birthday is kept on 28 February in other years. */
object Birthday {

    val NOTIFY_TIME: LocalTime = LocalTime.of(9, 0)

    fun age(birth: LocalDate, today: LocalDate): Int = Period.between(birth, today).years.coerceAtLeast(0)

    /** The birthday in [year]. */
    fun inYear(birth: LocalDate, year: Int): LocalDate = birth.withYear(year)

    fun isBirthday(birth: LocalDate, today: LocalDate): Boolean = inYear(birth, today.year) == today

    /** The age reached on the birthday in [year]. */
    fun turning(birth: LocalDate, year: Int): Int = year - birth.year

    /** The oldest and youngest birth dates the settings accept, so the age stays in [AGE_RANGE]. */
    fun earliest(today: LocalDate): LocalDate = today.minusYears(AGE_RANGE.last.toLong() + 1).plusDays(1)

    fun latest(today: LocalDate): LocalDate = today.minusYears(AGE_RANGE.first.toLong())

    fun allowed(date: LocalDate, today: LocalDate): Boolean = !date.isBefore(earliest(today)) && !date.isAfter(latest(today))

    fun clamp(date: LocalDate, today: LocalDate): LocalDate = when {
        date.isBefore(earliest(today)) -> earliest(today)
        date.isAfter(latest(today)) -> latest(today)
        else -> date
    }

    /**
     * When to send the next birthday greeting: 09:00 on the next birthday. On the birthday itself a greeting
     * not sent yet goes out right away, for a phone that was off at 09:00 or a birth date set during the day.
     */
    fun nextNotify(birth: LocalDate, now: LocalDateTime, notifiedYear: Int): LocalDateTime {
        val today = now.toLocalDate()
        if (isBirthday(birth, today) && notifiedYear != today.year) {
            val morning = today.atTime(NOTIFY_TIME)
            return if (now.isAfter(morning)) now else morning
        }
        val thisYear = inYear(birth, today.year)
        val next = if (thisYear.isAfter(today)) thisYear else inYear(birth, today.year + 1)
        return next.atTime(NOTIFY_TIME)
    }
}
