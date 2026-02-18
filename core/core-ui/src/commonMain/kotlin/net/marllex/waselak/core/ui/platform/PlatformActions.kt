package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable

expect class PlatformActions {
    fun openUrl(url: String)
    fun openMap(address: String?, lat: Double?, lng: Double?)
    fun showToast(message: String)
    fun copyToClipboard(text: String)
    fun shareText(text: String, title: String = "")
    fun shareHtmlAsImage(htmlContent: String, fileName: String = "receipt")

    /**
     * Save a byte array to the Downloads folder (or equivalent).
     * Returns the absolute path of the saved file.
     */
    fun saveFileToDownloads(bytes: ByteArray, fileName: String): String
}

@Composable
expect fun rememberPlatformActions(): PlatformActions
