package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLAnchorElement

actual class PlatformActions {
    actual fun openUrl(url: String) {
        window.open(url, "_blank")
    }

    actual fun openMap(address: String?, lat: Double?, lng: Double?) {
        val url = when {
            !address.isNullOrBlank() -> "https://www.google.com/maps/search/${address.replace(" ", "+")}"
            lat != null && lng != null -> "https://www.google.com/maps/@$lat,$lng,15z"
            else -> return
        }
        openUrl(url)
    }

    actual fun showToast(message: String) {
        window.alert(message)
    }

    actual fun copyToClipboard(text: String) {
        window.navigator.clipboard.writeText(text)
    }

    actual fun shareText(text: String, title: String) {
        copyToClipboard(text)
    }

    actual fun shareHtmlAsImage(htmlContent: String, fileName: String) {
        val printWindow = window.open("", "_blank")
        printWindow?.document?.write(htmlContent)
        printWindow?.document?.close()
    }

    actual fun saveFileToDownloads(bytes: ByteArray, fileName: String): String {
        // Use data URI approach for wasmJs compatibility
        val base64 = bytesToBase64(bytes)
        val a = document.createElement("a") as HTMLAnchorElement
        a.href = "data:application/octet-stream;base64,$base64"
        a.download = fileName
        document.body?.appendChild(a)
        a.click()
        document.body?.removeChild(a)
        return fileName
    }

    actual fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) {
        saveFileToDownloads(bytes, fileName)
    }

    private fun bytesToBase64(bytes: ByteArray): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            sb.append(chars[(b0 shr 2) and 0x3F])
            sb.append(chars[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            sb.append(if (i + 1 < bytes.size) chars[((b1 shl 2) or (b2 shr 6)) and 0x3F] else '=')
            sb.append(if (i + 2 < bytes.size) chars[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    return remember { PlatformActions() }
}
