package com.dordeorz.adimsayar.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import com.dordeorz.adimsayar.Locales
import com.dordeorz.adimsayar.MainActivity
import com.dordeorz.adimsayar.ui.theme.DarkColors
import com.dordeorz.adimsayar.ui.theme.LightColors
import com.dordeorz.adimsayar.ui.thousandSuffix
import java.text.NumberFormat
import java.time.format.TextStyle

object Widgets {

    suspend fun updateAll(context: Context) {
        TodayWidget().updateAll(context)
        WeekWidget().updateAll(context)
        StreakWidget().updateAll(context)
        MonthWidget().updateAll(context)
    }
}

private val AppColors = ColorProviders(light = LightColors, dark = DarkColors)

@Composable
internal fun WidgetTheme(dynamicColor: Boolean, content: @Composable () -> Unit) {
    if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme(content = content)
    } else {
        GlanceTheme(colors = AppColors, content = content)
    }
}

@Composable
internal fun widgetSurface(): GlanceModifier = GlanceModifier
    .fillMaxSize()
    .appWidgetBackground()
    .cornerRadius(24.dp)
    .background(GlanceTheme.colors.widgetBackground)
    .clickable(actionStartActivity<MainActivity>())
    .padding(12.dp)

internal fun formatSteps(steps: Long): String = NumberFormat.getIntegerInstance(Locales.current).format(steps)

internal fun formatCompact(steps: Long): String = if (steps < 1_000L) {
    steps.toString()
} else {
    String.format(Locales.current, "%.1f%s", steps / 1_000.0, thousandSuffix(Locales.current))
}

internal fun shortDayName(date: java.time.LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locales.current)
