package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun BarcodeScannerView(
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
) {
    // TODO: Implement iOS barcode scanning with AVFoundation
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text("Barcode scanning coming soon on iOS")
    }
}
