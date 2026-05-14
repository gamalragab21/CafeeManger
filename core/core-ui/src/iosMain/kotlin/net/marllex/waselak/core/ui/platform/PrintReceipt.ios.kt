package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
// UIPrintInfoJobNameKey was renamed in newer Kotlin/Native UIKit bindings —
// it's not exported anymore. We don't need it; printInfo.jobName setter
// already does the right thing. Drop the unused import.
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIMarkupTextPrintFormatter

// UIEdgeInsetsMake is a C struct constructor exposed via cinterop —
// requires the foreign-API opt-in. Annotating the whole class so any
// future cinterop call sites here don't need their own opt-in.
@OptIn(ExperimentalForeignApi::class)
actual class ReceiptPrinter {
    actual fun printHtml(htmlContent: String, jobName: String) {
        present(htmlContent, jobName)
    }

    actual fun printOrder(
        order: net.marllex.waselak.core.model.Order,
        vendor: net.marllex.waselak.core.model.Vendor?,
        language: String,
        jobName: String,
        qrCodeUrl: String?,
        copies: Int,
    ) {
        // iOS UIPrintInteractionController doesn't expose a programmatic
        // copies setter — the system print panel lets the USER pick the
        // copy count. To honor `copies` we present the print panel N
        // times in sequence; the user just taps "Print" each time.
        // Clamped 1..10 to match Android.
        val n = copies.coerceIn(1, 10)
        repeat(n) {
            present(buildReceiptHtml(order, vendor, language, qrCodeUrl), jobName)
        }
    }

    private fun present(html: String, jobName: String) {
        val printController = UIPrintInteractionController.sharedPrintController()

        val printInfo = UIPrintInfo.printInfo()
        printInfo.jobName = jobName
        printInfo.outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
        printController.printInfo = printInfo

        val formatter = UIMarkupTextPrintFormatter(markupText = html)
        // Zero insets — let the receipt CSS @page rules + last-child trim
        // determine the actual content area. Merchants complained that any
        // padding shows as a blank strip at the bottom on 80mm thermal paper.
        formatter.perPageContentInsets = platform.UIKit.UIEdgeInsetsMake(
            top = 0.0,
            left = 0.0,
            bottom = 0.0,
            right = 0.0,
        )
        printController.printFormatter = formatter

        printController.presentAnimated(true, completionHandler = null)
    }

    // iOS uses UIPrintInteractionController, which presents its own
    // system printer-picker UI. We always return true so the Compose
    // layer skips the Android-only BT/USB picker.
    actual fun hasPrinterConfigured(): Boolean = true

    actual fun clearPrinterConfiguration() { /* no-op on iOS */ }
}

@Composable
actual fun rememberReceiptPrinter(): ReceiptPrinter {
    return remember { ReceiptPrinter() }
}

@Composable
actual fun PrinterSelectionDialogIfNeeded(
    show: Boolean,
    onSelected: () -> Unit,
    onDismiss: () -> Unit,
) {
    // No-op on iOS; UIPrintInteractionController handles printer selection.
}

/**
 * Reads the active iOS app locale. NSBundle's preferredLocalizations
 * matches whatever language the OS picked for the running app — same
 * value NSLocalizedString uses, so it tracks the per-app language
 * override the user can set in Settings → App → Language.
 *
 * History: first tried `NSLocale.preferredLanguages` then
 * `NSLocale.currentLocale.languageCode` — both unresolved on K/N
 * Foundation bindings (the class-level static is not exposed as a
 * Kotlin-side property). NSBundle.mainBundle.preferredLocalizations is
 * an instance property and resolves cleanly. Falls back to "ar" if the
 * list is empty (it never is in practice; iOS always provides at least
 * the development localization).
 */
actual fun getReceiptLanguage(): String {
    val list = platform.Foundation.NSBundle.mainBundle.preferredLocalizations
    val first = list.firstOrNull() as? String ?: return "ar"
    return if (first.take(2) == "en") "en" else "ar"
}
