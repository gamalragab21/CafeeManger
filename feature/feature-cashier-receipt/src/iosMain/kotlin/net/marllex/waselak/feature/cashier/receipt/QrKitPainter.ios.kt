package net.marllex.waselak.feature.cashier.receipt

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/**
 * Placeholder iOS implementation — renders a transparent square in place
 * of the QR code. Acceptable for the first iOS build; the receipt UI
 * still lays out correctly. Replace with a CIQRCodeGenerator-based
 * Painter when we ship the iOS-side receipt feature for real.
 */
@Composable
actual fun rememberQrKitPainterMP(data: String): Painter = ColorPainter(Color.Transparent)
