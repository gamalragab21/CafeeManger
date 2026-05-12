package net.marllex.waselak.core.ui.platform

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.Vendor

/**
 * Android receipt printer.
 *
 * Two key tricks to deliver "no bottom whitespace" on Android:
 *
 *   1. Force the WebView to lay out at exactly 80mm (≈302 CSS px) wide
 *      BEFORE measuring content height, so the post-layout height we
 *      read matches what the print system will actually render.
 *
 *   2. Use a JS bridge (`window.__waselak_receipt_height()`) to read the
 *      precise `document.body.scrollHeight` after rendering. WebView's
 *      Java-side `getContentHeight()` is rounded and frequently off by
 *      tens of pixels — enough to leave or trim the closing greeting.
 *
 * The HTML itself centers every line; this file's job is only to make
 * the paper size match the rendered content.
 */
actual class ReceiptPrinter(private val context: Context) {

    actual fun printHtml(htmlContent: String, jobName: String) {
        renderAndPrint(htmlContent, jobName)
    }

    actual fun printOrder(
        order: Order,
        vendor: Vendor?,
        language: String,
        jobName: String,
        qrCodeUrl: String?,
    ) {
        renderAndPrint(buildReceiptHtml(order, vendor, language, qrCodeUrl), jobName)
    }

    private fun renderAndPrint(htmlContent: String, jobName: String) {
        // 80mm at 160dpi-baseline CSS px ≈ 302; round up so we don't
        // over-clip on devices with sub-pixel rounding.
        val widthCssPx = 304
        val displayDensity = context.resources.displayMetrics.density
        val widthDevicePx = (widthCssPx * displayDensity).roundToInt().coerceAtLeast(200)

        // Tall measurement view so the layout has room to render fully.
        val measureHeightDevicePx = 20_000

        val webView = WebView(context).apply {
            // JS is on so we can evaluate the height function the HTML
            // exposes. We don't load any remote scripts — safe.
            settings.javaScriptEnabled = true
            settings.loadWithOverviewMode = false
            settings.useWideViewPort = false
            settings.builtInZoomControls = false
            // Force measure + layout so getContentHeight() works correctly.
            measure(
                View.MeasureSpec.makeMeasureSpec(widthDevicePx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(measureHeightDevicePx, View.MeasureSpec.AT_MOST),
            )
            layout(0, 0, widthDevicePx, measureHeightDevicePx)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                val v = view ?: return
                // Wait long enough for both Skia layout to settle AND any
                // network-fetched images (logo, QR code from qrserver.com)
                // to download and lay out. 1200ms is the sweet spot from
                // empirical testing on Tab A7 / hotspot networks.
                Handler(Looper.getMainLooper()).postDelayed({
                    measureHeightAndPrint(v, jobName)
                }, 1200L)
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }

    private fun measureHeightAndPrint(v: WebView, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return
        v.evaluateJavascript("window.__waselak_receipt_height();") { result ->
            // result is the JSON-encoded return value (e.g. "1234").
            val jsHeightCssPx = result?.trim()?.toIntOrNull()
            val fallbackContentHeight = v.contentHeight
            val contentHeightCssPx = (jsHeightCssPx ?: fallbackContentHeight).coerceAtLeast(50)

            // 1 CSS px = 1/160 inch. mils = 1/1000 inch.
            // mils = cssPx × 1000 / 160 = cssPx × 6.25
            val contentMils = (contentHeightCssPx * 6.25).roundToInt().coerceAtLeast(2000)

            val thermal80mm = PrintAttributes.MediaSize(
                "WASELAK_THERMAL_80MM_AUTO",
                "Receipt 80mm (auto height)",
                /* widthMils  = */ 3149,
                /* heightMils = */ contentMils,
            )
            val attrs = PrintAttributes.Builder()
                .setMediaSize(thermal80mm)
                .setResolution(
                    PrintAttributes.Resolution("default", "Default", 300, 300),
                )
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build()

            val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                v.createPrintDocumentAdapter(jobName)
            } else {
                @Suppress("DEPRECATION")
                v.createPrintDocumentAdapter()
            }
            printManager.print(jobName, adapter, attrs)
        }
    }
}

@Composable
actual fun rememberReceiptPrinter(): ReceiptPrinter {
    val context = LocalContext.current
    return remember(context) { ReceiptPrinter(context) }
}

actual fun getReceiptLanguage(): String {
    val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val raw = if (locales.isEmpty) {
        java.util.Locale.getDefault().language
    } else {
        locales[0]?.language ?: "ar"
    }
    return if (raw == "en") "en" else "ar"
}
