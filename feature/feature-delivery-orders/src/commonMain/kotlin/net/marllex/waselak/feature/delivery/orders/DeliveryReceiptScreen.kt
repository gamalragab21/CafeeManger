package net.marllex.waselak.feature.delivery.orders

import androidx.compose.runtime.Composable

/**
 * Platform-specific delivery receipt screen.
 * On Android, uses native Canvas for bitmap rendering
 * and ZXing for QR code generation.
 * On other platforms, shows a placeholder.
 */
@Composable
expect fun DeliveryReceiptScreen(
    onBack: () -> Unit = {},
)
