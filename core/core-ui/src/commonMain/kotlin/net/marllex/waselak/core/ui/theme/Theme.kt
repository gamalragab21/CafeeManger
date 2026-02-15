package net.marllex.waselak.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    background = BackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = SurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF475569),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    inversePrimary = PrimaryLight,
    surfaceTint = Primary,
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryContainer,
    primaryContainer = PrimaryDark,
    onPrimaryContainer = PrimaryContainer,
    secondary = SecondaryLight,
    onSecondary = OnSecondaryContainer,
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = SecondaryContainer,
    tertiary = TertiaryLight,
    onTertiary = OnTertiaryContainer,
    tertiaryContainer = TertiaryDark,
    onTertiaryContainer = TertiaryContainer,
    error = ErrorLight,
    onError = Color(0xFF7F1D1D),
    errorContainer = Color(0xFF991B1B),
    onErrorContainer = Color(0xFFFEE2E2),
    background = BackgroundDark,
    onBackground = Color(0xFFF1F5F9),
    surface = SurfaceDark,
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF1E293B),
    inversePrimary = Primary,
    surfaceTint = PrimaryLight,
)

@Composable
fun WaselakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WaselakTypography,
        content = content
    )
}
