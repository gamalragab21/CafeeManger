package net.marllex.waselak.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp

// ═══════════════════════════════════════════════════════════════════
//  Receipt color provider — adapts to light/dark
// ═══════════════════════════════════════════════════════════════════
data class ReceiptColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val totalText: Color,
    val sectionBg: Color,
)

val LightReceiptColors = ReceiptColors(
    background = ReceiptBackground,
    surface = ReceiptSurface,
    textPrimary = ReceiptTextPrimary,
    textSecondary = ReceiptTextSecondary,
    divider = ReceiptDivider,
    totalText = ReceiptTotalText,
    sectionBg = ReceiptSectionBg,
)

val DarkReceiptColors = ReceiptColors(
    background = ReceiptBackgroundDark,
    surface = ReceiptSurfaceDark,
    textPrimary = ReceiptTextPrimaryDark,
    textSecondary = ReceiptTextSecondaryDark,
    divider = ReceiptDividerDark,
    totalText = ReceiptTotalTextDark,
    sectionBg = ReceiptSectionBgDark,
)

val LocalReceiptColors = staticCompositionLocalOf { LightReceiptColors }

// ═══════════════════════════════════════════════════════════════════
//  Material3 Shapes — Clean & Minimal with rounded corners
// ═══════════════════════════════════════════════════════════════════
val WaselakShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// ═══════════════════════════════════════════════════════════════════
//  Color Schemes
// ═══════════════════════════════════════════════════════════════════
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
    onBackground = Color(0xFFE2E8F0),
    surface = SurfaceDark,
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = Color(0xFFF1F5F9),
    inverseOnSurface = Color(0xFF1E293B),
    inversePrimary = Primary,
    surfaceTint = PrimaryLight,
)

// ═══════════════════════════════════════════════════════════════════
//  WaselakTheme
// ═══════════════════════════════════════════════════════════════════
@Composable
fun WaselakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val receiptColors = if (darkTheme) DarkReceiptColors else LightReceiptColors

    CompositionLocalProvider(LocalReceiptColors provides receiptColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WaselakTypography,
            shapes = WaselakShapes,
        ) {
            DismissKeyboardOnOutsideTap { content() }
        }
    }
}

/**
 * Wraps content in a full-screen tap region that dismisses the soft
 * keyboard when the user taps anywhere not handled by another widget.
 *
 * Why it lives in WaselakTheme: applied here, the behaviour is global —
 * every screen across Manager / Cashier / Delivery / KDS gets it for
 * free, on both Android and iOS. `focusManager.clearFocus()` is the
 * cross-platform Compose call that:
 *   • Android: clears the focused TextField → the system IME hides
 *   • iOS: triggers `resignFirstResponder` on the focused input view →
 *     the iOS keyboard slides down
 *
 * Why it matters especially on iOS: `KeyboardType.Phone` shows the
 * number-pad keyboard which has NO Done / Return key by design, so
 * without this gesture there is literally no way to dismiss the
 * keyboard from the phone field.
 *
 * Implementation note — why `awaitPointerEvent(Final)` and NOT
 * `detectTapGestures`:
 *
 * `detectTapGestures` runs in the Main pass (default), which means it
 * competes with `verticalScroll` / `LazyColumn` / nested scrollables for
 * the down event. In several Compose Multiplatform versions this
 * subtly breaks scrolling on screens where this composable wraps the
 * scrollable content — reported as "I can't scroll the login screen".
 *
 * Switching to `awaitPointerEvent(PointerEventPass.Final)` makes us
 * observe the press AFTER children. By that pass, `verticalScroll` has
 * already claimed the down event for itself when the user is starting
 * a drag, and `event.changes.any { it.isConsumed }` reports true → we
 * skip the focus-clear, the scroll continues uninterrupted. We don't
 * consume anything here, so children always get full event delivery.
 *
 * Effect by gesture:
 *   • Tap on TextField   — TextField consumes → we skip → focus moves there ✓
 *   • Tap on Button      — Button consumes → we skip → onClick runs ✓
 *   • Tap on background  — nobody consumes → we clear focus → keyboard hides ✓
 *   • Drag (scroll)      — scroll consumes the press → we skip → scroll works ✓
 */
@Composable
private fun DismissKeyboardOnOutsideTap(content: @Composable () -> Unit) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        if (event.type == PointerEventType.Press) {
                            val hasFreshDown = event.changes.any { it.changedToDownIgnoreConsumed() }
                            val anyConsumed = event.changes.any { it.isConsumed }
                            if (hasFreshDown && !anyConsumed) {
                                focusManager.clearFocus()
                            }
                        }
                    }
                }
            },
    ) {
        content()
    }
}
