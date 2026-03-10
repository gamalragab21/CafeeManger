package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable

@Composable
actual fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): () -> Unit {
    // iOS image picker - placeholder implementation
    // TODO: Implement using UIImagePickerController or PHPickerViewController
    return { onResult(null) }
}
