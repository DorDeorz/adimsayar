package com.dordeorz.adimsayar.ui

import com.dordeorz.adimsayar.Locales
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val appLocale: Locale get() = Locales.current
internal val TIME_FORMAT: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM HH:mm", appLocale)
internal val DAY_FORMAT: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMM", appLocale)
internal val LONG_DAY_FORMAT: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMMM EEEE", appLocale)
internal val FULL_DATE_FORMAT: DateTimeFormatter get() = DateTimeFormatter.ofPattern("d MMMM yyyy", appLocale)
internal val MONTH_FORMAT: DateTimeFormatter get() = DateTimeFormatter.ofPattern("LLLL yyyy", appLocale)

internal fun format(steps: Long): String = NumberFormat.getIntegerInstance(appLocale).format(steps)

internal fun thousandSuffix(locale: Locale): String = if (locale.language == "tr") "B" else "k"

internal fun formatCompact(steps: Long): String = when {
    steps < 1_000L -> steps.toString()
    steps < 100_000L -> String.format(appLocale, "%.1f%s", steps / 1_000.0, thousandSuffix(appLocale))
    else -> String.format(appLocale, "%d%s", steps / 1_000L, thousandSuffix(appLocale))
}

internal fun shortDayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, appLocale)

internal fun percent(steps: Long, goal: Long): Int = if (goal <= 0L) 0 else ((steps * 100L) / goal).toInt()

internal fun formatDecimal(value: Double): String = String.format(appLocale, "%.1f", value)
