package net.marllex.waselak.core.ui.thermal

import net.marllex.waselak.core.ui.platform.ReceiptModel
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * Raw ESC/POS byte formatter — bypasses Dantsu's markup parser entirely.
 *
 * We arrived here after multiple Dantsu-library prints completed cleanly
 * over Bluetooth (connected, bytes sent, no error) but the XP-P323B
 * produced no paper. The same printer self-tests fine and prints from a
 * third-party Android print app, so the printer hardware is OK — it's
 * the library's byte stream specifically that this firmware refuses.
 *
 * This formatter emits the canonical ESC/POS command set every thermal
 * printer recognises:
 *   ESC @       0x1B 0x40                — initialise
 *   ESC t  n    0x1B 0x74 n              — select code-page n
 *   ESC a  n    0x1B 0x61 n              — align L/C/R (0/1/2)
 *   ESC E  n    0x1B 0x45 n              — bold on/off
 *   GS  !  n    0x1D 0x21 n              — char size (low nibble = height, high = width)
 *   LF          0x0A                     — print line
 *   GS V B n    0x1D 0x56 0x42 n         — feed n dots then full cut
 *
 * Arabic: encoded via windows-1256 (the Microsoft Arabic code page) and
 * the printer is told to interpret it via `ESC t 32`. Code page 32 is
 * WPC1256 on XP-class printers; this matches the encoding so glyphs
 * render correctly. Don't try `ESC t 22` (PC864) — that's a different
 * Arabic encoding (DOS) and the bytes won't match.
 */
object EscPosCmd {
    val INIT = byteArrayOf(0x1B, 0x40)
    val LF = byteArrayOf(0x0A)

    val ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    val ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    val ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)

    val BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    val BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)

    val SIZE_NORMAL = byteArrayOf(0x1D, 0x21, 0x00)
    val SIZE_DOUBLE_HEIGHT = byteArrayOf(0x1D, 0x21, 0x01)
    val SIZE_DOUBLE_BOTH = byteArrayOf(0x1D, 0x21, 0x11)

    val FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x10)

    fun codepage(n: Int) = byteArrayOf(0x1B, 0x74, n.toByte())
}

/**
 * Build a width-aware ESC/POS byte stream from a [ReceiptModel].
 *
 * @param charsPerLine 32 for 58 mm, 48 for 80 mm. Used to format
 *   two-column [L]label[R]value rows with the right padding.
 */
fun ReceiptModel.toRawEscPosBytes(charsPerLine: Int): ByteArray {
    val charset = Charset.forName("windows-1256")
    val buf = ByteArrayOutputStream()

    fun raw(bytes: ByteArray) { buf.write(bytes) }
    fun text(s: String) { buf.write(s.toByteArray(charset)) }
    fun lf() { buf.write(EscPosCmd.LF) }
    fun line(s: String) { text(s); lf() }
    fun row(label: String, value: String) {
        val pad = charsPerLine - label.length - value.length
        line(label + " ".repeat(maxOf(1, pad)) + value)
    }

    // ── Initialise + select code page ─────────────────────────────────
    raw(EscPosCmd.INIT)
    raw(EscPosCmd.codepage(32)) // WPC1256 (Windows Arabic) — matches charset

    // ── Vendor header (centered, bold, double-size) ───────────────────
    raw(EscPosCmd.ALIGN_CENTER)
    raw(EscPosCmd.BOLD_ON)
    raw(EscPosCmd.SIZE_DOUBLE_BOTH)
    line(vendorName)
    raw(EscPosCmd.SIZE_NORMAL)
    raw(EscPosCmd.BOLD_OFF)
    vendorAddress?.let { line(it) }
    vendorPhone?.let { line("${if (isArabic) "هاتف" else "Tel"}: $it") }
    vendorWallet?.let { line("${if (isArabic) "محفظة" else "Wallet"}: $it") }
    vendorWhatsapp?.let { line("${if (isArabic) "واتساب" else "WhatsApp"}: $it") }
    line("=".repeat(charsPerLine))

    // ── Order ID — biggest element ────────────────────────────────────
    raw(EscPosCmd.BOLD_ON)
    raw(EscPosCmd.SIZE_DOUBLE_BOTH)
    line("$orderIdLabel $orderIdValue")
    raw(EscPosCmd.SIZE_NORMAL)
    raw(EscPosCmd.BOLD_OFF)
    line("-".repeat(charsPerLine))

    // ── Order info rows ───────────────────────────────────────────────
    raw(EscPosCmd.ALIGN_LEFT)
    for ((label, value) in orderRows) {
        row(label, value)
    }

    // ── Client info ───────────────────────────────────────────────────
    if (clientRows.isNotEmpty()) {
        raw(EscPosCmd.ALIGN_CENTER)
        line("-".repeat(charsPerLine))
        raw(EscPosCmd.ALIGN_LEFT)
        for ((label, value) in clientRows) {
            row(label, value)
        }
    }

    // ── Items ─────────────────────────────────────────────────────────
    raw(EscPosCmd.ALIGN_CENTER)
    line("=".repeat(charsPerLine))
    raw(EscPosCmd.ALIGN_LEFT)
    for (item in items) {
        raw(EscPosCmd.BOLD_ON)
        line(item.name)
        raw(EscPosCmd.BOLD_OFF)
        row("  ${item.qty} x ${item.unitPrice}", item.lineTotal)
    }
    raw(EscPosCmd.ALIGN_CENTER)
    line("-".repeat(charsPerLine))

    // ── Totals + grand total ──────────────────────────────────────────
    raw(EscPosCmd.ALIGN_LEFT)
    for ((label, value) in totalsRows) {
        row(label, value)
    }
    raw(EscPosCmd.ALIGN_CENTER)
    line("=".repeat(charsPerLine))
    raw(EscPosCmd.BOLD_ON)
    raw(EscPosCmd.SIZE_DOUBLE_BOTH)
    line("$grandTotalLabel: $grandTotalValue")
    raw(EscPosCmd.SIZE_NORMAL)
    raw(EscPosCmd.BOLD_OFF)
    line("=".repeat(charsPerLine))

    // ── Refunds + net total ───────────────────────────────────────────
    if (refundRows.isNotEmpty()) {
        raw(EscPosCmd.ALIGN_LEFT)
        for ((label, value) in refundRows) {
            row(label, value)
        }
    }
    if (netTotalLabel != null && netTotalValue != null) {
        raw(EscPosCmd.ALIGN_CENTER)
        raw(EscPosCmd.BOLD_ON)
        line("$netTotalLabel: $netTotalValue")
        raw(EscPosCmd.BOLD_OFF)
        line("-".repeat(charsPerLine))
    }

    // ── Notes ─────────────────────────────────────────────────────────
    if (notesLabel != null && notesText != null) {
        raw(EscPosCmd.ALIGN_LEFT)
        raw(EscPosCmd.BOLD_ON)
        line("$notesLabel:")
        raw(EscPosCmd.BOLD_OFF)
        line(notesText)
    }

    // ── Footer ────────────────────────────────────────────────────────
    raw(EscPosCmd.ALIGN_CENTER)
    raw(EscPosCmd.BOLD_ON)
    line(footerThanks)
    raw(EscPosCmd.BOLD_OFF)

    // Feed + cut. The 3 line feeds clear the print head past the cutter
    // bar so the last line of text isn't sliced in half.
    lf(); lf(); lf()
    raw(EscPosCmd.FEED_AND_CUT)

    return buf.toByteArray()
}
