package net.marllex.waselak.feature.cashier.attendance.components

import androidx.compose.runtime.Composable

/**
 * Platform-specific QR scanner dialog.
 * On Android, uses CameraX and ML Kit for real QR code scanning.
 * On other platforms, shows a placeholder indicating QR scanning is unavailable.
 */
@Composable
expect fun QrScannerDialog(
    title: String,
    onQrCodeScanned: (String) -> Unit,
    onDismiss: () -> Unit,
)
