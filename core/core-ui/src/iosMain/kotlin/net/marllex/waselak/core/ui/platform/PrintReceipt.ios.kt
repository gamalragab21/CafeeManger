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
    ) {
        present(buildReceiptHtml(order, vendor, language, qrCodeUrl), jobName)
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
}

@Composable
actual fun rememberReceiptPrinter(): ReceiptPrinter {
    return remember { ReceiptPrinter() }
}

/**
 * Reads the active iOS app locale. iOS's first preferred locale follows
 * the per-app language override that NSLocalizedString uses, so this
 * matches whatever the user sees in the UI.
 */
actual fun getReceiptLanguage(): String {
    val raw = (platform.Foundation.NSLocale.preferredLanguages.firstOrNull() as? String)
        ?.take(2)
        ?: "ar"
    return if (raw == "en") "en" else "ar"
}
