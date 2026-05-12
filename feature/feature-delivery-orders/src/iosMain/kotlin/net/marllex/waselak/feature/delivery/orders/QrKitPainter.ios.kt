package net.marllex.waselak.feature.delivery.orders

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/**
 * Placeholder iOS implementation — qr-kit fails to load under
 * Kotlin/Native 2.1.0 (KLIB resolver path-format mismatch). Replace with
 * a CIQRCodeGenerator-backed painter when iOS receipts grow first-class
 * QR support.
 */
@Composable
actual fun rememberQrKitPainterMP(data: String): Painter = ColorPainter(Color.Transparent)
