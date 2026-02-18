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
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    return remember { PlatformActions() }
}
