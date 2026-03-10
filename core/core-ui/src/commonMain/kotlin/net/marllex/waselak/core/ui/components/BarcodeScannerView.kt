package net.marllex.waselak.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific continuous barcode scanner.
 * - Android: CameraX + ML Kit real-time barcode detection.
 * - Desktop: Focused text field for USB barcode scanner input (keyboard HID).
 *
 * @param onBarcodeScanned called each time a barcode is detected.
 * @param modifier layout modifier.
 * @param enabled whether scanning is active.
 */
@Composable
expect fun BarcodeScannerView(
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
)
