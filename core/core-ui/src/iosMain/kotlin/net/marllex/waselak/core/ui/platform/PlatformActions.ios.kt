package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
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
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    return remember { PlatformActions() }
}
