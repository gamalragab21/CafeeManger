package net.marllex.waselak.core.ui.platform

import android.content.Context
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

actual class ReceiptPrinter(private val context: Context) {
    actual fun printHtml(htmlContent: String, jobName: String) {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    ?: return

                val adapter = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    view?.createPrintDocumentAdapter(jobName) ?: return
                } else {
                    @Suppress("DEPRECATION")
                    view?.createPrintDocumentAdapter() ?: return
                }

                val attrs = PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(PrintAttributes.Resolution("default", "Default", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build()

                printManager.print(jobName, adapter, attrs)
            }
        }
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }
}

@Composable
actual fun rememberReceiptPrinter(): ReceiptPrinter {
    val context = LocalContext.current
    return remember(context) { ReceiptPrinter(context) }
}
