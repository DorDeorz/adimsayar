package com.dordeorz.adimsayar.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProvider
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.ui.thousandSuffix
import java.text.NumberFormat
import java.time.format.TextStyle

object Widgets {

    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        WeekWidget().updateAll(context)
    }
}

internal val TextPrimary = ColorProvider(day = Color(0xFF1B1C1A), night = Color(0xFFF2F2EE))
internal val TextSecondary = ColorProvider(day = Color(0xFF5A5D57), night = Color(0xFFB5B8B0))
internal val Accent = ColorProvider(day = Color(0xFF2E7D32), night = Color(0xFF81C784))
internal val AccentMuted = ColorProvider(day = Color(0xFFC8E6C9), night = Color(0xFF2E4A30))

internal fun formatSteps(steps: Long): String = NumberFormat.getIntegerInstance(Locales.current).format(steps)

internal fun formatCompact(steps: Long): String = if (steps < 1_000L) {
    steps.toString()
} else {
    String.format(Locales.current, "%.1f%s", steps / 1_000.0, thousandSuffix(Locales.current))
}

internal fun shortDayName(date: java.time.LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locales.current)
