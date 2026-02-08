package net.marllex.cafeemanger.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

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
    onBackground = Color(0xFF0F172A),       // Slate 950
    surface = SurfaceLight,
    onSurface = Color(0xFF0F172A),          // Slate 950
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF475569),   // Slate 600
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = Color(0xFF1E293B),     // Slate 800
    inverseOnSurface = Color(0xFFF1F5F9),   // Slate 100
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
    onError = Color(0xFF7F1D1D),            // Red 900
    errorContainer = Color(0xFF991B1B),     // Red 800
    onErrorContainer = Color(0xFFFEE2E2),   // Red 100
    background = BackgroundDark,
    onBackground = Color(0xFFF1F5F9),       // Slate 100
    surface = SurfaceDark,
    onSurface = Color(0xFFF1F5F9),          // Slate 100
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFF94A3B8),   // Slate 400
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = Color(0xFFF1F5F9),     // Slate 100
    inverseOnSurface = Color(0xFF1E293B),   // Slate 800
    inversePrimary = Primary,
    surfaceTint = PrimaryLight,
)

@Composable
fun CafeeMangerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: transparent status bar, themed nav bar
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CafeeMangerTypography,
        content = content
    )
}
