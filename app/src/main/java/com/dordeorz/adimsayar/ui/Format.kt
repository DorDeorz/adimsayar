package com.dordeorz.adimsayar.ui

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

internal val TURKISH: Locale = Locale.forLanguageTag("tr-TR")
internal val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", TURKISH)
internal val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", TURKISH)
internal val LONG_DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM EEEE", TURKISH)
internal val FULL_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", TURKISH)
internal val MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("LLLL yyyy", TURKISH)

internal fun format(steps: Long): String = NumberFormat.getIntegerInstance(TURKISH).format(steps)

internal fun formatCompact(steps: Long): String = when {
    steps < 1_000L -> steps.toString()
    steps < 100_000L -> String.format(TURKISH, "%.1fB", steps / 1_000.0)
    else -> String.format(TURKISH, "%dB", steps / 1_000L)
}

internal fun shortDayName(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, TURKISH)

internal fun percent(steps: Long, goal: Long): Int = if (goal <= 0L) 0 else ((steps * 100L) / goal).toInt()

internal fun formatDecimal(value: Double): String = String.format(TURKISH, "%.1f", value)
