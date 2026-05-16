package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImagePNGRepresentation
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.darwin.NSObject

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
        val viewController = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val activityVC = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null
        )
        viewController.presentViewController(activityVC, animated = true, completion = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun saveFileToDownloads(bytes: ByteArray, fileName: String): String {
        val documentsDir = platform.Foundation.NSSearchPathForDirectoriesInDomains(
            platform.Foundation.NSDocumentDirectory,
            platform.Foundation.NSUserDomainMask,
            true
        ).firstOrNull() as? String ?: NSTemporaryDirectory()
        val path = "$documentsDir/$fileName"
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        data.writeToFile(path, atomically = true)
        return path
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) {
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return
        val path = NSTemporaryDirectory() + fileName
        val data = bytes.usePinned { pinned ->
            NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
        data.writeToFile(path, atomically = true)
        val fileUrl = NSURL.fileURLWithPath(path)
        val activityVC = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null
        )
        rootVC.presentViewController(activityVC, animated = true, completion = null)
    }

    @OptIn(ExperimentalForeignApi::class)
    actual fun shareHtmlAsImage(htmlContent: String, fileName: String) {
        val rootVC = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return

        val webView = WKWebView(frame = CGRectMake(0.0, 0.0, 580.0, 1.0))
        webView.navigationDelegate = object : NSObject(), WKNavigationDelegateProtocol {
            override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
                webView.evaluateJavaScript("document.body.scrollHeight") { result, _ ->
                    val height = (result as? Number)?.toDouble() ?: 800.0
                    webView.setFrame(CGRectMake(0.0, 0.0, 580.0, height))

                    // Give it a moment to re-layout then capture
                    platform.Foundation.NSTimer.scheduledTimerWithTimeInterval(
                        0.5,
                        repeats = false
                    ) { _ ->
                        try {
                            UIGraphicsBeginImageContextWithOptions(
                                CGSizeMake(580.0, height), true, 2.0
                            )
                            val context = platform.UIKit.UIGraphicsGetCurrentContext()
                            if (context != null) {
                                webView.layer.renderInContext(context)
                            }
                            val image = UIGraphicsGetImageFromCurrentImageContext()
                            UIGraphicsEndImageContext()

                            if (image != null) {
                                val pngData = UIImagePNGRepresentation(image)
                                if (pngData != null) {
                                    val path = NSTemporaryDirectory() + "$fileName.png"
                                    pngData.writeToFile(path, atomically = true)
                                    val fileUrl = NSURL.fileURLWithPath(path)
                                    val activityVC = UIActivityViewController(
                                        activityItems = listOf(fileUrl),
                                        applicationActivities = null
                                    )
                                    rootVC.presentViewController(
                                        activityVC,
                                        animated = true,
                                        completion = null
                                    )
                                }
                            }
                        } catch (e: Exception) {
                            println("Share as image failed: ${e.message}")
                        }
                    }
                }
            }
        }
        webView.loadHTMLString(htmlContent, baseURL = null)
    }

    // iOS doesn't permit HCE for non-payment use cases, so the
    // "Share via NFC" flow is Android-only. These stubs let the
    // common code call the same API without #ifdef noise.
    actual val isNfcAvailable: Boolean = false
    actual fun shareUrlViaNfc(url: String): Boolean = false
    actual fun stopNfcShare() {}

    // iOS has no in-app update path — App Store handles everything.
    // The banner UI calls these no-ops, then falls back to opening
    // the App Store URL via `openUrl` (which the caller already does).
    actual suspend fun downloadAppUpdate(
        url: String,
        filename: String,
        onProgress: (Float) -> Unit,
    ): String? = null

    actual fun installAppUpdate(filePath: String): Boolean = false
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    return remember { PlatformActions() }
}
