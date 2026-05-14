package net.marllex.waselak.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.awt.print.PageFormat
import java.awt.print.Paper
import java.awt.print.Printable
import java.awt.print.PrinterJob
import javax.swing.JEditorPane
import javax.swing.SwingUtilities
import javax.swing.text.html.HTMLEditorKit
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.Vendor

/**
 * Desktop receipt printer.
 *
 * **Why direct Graphics2D drawing (and not HTML)**: previous iterations
 * tried to fix bottom-whitespace and centering via CSS, but Java's
 * `HTMLEditorKit` ignores most of the modern CSS we need — `@page`,
 * `@media print`, flexbox, `dir=rtl` overrides on alignment, even
 * `text-align: center` on the body. The fix that survived merchant
 * acceptance is to skip HTML entirely on desktop:
 *
 *   1. Build the receipt from the `ReceiptModel` (see ReceiptModel.kt).
 *   2. "Dry run" the same painter against a tiny off-screen BufferedImage
 *      to measure the exact pixel height the receipt will occupy.
 *   3. Set the `Paper` size to (80 mm, measured height) — paper height
 *      now matches content height to the pixel, eliminating the trailing
 *      blank strip.
 *   4. Paint everything *centered* with explicit `g.drawString(...)`
 *      positioning — no more relying on CSS rules JEditorPane never
 *      honored.
 *
 * `printHtml` is preserved for the share-as-image flow which still goes
 * through HTML; only the actual receipt print uses the new path.
 */
actual class ReceiptPrinter {

    /**
     * Legacy HTML print path. Kept for share-as-image. The receipt-print
     * flow uses [printOrder] instead.
     */
    actual fun printHtml(htmlContent: String, jobName: String) {
        SwingUtilities.invokeLater {
            try {
                val pane = JEditorPane()
                pane.editorKit = HTMLEditorKit()
                pane.text = htmlContent
                pane.isEditable = false
                pane.setSize(302, Int.MAX_VALUE / 2)
                pane.setSize(302, pane.preferredSize.height)

                val job = PrinterJob.getPrinterJob()
                job.jobName = jobName
                job.setPrintable(object : Printable {
                    override fun print(g: Graphics, pf: PageFormat, idx: Int): Int {
                        if (idx > 0) return Printable.NO_SUCH_PAGE
                        val g2d = g as Graphics2D
                        g2d.translate(pf.imageableX.toInt(), pf.imageableY.toInt())
                        pane.print(g2d)
                        return Printable.PAGE_EXISTS
                    }
                })
                if (job.printDialog()) job.print()
            } catch (e: Exception) {
                println("[ReceiptPrinter] HTML print failed: ${e.message}")
            }
        }
    }

    actual fun printOrder(
        order: Order,
        vendor: Vendor?,
        language: String,
        jobName: String,
        qrCodeUrl: String?,
        copies: Int,
    ) {
        // Desktop: java.awt.print supports the OS-native copies count
        // directly on the PrinterJob.copies field set below. No looping
        // needed (the OS handles it via the printer driver).
        SwingUtilities.invokeLater {
            try {
                val model = buildReceiptModel(order, vendor, language)
                // QR code intentionally not drawn (merchant request).
                // `qrCodeUrl` parameter is ignored; kept on the public
                // ReceiptPrinter API for source-compatibility.
                @Suppress("UNUSED_VARIABLE") val ignoredQr = qrCodeUrl
                val painter = ReceiptPainter(model)

                // Java AWT works in points (1 pt = 1/72 inch). 80 mm thermal
                // width = 80 × 72/25.4 ≈ 226.77 pt. We use 226 to leave a
                // sliver of slack so the right column of items never gets
                // clipped on printers that under-report imageable width.
                val mm = 72.0 / 25.4
                val paperWidthPt = 80.0 * mm

                // Dry-run measurement: draw the receipt onto a throwaway
                // BufferedImage Graphics2D so we know exactly how tall the
                // rendered receipt is. Then use that as the paper height.
                val measureImg = BufferedImage(
                    paperWidthPt.toInt().coerceAtLeast(200),
                    8000,
                    BufferedImage.TYPE_INT_ARGB,
                )
                val measureG = measureImg.createGraphics()
                val contentHeightPt = painter.paint(measureG, paperWidthPt)
                measureG.dispose()

                // Paper.setSize takes width × height in points. Heightis
                // EXACTLY the content height plus a 2-pt safety pad — this
                // is what makes the bottom blank strip vanish.
                val paperHeightPt = contentHeightPt + 2.0
                val paper = Paper().apply {
                    setSize(paperWidthPt, paperHeightPt)
                    setImageableArea(0.0, 0.0, paperWidthPt, paperHeightPt)
                }
                val pageFormat = PageFormat().apply { this.paper = paper }

                val job = PrinterJob.getPrinterJob()
                job.jobName = jobName
                // OS-native copies setting; clamped 1..10 to match Android.
                job.copies = copies.coerceIn(1, 10)
                job.setPrintable(object : Printable {
                    override fun print(g: Graphics, pf: PageFormat, idx: Int): Int {
                        if (idx > 0) return Printable.NO_SUCH_PAGE
                        val g2d = g as Graphics2D
                        g2d.setRenderingHint(
                            RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                        )
                        g2d.setRenderingHint(
                            RenderingHints.KEY_RENDERING,
                            RenderingHints.VALUE_RENDER_QUALITY,
                        )
                        g2d.translate(pf.imageableX, pf.imageableY)
                        painter.paint(g2d, pf.imageableWidth)
                        return Printable.PAGE_EXISTS
                    }
                }, job.validatePage(pageFormat))

                if (job.printDialog()) job.print()
            } catch (e: Exception) {
                println("[ReceiptPrinter] printOrder failed: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    actual fun hasPrinterConfigured(): Boolean = true

    actual fun clearPrinterConfiguration() { /* no-op: OS print dialog handles selection */ }
}

// Desktop doesn't have a Bluetooth/USB ESC/POS picker — it uses the OS
// print dialog instead, which handles printer selection on its own.
// `hasPrinterConfigured` always returns true so the receipt screen
// skips the picker and goes straight to PrinterJob.printDialog().
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
    // No-op on desktop; the OS print dialog handles printer selection.
}

actual fun getReceiptLanguage(): String {
    val node = java.util.prefs.Preferences
        .userRoot()
        .node("/net/marllex/waselak/core/ui/components")
    val raw = node.get("waselak_language", java.util.Locale.getDefault().language)
    return if (raw == "en") "en" else "ar"
}

// ─────────────────────────────────────────────────────────────────────────
// Direct Graphics2D receipt painter
// ─────────────────────────────────────────────────────────────────────────

/**
 * Paints a [ReceiptModel] onto any Graphics2D, returning the y-coordinate
 * at which the receipt content ends. Used in two modes:
 *
 *   - Dry run against a `BufferedImage` to measure total height before
 *     deciding the paper size.
 *   - Real print run during `Printable.print(...)`.
 *
 * The painter exclusively centers content horizontally. Each "line" is
 * computed against the page width passed in, so the same instance
 * renders correctly whether the dry-run width and real-print width
 * differ slightly.
 */
private class ReceiptPainter(
    private val model: ReceiptModel,
) {

    // Font sizes bumped per merchant request for better readability,
    // with extra vertical spacing in the row painters below. The order
    // ID fonts remain the LARGEST on the receipt.
    // Font sizes trimmed ~15-20% vs the previous version (merchant
    // feedback: "a little smaller please"). Order-ID is still the
    // biggest text on the page so the cashier can still call it out
    // at a glance, just less dominant relative to the rest.
    private val fontFamily = "Tahoma"
    private val fontHeader = Font(fontFamily, Font.BOLD, 17)
    private val fontVendorMeta = Font(fontFamily, Font.PLAIN, 10)
    // Biggest text on the whole receipt — order ID label/value.
    private val fontOrderIdLabel = Font(fontFamily, Font.BOLD, 18)
    private val fontOrderIdValue = Font(fontFamily, Font.BOLD, 22)
    private val fontLabel = Font(fontFamily, Font.BOLD, 12)
    private val fontValue = Font(fontFamily, Font.BOLD, 12)
    private val fontItemName = Font(fontFamily, Font.PLAIN, 11)
    private val fontItemBold = Font(fontFamily, Font.BOLD, 11)
    private val fontTotalLabel = Font(fontFamily, Font.BOLD, 12)
    private val fontTotalValue = Font(fontFamily, Font.BOLD, 18)
    private val fontFooter = Font(fontFamily, Font.BOLD, 12)
    private val fontNotes = Font(fontFamily, Font.PLAIN, 10)

    /**
     * Paint the receipt onto [g] using [widthPt] as the column width.
     * Returns the y-coordinate of the bottom of the last line of content.
     */
    fun paint(g: Graphics2D, widthPt: Double): Double {
        g.color = Color.BLACK
        // Tighter top margin so the receipt fits on one continuous page
        // (was over-flowing to 2 pages before). Still leaves a small gap
        // so the vendor name isn't glued to the paper edge.
        var y = 6.0

        // ── Header ──────────────────────────────────────────────
        y = drawCenteredText(g, widthPt, y, model.vendorName, fontHeader)
        model.vendorAddress?.let { y = drawCenteredText(g, widthPt, y, it, fontVendorMeta) }
        model.vendorPhone?.let { y = drawCenteredText(g, widthPt, y, "☎ $it", fontVendorMeta) }
        model.vendorWallet?.let {
            val label = if (model.isArabic) "محفظة" else "Wallet"
            y = drawCenteredText(g, widthPt, y, "$label: $it", fontVendorMeta)
        }
        model.vendorWhatsapp?.let {
            val label = if (model.isArabic) "واتساب" else "WhatsApp"
            y = drawCenteredText(g, widthPt, y, "$label: $it", fontVendorMeta)
        }

        y += 4
        y = drawHorizontalLine(g, widthPt, y, double = true)
        y += 4

        // ── Prominent Order ID box ──────────────────────────────
        // Matches the HTML's `.order-id` box — boxed row with much larger
        // value font than the regular kv rows below it. The label sits on
        // the start side, the value on the end side; in Arabic (RTL)
        // those swap automatically via the `model.isArabic` branch.
        y = drawOrderIdBox(g, widthPt, y, model.orderIdLabel, model.orderIdValue)
        y += 4

        // ── Order info rows ─────────────────────────────────────
        for ((label, value) in model.orderRows) {
            y = drawLabelValueRow(g, widthPt, y, label, value)
        }

        if (model.clientRows.isNotEmpty()) {
            y += 2
            y = drawHorizontalLine(g, widthPt, y)
            y += 2
            for ((label, value) in model.clientRows) {
                y = drawLabelValueRow(g, widthPt, y, label, value)
            }
        }

        y += 2
        y = drawHorizontalLine(g, widthPt, y)
        y += 2

        // ── Items ───────────────────────────────────────────────
        val (itemLabel, qtyLabel, priceLabel) = if (model.isArabic) {
            Triple("الصنف", "الكمية", "السعر")
        } else {
            Triple("Item", "Qty", "Price")
        }
        y = drawItemsTableHeader(g, widthPt, y, itemLabel, qtyLabel, priceLabel)
        for (item in model.items) {
            y = drawItemRow(g, widthPt, y, item)
        }

        y += 2
        y = drawHorizontalLine(g, widthPt, y)
        y += 2

        // ── Totals ──────────────────────────────────────────────
        for ((label, value) in model.totalsRows) {
            y = drawLabelValueRow(g, widthPt, y, label, value)
        }

        y += 4
        y = drawGrandTotalBox(g, widthPt, y, model.grandTotalLabel, model.grandTotalValue)

        // ── Refunds ─────────────────────────────────────────────
        if (model.refundRows.isNotEmpty()) {
            y += 4
            for ((label, value) in model.refundRows) {
                y = drawLabelValueRow(g, widthPt, y, label, value)
            }
            if (model.netTotalLabel != null && model.netTotalValue != null) {
                y += 2
                y = drawGrandTotalBox(g, widthPt, y, model.netTotalLabel, model.netTotalValue, dashed = true)
            }
        }

        // ── Notes ───────────────────────────────────────────────
        if (model.notesLabel != null && model.notesText != null) {
            y += 4
            y = drawNotesBox(g, widthPt, y, model.notesLabel, model.notesText)
        }

        // (QR code / share-URL section removed — merchant asked to drop
        // the QR from the printed receipt. `qrCodeUrl` is kept on the
        // ReceiptPainter constructor for source-compatibility but is now
        // ignored on every platform.)

        // ── Footer ──────────────────────────────────────────────
        y += 6
        y = drawHorizontalLine(g, widthPt, y)
        y += 4
        y = drawCenteredText(g, widthPt, y, model.footerThanks, fontFooter)
        // Wide gap so the branding sits at the very bottom of the cut.
        y += 32
        y = drawCenteredText(g, widthPt, y, model.poweredBy, fontFooter.deriveFont(8f))
        y += 2  // tiny bottom buffer so the cut doesn't clip descenders

        return y
    }

    /** Draw [text] horizontally centered at [centerWidthPt] starting at [y0]. */
    private fun drawCenteredText(
        g: Graphics2D,
        widthPt: Double,
        y0: Double,
        text: String,
        font: Font,
    ): Double {
        g.font = font
        val fm = g.fontMetrics
        val maxWidth = widthPt - 8.0
        val lines = wrapText(text, fm, maxWidth)
        var y = y0
        for (line in lines) {
            val w = fm.stringWidth(line)
            val x = (widthPt - w) / 2.0
            g.drawString(line, x.toFloat(), (y + fm.ascent).toFloat())
            y += fm.height.toDouble()
        }
        return y
    }

    /**
     * Prominent order-ID row. Label and value use the LARGEST fonts on
     * the receipt — no surrounding box per merchant request. Direction
     * (start/end of the row) flips automatically per `model.isArabic`.
     */
    private fun drawOrderIdBox(
        g: Graphics2D,
        widthPt: Double,
        y0: Double,
        label: String,
        value: String,
    ): Double {
        g.font = fontOrderIdLabel
        val labelFm = g.fontMetrics
        g.font = fontOrderIdValue
        val valueFm = g.fontMetrics
        val rowHeight = maxOf(labelFm.height, valueFm.height) + 4.0
        val padding = 6.0
        val labelW = labelFm.stringWidth(label)
        val valueW = valueFm.stringWidth(value)
        val baseline = y0 + valueFm.ascent

        if (model.isArabic) {
            g.font = fontOrderIdLabel
            g.drawString(label, (widthPt - padding - labelW).toFloat(), baseline.toFloat())
            g.font = fontOrderIdValue
            g.drawString(value, padding.toFloat(), baseline.toFloat())
        } else {
            g.font = fontOrderIdLabel
            g.drawString(label, padding.toFloat(), baseline.toFloat())
            g.font = fontOrderIdValue
            g.drawString(value, (widthPt - padding - valueW).toFloat(), baseline.toFloat())
        }
        return y0 + rowHeight + 1
    }

    /**
     * Label/value row, key on one edge and value on the other — RTL flips
     * automatically via `model.isArabic`.
     */
    private fun drawLabelValueRow(
        g: Graphics2D,
        widthPt: Double,
        y0: Double,
        label: String,
        value: String,
    ): Double {
        g.font = fontLabel
        val labelFm = g.fontMetrics
        val labelW = labelFm.stringWidth(label)
        g.font = fontValue
        val valueFm = g.fontMetrics
        val valueW = valueFm.stringWidth(value)

        val rowHeight = maxOf(labelFm.height, valueFm.height)
        val baseline = y0 + maxOf(labelFm.ascent, valueFm.ascent)
        val padding = 4.0

        if (model.isArabic) {
            // RTL: label on the RIGHT, value on the LEFT.
            g.font = fontLabel
            g.drawString(label, (widthPt - padding - labelW).toFloat(), baseline.toFloat())
            g.font = fontValue
            g.drawString(value, padding.toFloat(), baseline.toFloat())
        } else {
            // LTR: label on the LEFT, value on the RIGHT.
            g.font = fontLabel
            g.drawString(label, padding.toFloat(), baseline.toFloat())
            g.font = fontValue
            g.drawString(value, (widthPt - padding - valueW).toFloat(), baseline.toFloat())
        }
        // +5 extra vertical pad — gives clear breathing space between
        // consecutive label/value rows so the receipt doesn't feel
        // crammed even with the larger fonts.
        return y0 + rowHeight + 5
    }

    /** Header row for the items table: name / qty / price. */
    private fun drawItemsTableHeader(
        g: Graphics2D,
        widthPt: Double,
        y0: Double,
        itemLabel: String,
        qtyLabel: String,
        priceLabel: String,
    ): Double {
        g.font = fontItemBold
        val fm = g.fontMetrics
        val baseline = y0 + fm.ascent
        val qtyCol = widthPt * 0.6
        val priceCol = widthPt * 0.78

        if (model.isArabic) {
            // RTL: item name from right, qty middle, price left.
            val itemW = fm.stringWidth(itemLabel)
            g.drawString(itemLabel, (widthPt - 4 - itemW).toFloat(), baseline.toFloat())
            val qtyW = fm.stringWidth(qtyLabel)
            g.drawString(qtyLabel, (widthPt - qtyCol - qtyW / 2).toFloat(), baseline.toFloat())
            g.drawString(priceLabel, 4f, baseline.toFloat())
        } else {
            g.drawString(itemLabel, 4f, baseline.toFloat())
            val qtyW = fm.stringWidth(qtyLabel)
            g.drawString(qtyLabel, (qtyCol - qtyW / 2).toFloat(), baseline.toFloat())
            val priceW = fm.stringWidth(priceLabel)
            g.drawString(priceLabel, (widthPt - 4 - priceW).toFloat(), baseline.toFloat())
        }

        val nextY = y0 + fm.height + 1
        g.stroke = BasicStroke(0.8f)
        g.drawLine(2, nextY.toInt(), (widthPt - 2).toInt(), nextY.toInt())
        return nextY + 2
    }

    private fun drawItemRow(
        g: Graphics2D,
        widthPt: Double,
        y0: Double,
        item: ReceiptModel.ItemRow,
    ): Double {
        g.font = fontItemName
        val nameFm = g.fontMetrics
        g.font = fontItemBold
        val boldFm = g.fontMetrics

        val nameAreaWidth = widthPt * 0.55
        val qtyCol = widthPt * 0.6
        val priceCol = widthPt * 0.78
        val qtyW = boldFm.stringWidth(item.qty)
        val priceW = boldFm.stringWidth(item.price)

        // Wrap item name within the name area.
        val nameLines = wrapText(item.name, nameFm, nameAreaWidth)
        var y = y0
        nameLines.forEachIndexed { i, line ->
            g.font = fontItemName
            val baseline = y + nameFm.ascent
            if (model.isArabic) {
                val w = nameFm.stringWidth(line)
                g.drawString(line, (widthPt - 4 - w).toFloat(), baseline.toFloat())
            } else {
                g.drawString(line, 4f, baseline.toFloat())
            }
            // Only draw qty + price on the first line.
            if (i == 0) {
                g.font = fontItemBold
                val boldBaseline = y + boldFm.ascent
                if (model.isArabic) {
                    g.drawString(item.qty, (widthPt - qtyCol - qtyW / 2).toFloat(), boldBaseline.toFloat())
                    g.drawString(item.price, 4f, boldBaseline.toFloat())
                } else {
                    g.drawString(item.qty, (qtyCol - qtyW / 2).toFloat(), boldBaseline.toFloat())
                    g.drawString(item.price, (widthPt - 4 - priceW).toFloat(), boldBaseline.toFloat())
                }
            }
            y += nameFm.height
        }
        return y + 1
    }

    /** Big framed total at the bottom, centered. */
    private fun drawGrandTotalBox(
        g: Graphics2D,
        widthPt: Double,
        y0: Double,
        label: String,
        value: String,
        dashed: Boolean = false,
    ): Double {
        val padding = 6.0
        g.font = fontTotalLabel
        val labelFm = g.fontMetrics
        g.font = fontTotalValue
        val valueFm = g.fontMetrics
        val rowHeight = maxOf(labelFm.height, valueFm.height) + padding * 2

        // Box outline
        val strokeW = if (dashed) 1.0f else 1.5f
        g.stroke = if (dashed) {
            BasicStroke(strokeW, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, floatArrayOf(4f, 3f), 0f)
        } else {
            BasicStroke(strokeW)
        }
        g.drawRect(2, y0.toInt(), (widthPt - 4).toInt(), rowHeight.toInt())
        g.stroke = BasicStroke(1f)

        val baseline = y0 + padding + valueFm.ascent
        val labelW = labelFm.stringWidth(label)
        val valueW = valueFm.stringWidth(value)

        if (model.isArabic) {
            g.font = fontTotalLabel
            g.drawString(label, (widthPt - padding - labelW).toFloat(), baseline.toFloat())
            g.font = fontTotalValue
            g.drawString(value, padding.toFloat(), baseline.toFloat())
        } else {
            g.font = fontTotalLabel
            g.drawString(label, padding.toFloat(), baseline.toFloat())
            g.font = fontTotalValue
            g.drawString(value, (widthPt - padding - valueW).toFloat(), baseline.toFloat())
        }

        return y0 + rowHeight + 1
    }

    private fun drawNotesBox(
        g: Graphics2D,
        widthPt: Double,
        y0: Double,
        title: String,
        text: String,
    ): Double {
        val padding = 4.0
        g.font = fontItemBold
        val titleFm = g.fontMetrics
        g.font = fontNotes
        val textFm = g.fontMetrics

        // Pre-measure body lines to size the bounding box.
        val maxTextW = widthPt - padding * 2 - 8
        val lines = wrapText(text, textFm, maxTextW)
        val boxHeight = padding * 2 + titleFm.height + lines.size * textFm.height

        g.stroke = BasicStroke(
            0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0f, floatArrayOf(3f, 2f), 0f,
        )
        g.drawRect(2, y0.toInt(), (widthPt - 4).toInt(), boxHeight.toInt())
        g.stroke = BasicStroke(1f)

        // Title
        g.font = fontItemBold
        val titleBaseline = y0 + padding + titleFm.ascent
        val titleW = titleFm.stringWidth(title)
        if (model.isArabic) {
            g.drawString(title, (widthPt - padding - 4 - titleW).toFloat(), titleBaseline.toFloat())
        } else {
            g.drawString(title, (padding + 4).toFloat(), titleBaseline.toFloat())
        }

        // Body
        g.font = fontNotes
        var y = y0 + padding + titleFm.height
        for (line in lines) {
            val baseline = y + textFm.ascent
            if (model.isArabic) {
                val w = textFm.stringWidth(line)
                g.drawString(line, (widthPt - padding - 4 - w).toFloat(), baseline.toFloat())
            } else {
                g.drawString(line, (padding + 4).toFloat(), baseline.toFloat())
            }
            y += textFm.height
        }
        return y0 + boxHeight + 1
    }

    private fun drawHorizontalLine(
        g: Graphics2D,
        widthPt: Double,
        y0: Double,
        double: Boolean = false,
    ): Double {
        if (double) {
            g.stroke = BasicStroke(1.4f)
            g.drawLine(2, y0.toInt(), (widthPt - 2).toInt(), y0.toInt())
            g.stroke = BasicStroke(1f)
            return y0 + 2
        }
        g.stroke = BasicStroke(
            0.8f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL,
            0f, floatArrayOf(3f, 2f), 0f,
        )
        g.drawLine(2, y0.toInt(), (widthPt - 2).toInt(), y0.toInt())
        g.stroke = BasicStroke(1f)
        return y0 + 1
    }

    /**
     * Wrap [text] into lines that fit within [maxWidth] given the [fm]
     * font metrics. Greedy word-wrap — single overflowing words are
     * emitted on their own line.
     */
    private fun wrapText(text: String, fm: java.awt.FontMetrics, maxWidth: Double): List<String> {
        if (fm.stringWidth(text) <= maxWidth) return listOf(text)
        val words = text.split(' ')
        val out = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val tentative = if (current.isEmpty()) word else "$current $word"
            if (fm.stringWidth(tentative) <= maxWidth) {
                current = StringBuilder(tentative)
            } else {
                if (current.isNotEmpty()) {
                    out += current.toString()
                    current = StringBuilder(word)
                } else {
                    // Single word overflows the column — emit it as-is.
                    out += word
                }
            }
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
    }
}
