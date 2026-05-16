package net.marllex.waselak.core.ui.thermal

import net.marllex.waselak.core.ui.platform.ReceiptModel

/**
 * Convert a platform-neutral [ReceiptModel] into the markup string that
 * `com.dantsu.escposprinter.EscPosPrinter.printFormattedTextAndCut(...)`
 * understands.
 *
 * Markup cheat-sheet (Dantsu / ESC/POS):
 *   [L] / [C] / [R]   line-alignment tags
 *   <b>...</b>        bold
 *   <u>...</u>        underline
 *   [L]a[R]b          two-column row (label / value)
 *   \n                end of line — every line MUST end with \n
 *
 * Why we are deliberately NOT using `<font size='big'>` etc:
 *   XP-class printers (XP-P323B and its clones) have a quirky markup
 *   parser that silently drops the rest of a line when it sees a font-
 *   size tag larger than `normal`. Earlier builds put the vendor name
 *   and order ID inside `<font size='big-2'>` and only the literal `##`
 *   from the order-ID prefix came through; everything else on those
 *   lines was eaten. Sticking to `<b>` + alignment tags is the reliable
 *   subset that prints on every ESC/POS printer we've tested.
 *
 * Arabic glyphs: the EscPosPrinter is configured with `windows-1256`
 * charset + codepage 22 (PC864) in PrintReceipt.android.kt, so Arabic
 * letters in vendor names, item names, and labels are encoded correctly.
 * Do NOT insert Unicode dingbats (☎, ★, etc.) — they don't exist in
 * either WPC1256 or PC864 and the line containing them gets dropped.
 */
fun ReceiptModel.toEscPosMarkup(): String = buildString {
    // ── Part 1: vendor header ────────────────────────────────────────
    appendLine("[C]<b>${escape(vendorName)}</b>")
    vendorAddress?.let { appendLine("[C]${escape(it)}") }
    vendorPhone?.let { appendLine("[C]${if (isArabic) "هاتف" else "Tel"}: ${escape(it)}") }
    vendorWallet?.let { appendLine("[C]${if (isArabic) "محفظة" else "Wallet"}: ${escape(it)}") }
    vendorWhatsapp?.let { appendLine("[C]${if (isArabic) "واتساب" else "WhatsApp"}: ${escape(it)}") }
    appendLine("[C]================================")

    // ── Order ID — emphasised with bold + double underline ───────────
    appendLine("[C]<u><b>${escape(orderIdLabel)} ${escape(orderIdValue)}</b></u>")
    appendLine("[C]--------------------------------")

    // ── Part 2: order info rows (Date / Channel / Status / Cashier …)
    for ((label, value) in orderRows) {
        appendLine("[L]<b>${escape(label)}</b>[R]${escape(value)}")
    }

    // ── Part 2b: client info ─────────────────────────────────────────
    if (clientRows.isNotEmpty()) {
        appendLine("[C]--------------------------------")
        for ((label, value) in clientRows) {
            appendLine("[L]<b>${escape(label)}</b>[R]${escape(value)}")
        }
    }

    // ── Part 3: line items ───────────────────────────────────────────
    appendLine("[C]================================")
    for (item in items) {
        appendLine("[L]<b>${escape(item.name)}</b>")
        appendLine("[L]  ${escape(item.qty)} x ${escape(item.unitPrice)}[R]${escape(item.lineTotal)}")
    }
    appendLine("[C]--------------------------------")

    // ── Part 4: totals + grand ──────────────────────────────────────
    for ((label, value) in totalsRows) {
        appendLine("[L]${escape(label)}[R]${escape(value)}")
    }
    appendLine("[C]================================")
    appendLine("[C]<b>${escape(grandTotalLabel)}: ${escape(grandTotalValue)}</b>")
    appendLine("[C]================================")

    // ── Returns / net total (only when partial returns exist) ────────
    if (refundRows.isNotEmpty()) {
        for ((label, value) in refundRows) {
            appendLine("[L]${escape(label)}[R]${escape(value)}")
        }
    }
    if (netTotalLabel != null && netTotalValue != null) {
        appendLine("[C]<b>${escape(netTotalLabel)}: ${escape(netTotalValue)}</b>")
        appendLine("[C]--------------------------------")
    }

    // ── Part 5: notes ─────────────────────────────────────────────────
    if (notesLabel != null && notesText != null) {
        appendLine("[L]<b>${escape(notesLabel)}:</b>")
        appendLine("[L]${escape(notesText)}")
    }

    // ── Part 6: footer / thank-you ────────────────────────────────────
    appendLine("[C]<b>${escape(footerThanks)}</b>")
}

/**
 * Escape markup-meta characters the Dantsu parser would otherwise
 * interpret. The library tokenises on '[', ']', '<', '>'. Worker /
 * client names with literal `[L]` or `<b>` in them are exotic but
 * cheap to defend against.
 */
private fun escape(s: String): String =
    s.replace("[", "(").replace("]", ")").replace("<", "(").replace(">", ")")
