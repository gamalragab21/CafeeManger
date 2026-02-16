package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.print.PageFormat
import java.awt.print.Printable
import java.awt.print.PrinterJob
import javax.swing.JEditorPane
import javax.swing.SwingUtilities

actual class ReceiptPrinter {
    actual fun printHtml(htmlContent: String, jobName: String) {
        SwingUtilities.invokeLater {
            try {
                val editorPane = JEditorPane("text/html", htmlContent).apply {
                    // Set a reasonable size so the layout engine works properly
                    setSize(580, Int.MAX_VALUE / 2)
                    val prefSize = preferredSize
                    setSize(580, prefSize.height)
                }

                val printerJob = PrinterJob.getPrinterJob()
                printerJob.jobName = jobName

                printerJob.setPrintable(object : Printable {
                    override fun print(
                        graphics: Graphics,
                        pageFormat: PageFormat,
                        pageIndex: Int,
                    ): Int {
                        if (pageIndex > 0) {
                            // Check if there is more content to print
                            val totalHeight = editorPane.preferredSize.height.toDouble()
                            val pageHeight = pageFormat.imageableHeight
                            val maxPages = kotlin.math.ceil(totalHeight / pageHeight).toInt()
                            if (pageIndex >= maxPages) return Printable.NO_SUCH_PAGE
                        }

                        val g2d = graphics as Graphics2D
                        g2d.translate(
                            pageFormat.imageableX.toInt(),
                            pageFormat.imageableY.toInt()
                        )

                        // Translate for the current page
                        val pageHeight = pageFormat.imageableHeight
                        g2d.translate(0, -(pageIndex * pageHeight).toInt())

                        // Scale to fit the printable width
                        val scaleX = pageFormat.imageableWidth / editorPane.width.toDouble()
                        if (scaleX < 1.0) {
                            g2d.scale(scaleX, scaleX)
                        }

                        // Set clip to only show content for this page
                        g2d.setClip(
                            0,
                            (pageIndex * pageHeight / (if (scaleX < 1.0) scaleX else 1.0)).toInt(),
                            editorPane.width,
                            (pageHeight / (if (scaleX < 1.0) scaleX else 1.0)).toInt()
                        )

                        editorPane.print(g2d)
                        return Printable.PAGE_EXISTS
                    }
                })

                if (printerJob.printDialog()) {
                    printerJob.print()
                }
            } catch (e: Exception) {
                println("Print failed: ${e.message}")
            }
        }
    }
}

@Composable
actual fun rememberReceiptPrinter(): ReceiptPrinter {
    return remember { ReceiptPrinter() }
}
