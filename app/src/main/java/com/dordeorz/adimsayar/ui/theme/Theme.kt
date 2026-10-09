package com.dordeorz.adimsayar.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.dordeorz.adimsayar.data.ThemeMode

private val Green = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF81C784)
private val GreenContainer = Color(0xFFC8E6C9)
private val GreenContainerDark = Color(0xFF1F3A21)
private val Neutral10 = Color(0xFF1B1C1A)
private val Neutral17 = Color(0xFF262825)
private val Neutral90 = Color(0xFFE6E7E1)
private val Neutral95 = Color(0xFFF2F2EE)

private val LightColors = lightColorScheme(
    primary = Green,
    primaryContainer = GreenContainer,
    onPrimaryContainer = Color(0xFF0B2E0E),
    background = Neutral95,
    surface = Neutral95,
    surfaceContainer = Neutral90,
    surfaceContainerHighest = Color(0xFFDCDDD7),
)
private val DarkColors = darkColorScheme(
    primary = GreenDark,
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenContainer,
    background = Neutral10,
    surface = Neutral10,
    surfaceContainer = Neutral17,
    surfaceContainerHighest = Color(0xFF33352F),
)

val MedalColors = listOf(
    Color(0xFFB87333),
    Color(0xFF9EA4AA),
    Color(0xFFD4A62A),
    Color(0xFF3FA7A0),
    Color(0xFF7E57C2),
    Color(0xFFD84343),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 48.sp, fontFeatureSettings = "tnum"),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, fontFeatureSettings = "tnum"),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(fontSize = 16.sp, fontFeatureSettings = "tnum"),
)

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

@Composable
fun AdimSayarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
