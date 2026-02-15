package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Desktop
import java.net.URI

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
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    return remember { PlatformActions() }
}
