package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIPrintFormatter
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoJobNameKey
import platform.UIKit.UIPrintInfoOutputType
import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIMarkupTextPrintFormatter

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
