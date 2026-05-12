package net.marllex.waselak.feature.delivery.orders

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter

/**
 * Placeholder desktop implementation — qr-kit doesn't link on the JVM
 * target. Receipts still lay out correctly with a transparent square in
 * place of the QR. Swap for a zxing-backed painter when desktop delivery
 * receipts grow real QR support.
 */
@Composable
actual fun rememberQrKitPainterMP(data: String): Painter = ColorPainter(Color.Transparent)
