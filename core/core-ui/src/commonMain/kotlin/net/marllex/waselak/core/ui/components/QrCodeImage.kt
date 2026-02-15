package net.marllex.waselak.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
)
