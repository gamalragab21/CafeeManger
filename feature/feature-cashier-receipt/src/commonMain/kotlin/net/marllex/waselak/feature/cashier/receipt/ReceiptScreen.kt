package net.marllex.waselak.feature.cashier.receipt

import androidx.compose.runtime.Composable

/**
 * Platform-specific receipt screen.
 * On Android, uses native Canvas for bitmap rendering, PrintHelper for printing,
 * and ZXing for QR code generation.
 * On other platforms, shows a placeholder.
 */
@Composable
expect fun ReceiptScreen(
    onBack: () -> Unit = {},
)
