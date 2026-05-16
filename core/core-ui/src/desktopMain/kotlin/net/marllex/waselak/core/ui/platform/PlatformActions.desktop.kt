package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.io.File
import java.net.URI
import javax.imageio.ImageIO
import javax.swing.JEditorPane
import javax.swing.SwingUtilities

actual class PlatformActions {
    actual fun openUrl(url: String) {
        try {
            Desktop.getDesktop().browse(URI(url))
        } catch (e: Exception) {
            println("Failed to open URL: $url")
        }
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
        println("Toast: $message")
    }

    actual fun copyToClipboard(text: String) {
        try {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(text), null)
        } catch (e: Exception) {
            println("Failed to copy to clipboard: ${e.message}")
        }
    }

    actual fun shareText(text: String, title: String) {
        // Desktop doesn't have a native share dialog, copy to clipboard instead
        copyToClipboard(text)
    }

    actual fun saveFileToDownloads(bytes: ByteArray, fileName: String): String {
        val downloadsDir = File(System.getProperty("user.home"), "Downloads")
        downloadsDir.mkdirs()
        val file = File(downloadsDir, fileName)
        file.writeBytes(bytes)
        // Open file in default app
        if (Desktop.isDesktopSupported()) {
            try { Desktop.getDesktop().open(file) } catch (_: Exception) {}
        }
        return file.absolutePath
    }

    actual fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) {
        try {
            val tmpDir = File(System.getProperty("java.io.tmpdir"), "waselak_logs")
            tmpDir.mkdirs()
            val file = File(tmpDir, fileName)
            file.writeBytes(bytes)
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file)
            }
        } catch (e: Exception) {
            println("Failed to share file: ${e.message}")
        }
    }

    actual fun shareHtmlAsImage(htmlContent: String, fileName: String) {
        SwingUtilities.invokeLater {
            try {
                val editorPane = JEditorPane("text/html", htmlContent)
                editorPane.setSize(580, Int.MAX_VALUE)
                editorPane.size = java.awt.Dimension(580, editorPane.preferredSize.height)

                val image = java.awt.image.BufferedImage(
                    editorPane.width,
                    editorPane.height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB
                )
                val g2d = image.createGraphics()
                g2d.setRenderingHint(
                    java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                )
                g2d.color = java.awt.Color.WHITE
                g2d.fillRect(0, 0, image.width, image.height)
                editorPane.paint(g2d)
                g2d.dispose()

                // Save to temp file and open with default viewer
                val tmpDir = File(System.getProperty("java.io.tmpdir"), "waselak_shared")
                tmpDir.mkdirs()
                val imageFile = File(tmpDir, "$fileName.png")
                ImageIO.write(image, "png", imageFile)

                // Open in default image viewer / file manager
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(imageFile)
                }
            } catch (e: Exception) {
                println("Share as image failed: ${e.message}")
            }
        }
    }

    // Desktop has no NFC stack — the cashier laptop is plugged in and
    // can't physically tap another phone. The "Share via NFC" button
    // is hidden when isNfcAvailable is false.
    actual val isNfcAvailable: Boolean = false
    actual fun shareUrlViaNfc(url: String): Boolean = false
    actual fun stopNfcShare() {}

    actual suspend fun downloadAppUpdate(
        url: String,
        filename: String,
        onProgress: (Float) -> Unit,
    ): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val home = System.getProperty("user.home") ?: return@withContext null
            val dir = java.io.File(home, "Downloads")
            if (!dir.exists() && !dir.mkdirs()) return@withContext null
            val outFile = java.io.File(dir, filename)
            val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 120_000
                instanceFollowRedirects = true
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L
            var read = 0L
            java.io.FileOutputStream(outFile).use { out ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                        read += n
                        if (total > 0) onProgress((read.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
            outFile.absolutePath
        } catch (e: Throwable) {
            println("[PlatformActions] downloadAppUpdate failed: ${e.message}")
            null
        }
    }

    actual fun installAppUpdate(filePath: String): Boolean = try {
        // Hand the installer to the OS-default handler. macOS opens
        // DMG in Finder (auto-mounts); Windows runs the MSI installer
        // chain; Linux DEB pops up the system package installer.
        val os = System.getProperty("os.name").lowercase()
        val command = when {
            os.contains("mac") -> arrayOf("open", filePath)
            os.contains("win") -> arrayOf("cmd", "/c", "start", "", filePath)
            else -> arrayOf("xdg-open", filePath)
        }
        Runtime.getRuntime().exec(command)
        true
    } catch (e: Throwable) {
        println("[PlatformActions] installAppUpdate failed: ${e.message}")
        false
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    return remember { PlatformActions() }
}
