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

private val Green = Color(0xFF2E7D32)
private val GreenDark = Color(0xFF81C784)
private val Neutral10 = Color(0xFF1B1C1A)
private val Neutral95 = Color(0xFFF2F2EE)

private val LightColors = lightColorScheme(primary = Green, background = Neutral95, surface = Neutral95)
private val DarkColors = darkColorScheme(primary = GreenDark, background = Neutral10, surface = Neutral10)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 64.sp, fontFeatureSettings = "tnum"),
    bodyLarge = TextStyle(fontSize = 16.sp, fontFeatureSettings = "tnum"),
)

@Composable
fun AdimSayarTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, typography = AppTypography, content = content)
}
