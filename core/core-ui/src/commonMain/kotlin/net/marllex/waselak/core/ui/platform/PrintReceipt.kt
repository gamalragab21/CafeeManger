package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable

expect class ReceiptPrinter {
    fun printHtml(htmlContent: String, jobName: String)
}

@Composable
expect fun rememberReceiptPrinter(): ReceiptPrinter
