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
        val printController = UIPrintInteractionController.sharedPrintController()

        val printInfo = UIPrintInfo.printInfo()
        printInfo.jobName = jobName
        printInfo.outputType = UIPrintInfoOutputType.UIPrintInfoOutputGeneral
        printController.printInfo = printInfo

        val formatter = UIMarkupTextPrintFormatter(markupText = htmlContent)
        formatter.perPageContentInsets = platform.UIKit.UIEdgeInsetsMake(
            top = 20.0,
            left = 20.0,
            bottom = 20.0,
            right = 20.0,
        )
        printController.printFormatter = formatter

        printController.presentAnimated(true, completionHandler = null)
    }
}

@Composable
actual fun rememberReceiptPrinter(): ReceiptPrinter {
    return remember { ReceiptPrinter() }
}
