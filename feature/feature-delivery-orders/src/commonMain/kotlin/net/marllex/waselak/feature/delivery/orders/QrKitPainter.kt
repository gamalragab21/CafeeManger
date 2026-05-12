package net.marllex.waselak.feature.delivery.orders

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * QR-code painter wrapper. Mirrors the pattern in feature-cashier-receipt's
 * QrKitPainter — see that file for the long version. Summary: the
 * `network.chaintech:qr-kit:3.1.3` library only links on Android. iOS
 * fails to load it under Kotlin/Native (KLIB resolver path-format mismatch)
 * and Desktop has no equivalent target. So we expose this expect/actual
 * pair and confine the real qr-kit dependency to androidMain.
 *
 *  - androidMain — real QR code via `rememberQrKitPainter`.
 *  - desktopMain / iosMain — transparent placeholder. Receipts still
 *    lay out correctly; replace with zxing (JVM) / CIQRCodeGenerator
 *    (iOS) when those platforms grow first-class receipt support.
 */
@Composable
expect fun rememberQrKitPainterMP(data: String): Painter
