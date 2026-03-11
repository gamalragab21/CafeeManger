package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): () -> Unit {
    // Image picking on web requires HTML file input interop — no-op for now
    return { onResult(null) }
}
