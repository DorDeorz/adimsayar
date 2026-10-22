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

internal val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenContainer,
    onPrimaryContainer = Color(0xFF0B2E0E),
    inversePrimary = GreenDark,
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF101F10),
    tertiary = Color(0xFF38656A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFBCEBF0),
    onTertiaryContainer = Color(0xFF002023),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Neutral95,
    onBackground = Color(0xFF1A1C19),
    surface = Neutral95,
    onSurface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFFDEE5D8),
    onSurfaceVariant = Color(0xFF424940),
    surfaceTint = Green,
    inverseSurface = Color(0xFF2F312D),
    inverseOnSurface = Color(0xFFF0F1EB),
    outline = Color(0xFF72796F),
    outlineVariant = Color(0xFFC2C9BD),
    scrim = Color.Black,
    surfaceBright = Color(0xFFF8F8F4),
    surfaceDim = Color(0xFFD9DAD4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFECEDE8),
    surfaceContainer = Neutral90,
    surfaceContainerHigh = Color(0xFFE1E2DC),
    surfaceContainerHighest = Color(0xFFDCDDD7),
)
internal val DarkColors = darkColorScheme(
    primary = GreenDark,
    onPrimary = Color(0xFF0A3910),
    primaryContainer = GreenContainerDark,
    onPrimaryContainer = GreenContainer,
    inversePrimary = Green,
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B39),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFA0CFD4),
    onTertiary = Color(0xFF00363B),
    tertiaryContainer = Color(0xFF1F4D52),
    onTertiaryContainer = Color(0xFFBCEBF0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Neutral10,
    onBackground = Color(0xFFE2E3DD),
    surface = Neutral10,
    onSurface = Color(0xFFE2E3DD),
    surfaceVariant = Color(0xFF424940),
    onSurfaceVariant = Color(0xFFC2C9BD),
    surfaceTint = GreenDark,
    inverseSurface = Color(0xFFE2E3DD),
    inverseOnSurface = Color(0xFF2F312D),
    outline = Color(0xFF8C9388),
    outlineVariant = Color(0xFF424940),
    scrim = Color.Black,
    surfaceBright = Color(0xFF393A36),
    surfaceDim = Neutral10,
    surfaceContainerLowest = Color(0xFF151614),
    surfaceContainerLow = Color(0xFF1F201E),
    surfaceContainer = Neutral17,
    surfaceContainerHigh = Color(0xFF2D2F2B),
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

private val BaseTypography = Typography()

private fun TextStyle.tabular() = copy(fontFeatureSettings = "tnum")

private val AppTypography = BaseTypography.copy(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 48.sp, fontFeatureSettings = "tnum"),
    displayMedium = BaseTypography.displayMedium.tabular(),
    displaySmall = BaseTypography.displaySmall.tabular(),
    headlineLarge = BaseTypography.headlineLarge.tabular(),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, fontFeatureSettings = "tnum"),
    headlineSmall = BaseTypography.headlineSmall.tabular(),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, fontFeatureSettings = "tnum"),
    titleMedium = BaseTypography.titleMedium.tabular(),
    titleSmall = BaseTypography.titleSmall.tabular(),
    bodyLarge = TextStyle(fontSize = 16.sp, fontFeatureSettings = "tnum"),
    bodyMedium = BaseTypography.bodyMedium.tabular(),
    bodySmall = BaseTypography.bodySmall.tabular(),
    labelLarge = BaseTypography.labelLarge.tabular(),
    labelMedium = BaseTypography.labelMedium.tabular(),
    labelSmall = BaseTypography.labelSmall.tabular(),
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
