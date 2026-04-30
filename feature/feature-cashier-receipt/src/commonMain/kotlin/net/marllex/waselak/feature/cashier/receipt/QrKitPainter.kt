package net.marllex.waselak.feature.cashier.receipt

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * QR-code painter wrapper. Was a direct call to network.chaintech's
 * `qr-kit:3.1.3` `rememberQrKitPainter()` but that library's iosArm64
 * artifact fails to load under Kotlin/Native 2.1.0 (KLIB resolver
 * path-format mismatch, see issue tracker for `qr-kit#XX`).
 *
 * Solution: expect/actual.
 *  - androidMain delegates to the original library so Android receipts
 *    keep their QR codes.
 *  - iosMain returns an empty `ColorPainter(Transparent)` for now —
 *    receipts on iOS show no QR. Replace with an AVFoundation-based
 *    generator (CIQRCodeGenerator filter) when we ship iOS receipts.
 */
@Composable
expect fun rememberQrKitPainterMP(data: String): Painter
