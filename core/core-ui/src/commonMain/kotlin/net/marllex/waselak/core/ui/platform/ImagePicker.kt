package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable

/**
 * Remembers a launcher that opens the system image picker.
 * When the user picks an image, [onResult] is called with the image bytes.
 * Returns a lambda that launches the picker when invoked.
 */
@Composable
expect fun rememberImagePickerLauncher(onResult: (ByteArray?) -> Unit): () -> Unit
