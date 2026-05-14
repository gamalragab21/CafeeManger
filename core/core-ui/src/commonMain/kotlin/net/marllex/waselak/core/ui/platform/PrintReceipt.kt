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
     * @param copies    Number of physical paper copies to print. Default 1.
     *                  Implementations issue one PrintManager job per copy
     *                  so the user gets `copies` separate paper outputs.
     *                  Clamped to [1..10] by the actual implementations.
     */
    fun printOrder(
        order: Order,
        vendor: Vendor?,
        language: String,
        jobName: String,
        qrCodeUrl: String?,
        copies: Int = 1,
    )

    /**
     * Android only: returns false when the cashier hasn't picked a
     * Bluetooth/USB thermal printer yet. The receipt-screen UI should
     * show [PrinterSelectionDialogIfNeeded] before calling [printOrder]
     * in that case.
     *
     * Other platforms (desktop, iOS) return true — they delegate to the
     * OS print dialog which handles printer selection itself.
     */
    fun hasPrinterConfigured(): Boolean

    /**
     * Android only: forget the saved printer choice so the next call to
     * [hasPrinterConfigured] returns false and the picker dialog is
     * re-shown. Used by the "Change printer" entry / long-press.
     * No-op on other platforms.
     */
    fun clearPrinterConfiguration()
}

/**
 * Compose dialog wrapper that the receipt screen calls regardless of
 * platform. On Android it shows a Bluetooth/USB printer picker. On
 * other platforms it's a no-op (the OS handles printer selection).
 *
 * @param show     When true, render the dialog. The caller flips this
 *                 to true on first Print tap when hasPrinterConfigured
 *                 returns false.
 * @param onSelected Fired when the cashier picks a printer + taps "Use".
 *                   The caller should both hide the dialog and trigger
 *                   the print job from this callback.
 * @param onDismiss Fired when the cashier cancels.
 */
@Composable
expect fun PrinterSelectionDialogIfNeeded(
    show: Boolean,
    onSelected: () -> Unit,
    onDismiss: () -> Unit,
)

@Composable
expect fun rememberReceiptPrinter(): ReceiptPrinter

/**
 * The language the receipt should be printed in. Returns "ar" or "en".
 */
expect fun getReceiptLanguage(): String
