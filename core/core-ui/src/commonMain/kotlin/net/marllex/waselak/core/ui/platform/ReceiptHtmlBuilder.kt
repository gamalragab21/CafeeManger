package net.marllex.waselak.core.ui.platform

import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.Vendor

// ─────────────────────────────────────────────────────────────────────────────
// Receipt HTML builder — 6-part structured layout, RTL/LTR aware.
//
// Per merchant feedback the receipt is partitioned into clearly-separated
// sections with intentional font weights:
//
//   Part 1 — Restaurant header (logo + name + address + phone): 18px base
//   Part 2 — Client info + cashier + delivery driver:           18px base
//   Part 3 — Order items table (name | qty | price columns):    14px base
//             Smaller font + row layout so more items fit before
//             the receipt spills past one continuous page.
//   Part 4 — Subtotals + delivery fee + grand total:            18px base
//   Part 5 — QR code for digital receipt. Auto-HIDDEN when the
//             item list is long enough that drawing it would push
//             the receipt onto a second page. Threshold tuned for
//             80mm thermal at the current font scale.
//   Part 6 — Thank-you / closing greeting.                      same as P1-P2
//
// Order ID stays as the largest text (35px value / 30px label, no box)
// because that was the previous round's accepted design — the merchant
// uses it as the primary glance-at-it identifier when calling out an
// order to a customer.
// ─────────────────────────────────────────────────────────────────────────────

private fun String.htmlEscape(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun buildReceiptHtml(
    order: Order,
    vendor: Vendor?,
    language: String = "ar",
    @Suppress("UNUSED_PARAMETER") qrCodeUrl: String? = null,
): String {
    val model = buildReceiptModel(order, vendor, language)
    val isAr = model.isArabic
    val lang = if (isAr) "ar" else "en"
    val dir = if (isAr) "rtl" else "ltr"
    // QR code intentionally removed from the printed receipt (merchant
    // request — too noisy on the thermal print, customer never scanned).
    // `qrCodeUrl` parameter is kept for source-compatibility with the
    // various platform callers but is no longer rendered. Safe to drop
    // the parameter entirely once all platform callers stop passing it.

    // Sub-labels for items table header
    val tItem = if (isAr) "الصنف" else "Item"
    val tQty = if (isAr) "الكمية" else "Qty"
    val tPrice = if (isAr) "السعر" else "Price"

    return buildString {
        append("""
<!DOCTYPE html>
<html lang="$lang" dir="$dir">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  @page { margin: 0; size: 80mm auto; }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body { background: #fff; margin: 0; padding: 0; }
  body {
    /* Font sizes bumped substantially (final round) — receipt now
       fills the printer preview canvas instead of looking like a
       small column on a virtual A4 page. Base 22px; everything
       scales from there. */
    font-family: 'Tajawal', 'Cairo', 'Noto Naskh Arabic',
                 'Segoe UI', 'Tahoma', Helvetica, Arial, sans-serif;
    font-size: 22px;
    line-height: 1.4;
    color: #000;
    text-align: center;
    width: 100%;
    padding: 10px 6px 0 6px;
  }

  /* ═══ Part 1 — Restaurant header ═══════════════════════════════ */
  .part-header { text-align: center; margin-bottom: 8px; }
  .hdr-logo {
    width: 90px; height: 90px;
    border-radius: 50%; object-fit: cover;
    display: block;
    margin: 0 auto 6px;
  }
  .hdr-name {
    font-size: 32px;
    font-weight: 900;
    margin-bottom: 4px;
  }
  .hdr-line {
    font-size: 18px;
    color: #222;
    margin: 3px 0;
  }

  /* ═══ Order ID — focal point ═══════════════════════════════════ */
  .order-id {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    margin: 10px 4px;
    padding: 0 8px;
  }
  .order-id .label {
    font-size: 34px;
    font-weight: 900;
    color: #000;
  }
  .order-id .value {
    font-size: 40px;
    font-weight: 900;
    color: #000;
    letter-spacing: 0.5px;
    direction: ltr;
    unicode-bidi: plaintext;
  }

  /* ═══ Part 2 — Client/cashier/delivery info ═══════════════════
     Same 22px base as the body. Each row is a space-between flex
     layout so label and value sit on opposite edges (RTL/LTR-aware
     via body's `dir` attribute). */
  .part-info { margin: 6px 0; }
  .kv {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 8px;
    padding: 0 10px;
    margin: 8px 0;
    font-size: 22px;
  }
  .kv .label {
    font-weight: 700;
    color: #222;
    text-align: start;
  }
  .kv .value {
    font-weight: 800;
    color: #000;
    text-align: end;
    direction: ltr;
    unicode-bidi: plaintext;
  }

  /* ═══ Part 3 — Items table ════════════════════════════════════
     Compact 3-column row: item name takes most of the row, qty +
     price hug the other edge. Smaller font (12px) than parts 1/2
     so more items fit per page. */
  .part-items {
    margin: 6px 0;
    border-top: 1px solid #333;
    border-bottom: 1px solid #333;
    padding: 3px 0;
  }
  .items-header {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 6px;
    padding: 5px 8px;
    font-size: 16px;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 0.5px;
    color: #555;
    border-bottom: 1px dashed #aaa;
  }
  .items-header .col-name { flex: 1; text-align: start; }
  .items-header .col-qty  { width: 52px; text-align: center; }
  .items-header .col-price { width: 100px; text-align: end; }

  .item-row {
    display: flex;
    justify-content: space-between;
    align-items: baseline;
    gap: 6px;
    padding: 4px 8px;
    font-size: 19px;
  }
  .item-row .col-name {
    flex: 1;
    text-align: start;
    font-weight: 700;
    line-height: 1.3;
    word-wrap: break-word;
    overflow-wrap: anywhere;
  }
  .item-row .col-qty {
    width: 52px;
    text-align: center;
    font-weight: 800;
    direction: ltr;
    unicode-bidi: plaintext;
  }
  .item-row .col-price {
    width: 100px;
    text-align: end;
    font-weight: 800;
    direction: ltr;
    unicode-bidi: plaintext;
  }
  /* Tighter alternating rows for scanning */
  .item-row:nth-child(odd of .item-row) { background: #f9f9f9; }

  /* ═══ Part 4 — Totals + grand total ═══════════════════════════
     Slightly smaller than the body so totals don't dominate; the
     grand total below is the framed focal element. */
  .part-totals { margin: 9px 0; }
  .part-totals .kv { margin: 5px 0; font-size: 20px; }
  .part-totals .kv .label { font-weight: 600; }
  .part-totals .kv .value { font-size: 21px; }

  .grand-wrap { text-align: center; margin: 12px 0 8px 0; }
  .grand {
    display: inline-block;
    padding: 12px 24px;
    border: 2.5px solid #000;
    text-align: center;
    min-width: 75%;
  }
  .grand .grand-label {
    font-size: 17px;
    font-weight: 800;
    text-transform: uppercase;
    letter-spacing: 2px;
    color: #333;
    margin-bottom: 4px;
  }
  .grand .grand-value {
    font-size: 35px;
    font-weight: 900;
    color: #000;
    line-height: 1.1;
    direction: ltr;
    unicode-bidi: plaintext;
  }
  .grand-wrap.muted .grand {
    border-style: dashed;
    border-width: 1.5px;
  }
  .grand-wrap.muted .grand-value { font-size: 26px; }

  /* Notes (when present) */
  .notes {
    margin: 9px 8px 0 8px;
    padding: 8px 10px;
    border: 1px dashed #aaa;
    font-size: 18px;
    text-align: center;
  }
  .notes .notes-title { font-weight: 800; margin-bottom: 3px; }

  /* Part 5 (QR code) removed — receipt is now 5 parts: header / client /
     items / totals / footer. */

  /* ═══ Part 6 — Footer / closing greeting ═══════════════════════ */
  .part-footer {
    margin: 12px 0 0 0;
    padding: 8px 6px 0 6px;
    border-top: 1px dashed #999;
    font-size: 22px;
    font-weight: 800;
    text-align: center;
  }

  /* Soft separator between major sections */
  .section-divider {
    border: 0;
    border-top: 1px dashed #888;
    margin: 6px 4px;
  }
  .section-divider-strong {
    border: 0;
    border-top: 2px solid #000;
    margin: 7px 4px;
  }

  /* Trim any inherited bottom margin from the very last block so
     document.body.scrollHeight reports the actual ink area, not the
     ink area + a phantom margin. The Android printer uses scrollHeight
     to size the page, so a stray bottom margin here would print as
     trailing white space on the thermal paper. Applied OUTSIDE the
     `@media print` block on purpose — we measure on screen (not on
     print), so the screen-side rule has to match. */
  body > *:last-child {
    margin-bottom: 0;
    padding-bottom: 0;
  }

  @media print {
    html, body { margin: 0 !important; padding: 0 !important; }
    body { padding: 8px 0 0 0 !important; }
    body > *:last-child {
      margin-bottom: 0 !important;
      padding-bottom: 0 !important;
    }
    img { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    /* Print-time tightening: each item row a touch shorter to
       squeeze the most items into the one-page budget. */
    .item-row { padding: 2px 8px; }
    .item-row .col-name { font-weight: 700; }
  }
</style>
</head>
<body>
""")

        // ────────────────────────────────────────────────────────────
        // Part 1 — Restaurant header (logo + name + address + phone)
        // ────────────────────────────────────────────────────────────
        append("<div class='part-header'>")
        model.vendorLogoUrl?.let {
            append("<img class='hdr-logo' src='${it.htmlEscape()}' alt='Logo'>")
        }
        append("<div class='hdr-name'>${model.vendorName.htmlEscape()}</div>")
        model.vendorAddress?.let { append("<div class='hdr-line'>${it.htmlEscape()}</div>") }
        model.vendorPhone?.let { append("<div class='hdr-line'>☎ ${it.htmlEscape()}</div>") }
        model.vendorWallet?.let {
            val label = if (isAr) "محفظة" else "Wallet"
            append("<div class='hdr-line'>$label: ${it.htmlEscape()}</div>")
        }
        model.vendorWhatsapp?.let {
            val label = if (isAr) "واتساب" else "WhatsApp"
            append("<div class='hdr-line'>$label: ${it.htmlEscape()}</div>")
        }
        append("</div>")
        append("<hr class='section-divider-strong'>")

        // Order ID — focal point (between parts 1 and 2)
        append("<div class='order-id'>")
        append("<span class='label'>${model.orderIdLabel.htmlEscape()}</span>")
        append("<span class='value'>${model.orderIdValue.htmlEscape()}</span>")
        append("</div>")
        append("<hr class='section-divider'>")

        // ────────────────────────────────────────────────────────────
        // Part 2 — Client / cashier / delivery info
        // ────────────────────────────────────────────────────────────
        append("<div class='part-info'>")
        for ((label, value) in model.orderRows) {
            append(
                "<div class='kv'>" +
                    "<span class='label'>${label.htmlEscape()}</span>" +
                    "<span class='value'>${value.htmlEscape()}</span>" +
                "</div>"
            )
        }
        if (model.clientRows.isNotEmpty()) {
            append("<hr class='section-divider'>")
            for ((label, value) in model.clientRows) {
                append(
                    "<div class='kv'>" +
                        "<span class='label'>${label.htmlEscape()}</span>" +
                        "<span class='value'>${value.htmlEscape()}</span>" +
                    "</div>"
                )
            }
        }
        append("</div>")

        // ────────────────────────────────────────────────────────────
        // Part 3 — Items table (compact 3-column rows)
        // ────────────────────────────────────────────────────────────
        append("<div class='part-items'>")
        append("<div class='items-header'>")
        append("<span class='col-name'>${tItem.htmlEscape()}</span>")
        append("<span class='col-qty'>${tQty.htmlEscape()}</span>")
        append("<span class='col-price'>${tPrice.htmlEscape()}</span>")
        append("</div>")
        for (item in model.items) {
            append("<div class='item-row'>")
            append("<span class='col-name'>${item.name.htmlEscape()}</span>")
            append("<span class='col-qty'>${item.qty.htmlEscape()}</span>")
            append("<span class='col-price'>${item.price.htmlEscape()}</span>")
            append("</div>")
        }
        append("</div>")

        // ────────────────────────────────────────────────────────────
        // Part 4 — Totals (subtotal / delivery / tax / discount / TOTAL)
        // ────────────────────────────────────────────────────────────
        append("<div class='part-totals'>")
        for ((label, value) in model.totalsRows) {
            append(
                "<div class='kv'>" +
                    "<span class='label'>${label.htmlEscape()}</span>" +
                    "<span class='value'>${value.htmlEscape()}</span>" +
                "</div>"
            )
        }
        append("<div class='grand-wrap'>")
        append("<div class='grand'>")
        append("<div class='grand-label'>${model.grandTotalLabel.htmlEscape()}</div>")
        append("<div class='grand-value'>${model.grandTotalValue.htmlEscape()}</div>")
        append("</div></div>")

        if (model.refundRows.isNotEmpty()) {
            for ((label, value) in model.refundRows) {
                append(
                    "<div class='kv' style='color:#a00;'>" +
                        "<span class='label'>${label.htmlEscape()}</span>" +
                        "<span class='value'>${value.htmlEscape()}</span>" +
                    "</div>"
                )
            }
            if (model.netTotalLabel != null && model.netTotalValue != null) {
                append("<div class='grand-wrap muted'>")
                append("<div class='grand'>")
                append("<div class='grand-label'>${model.netTotalLabel.htmlEscape()}</div>")
                append("<div class='grand-value'>${model.netTotalValue.htmlEscape()}</div>")
                append("</div></div>")
            }
        }
        append("</div>")

        // Notes (when present)
        if (model.notesLabel != null && model.notesText != null) {
            append("<div class='notes'>")
            append("<div class='notes-title'>${model.notesLabel.htmlEscape()}</div>")
            append("<div>${model.notesText.htmlEscape()}</div>")
            append("</div>")
        }

        // ────────────────────────────────────────────────────────────
        // (Part 5 — QR code — intentionally removed; see header comment.)
        // ────────────────────────────────────────────────────────────

        // ────────────────────────────────────────────────────────────
        // Part 6 — Footer / thank-you (always last so the page-end
        // trim CSS only kills the trailing whitespace below this).
        // ────────────────────────────────────────────────────────────
        append("<div class='part-footer'>${model.footerThanks.htmlEscape()}</div>")

        // JS bridge used by the Android printer to read the exact
        // rendered content height for MediaSize sizing.
        append("""
<script>
  window.__waselak_receipt_height = function() {
    // When the Android print pipeline duplicates the receipt for multi-
    // copy printing it wraps each copy in <div class='receipt-copy'>.
    // We measure JUST the first one — the print engine paginates on
    // the page-break-before separators, so each printed page is the
    // size of ONE copy, not the stacked total.
    //
    // Fall back to body.scrollHeight when there is no .receipt-copy
    // wrapper (single-copy receipts; or non-Android renderers that
    // don't use the wrapper).
    var first = document.querySelector('.receipt-copy');
    if (first) {
      var fr = first.getBoundingClientRect();
      if (fr && fr.height > 0) return Math.ceil(fr.height);
    }
    var h1 = document.body.scrollHeight;
    var h2 = document.documentElement.scrollHeight;
    var r = document.body.getBoundingClientRect();
    var h3 = r ? r.height : 0;
    return Math.ceil(Math.max(h1, h2, h3));
  };
</script>
""")
        append("</body></html>")
    }
}
