package net.marllex.waselak.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
actual fun QrCodeImage(
    content: String,
    modifier: Modifier,
) {
    // iOS: QR code generation via CoreImage not yet implemented
    // Show URL text as fallback
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
