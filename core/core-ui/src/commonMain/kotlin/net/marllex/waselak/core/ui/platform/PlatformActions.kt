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

    /**
     * Share/open a file via the platform share sheet or file explorer.
     */
    fun shareFile(bytes: ByteArray, fileName: String, mimeType: String)

    /**
     * True when this device has NFC hardware AND it's currently enabled
     * in system settings AND the platform supports HCE for tap-to-share.
     *
     * Android: real check against [android.nfc.NfcAdapter.isEnabled].
     * iOS / Desktop: always false — iOS doesn't permit HCE for non-
     * payment use cases, and desktop has no NFC stack.
     *
     * The receipt screen calls this every recomposition (cheap — single
     * service lookup on Android) to decide whether to show the
     * "Share via NFC" button.
     */
    val isNfcAvailable: Boolean

    /**
     * Arm the NFC HCE service to emit [url] as an NDEF URI record on the
     * next reader tap. Returns true if armed successfully, false if NFC
     * is unavailable or the URL is blank.
     *
     * The caller should also show a "tap to share" dialog so the
     * cashier knows what to do; this function only sets up the data —
     * it does NOT block waiting for the tap.
     *
     * Call [stopNfcShare] when the dialog is dismissed to clear the
     * stale URL so a later accidental tap can't leak the previous
     * receipt.
     */
    fun shareUrlViaNfc(url: String): Boolean

    /** Clear the URL armed by [shareUrlViaNfc]. Safe to call on any platform. */
    fun stopNfcShare()
}

@Composable
expect fun rememberPlatformActions(): PlatformActions
