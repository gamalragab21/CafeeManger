package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

actual class ReceiptPrinter {
    actual fun printHtml(htmlContent: String, jobName: String) {
        // Open a new window with the HTML and trigger print
        val printWindow = window.open("", "_blank")
        printWindow?.document?.write(htmlContent)
        printWindow?.document?.close()
        printWindow?.print()
    }
}

@Composable
actual fun rememberReceiptPrinter(): ReceiptPrinter {
    return remember { ReceiptPrinter() }
}
