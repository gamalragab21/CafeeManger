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

    /**
     * Download an app-update artifact (APK / DMG / MSI / DEB) to a
     * platform-appropriate location that the OS install flow can
     * consume.
     *
     * Android: writes to `getExternalFilesDir("updates")` so the
     *   FileProvider can serve it to the install intent without any
     *   storage permission. Survives app restarts.
     * Desktop: writes to `~/Downloads/` so the user can also pick the
     *   installer manually if the auto-open path fails.
     * iOS: no-op (App Store handles updates).
     *
     * Returns the absolute file path on success, null otherwise.
     * Progress callback fires with values in [0.0, 1.0].
     */
    suspend fun downloadAppUpdate(
        url: String,
        filename: String,
        onProgress: (Float) -> Unit,
    ): String?

    /**
     * Hand the just-downloaded artifact to the OS install/open flow.
     *
     * Android: fires `Intent.ACTION_VIEW` with the
     *   `application/vnd.android.package-archive` MIME and a
     *   FileProvider URI — the system "Install update?" dialog
     *   appears. User taps Install → APK replaces the running app.
     *   The user must have "Install unknown apps" allowed for this
     *   app once (Android 8+ requirement) — the system Settings UI
     *   opens automatically on first attempt if not granted.
     * Desktop: opens the DMG/MSI/DEB with the OS default handler
     *   (macOS `open`, Windows `start`, Linux `xdg-open`).
     * iOS: opens the App Store page for the app via the URL fallback.
     *
     * Returns true if the install flow was initiated successfully.
     */
    fun installAppUpdate(filePath: String): Boolean
}

@Composable
expect fun rememberPlatformActions(): PlatformActions
