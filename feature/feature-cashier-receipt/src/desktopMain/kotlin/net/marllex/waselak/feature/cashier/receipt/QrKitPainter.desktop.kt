package net.marllex.waselak.feature.cashier.receipt

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/**
 * Placeholder desktop implementation — same rationale as the iOS one.
 * Receipts on the desktop manager app don't print QR codes today.
 * Replace with a JVM-side QR generator (e.g., zxing) when needed.
 */
@Composable
actual fun rememberQrKitPainterMP(data: String): Painter = ColorPainter(Color.Transparent)
