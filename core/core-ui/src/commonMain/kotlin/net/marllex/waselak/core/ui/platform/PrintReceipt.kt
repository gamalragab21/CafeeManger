package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.Vendor

expect class ReceiptPrinter {
    /**
     * Legacy: print an arbitrary HTML payload. Used for the share-as-image
     * flow (and any future feature that needs HTML rendering). On desktop
     * this path goes through JEditorPane which has limited CSS support —
     * for actual receipt printing prefer [printOrder].
     */
    fun printHtml(htmlContent: String, jobName: String)

    /**
     * Preferred path for printing a real receipt.
     *
     * @param qrCodeUrl When non-null, a QR-encodable URL/string is rendered
     *                  into the receipt so the customer can scan it for the
     *                  digital receipt. Use the share-link URL from the
     *                  digital-receipt feature; pass null to skip the QR.
     */
    fun printOrder(
        order: Order,
        vendor: Vendor?,
        language: String,
        jobName: String,
        qrCodeUrl: String?,
    )
}

@Composable
expect fun rememberReceiptPrinter(): ReceiptPrinter

/**
 * The language the receipt should be printed in. Returns "ar" or "en".
 */
expect fun getReceiptLanguage(): String
