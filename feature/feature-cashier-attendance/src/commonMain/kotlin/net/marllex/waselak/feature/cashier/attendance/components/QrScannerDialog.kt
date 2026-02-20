package net.marllex.waselak.feature.cashier.attendance.components

import androidx.compose.runtime.Composable

/**
 * Platform-specific QR scanner dialog.
 * - Android: CameraX + ML Kit for real-time camera QR scanning.
 * - Desktop: USB scanner input field + image file decode with ZXing.
 * - iOS: AVFoundation camera with metadata QR detection.
 */
@Composable
expect fun QrScannerDialog(
    title: String,
    onQrCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
)
