package com.dordeorz.adimsayar.ui

import android.text.format.DateFormat
import com.dordeorz.adimsayar.Locales
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal val appLocale: Locale get() = Locales.current
internal val TIME_FORMAT: DateTimeFormatter get() = localized("dMMMHHmm")
internal val DAY_FORMAT: DateTimeFormatter get() = localized("dMMM")
internal val SHORT_DATE_FORMAT: DateTimeFormatter get() = localized("dMMMy")
internal val LONG_DAY_FORMAT: DateTimeFormatter get() = localized("dMMMMEEEE")
internal val FULL_DATE_FORMAT: DateTimeFormatter get() = localized("dMMMMy")
internal val MONTH_FORMAT: DateTimeFormatter get() = localized("yMMMM")

private val formatters = ConcurrentHashMap<Pair<Locale, String>, DateTimeFormatter>()

private fun localized(skeleton: String): DateTimeFormatter = formatters.getOrPut(appLocale to skeleton) {
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(appLocale, skeleton), appLocale)
}

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
