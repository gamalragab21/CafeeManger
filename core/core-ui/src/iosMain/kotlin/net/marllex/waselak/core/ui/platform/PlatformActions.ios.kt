package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual class PlatformActions {
    actual fun openUrl(url: String) {
        val nsUrl = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(nsUrl)
    }

    actual fun openMap(address: String?, lat: Double?, lng: Double?) {
        val urlString = when {
            !address.isNullOrBlank() -> "http://maps.apple.com/?q=$address"
            lat != null && lng != null -> "http://maps.apple.com/?ll=$lat,$lng"
            else -> return
        }
        val nsUrl = NSURL.URLWithString(urlString) ?: return
        UIApplication.sharedApplication.openURL(nsUrl)
    }

    actual fun showToast(message: String) {
        // iOS doesn't have native toast - placeholder
        println("Toast: $message")
    }

    actual fun copyToClipboard(text: String) {
        platform.UIKit.UIPasteboard.generalPasteboard.string = text
    }

    actual fun shareText(text: String, title: String) {
        // iOS share requires UIViewController context, just copy for now
        copyToClipboard(text)
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    return remember { PlatformActions() }
}
