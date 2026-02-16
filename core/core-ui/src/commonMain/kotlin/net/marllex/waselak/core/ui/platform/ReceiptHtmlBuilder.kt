package net.marllex.waselak.core.ui.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.Vendor

private fun formatAmount(amount: Double, currency: String = "EGP"): String {
    val rounded = kotlin.math.round(amount * 100) / 100.0
    val whole = rounded.toLong()
    val frac = ((rounded - whole) * 100 + 0.5).toInt()
    return "$whole.${frac.toString().padStart(2, '0')} $currency"
}

private fun formatDate(epochMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${
        dt.dayOfMonth.toString().padStart(2, '0')
    }  ${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
}

private fun channelLabel(channel: OrderChannel): String = when (channel) {
    OrderChannel.DINE_IN -> "Dine In"
    OrderChannel.DELIVERY -> "Delivery"
    OrderChannel.TAKEAWAY -> "Takeaway"
}

private fun paymentLabel(method: PaymentMethod): String = when (method) {
    PaymentMethod.CASH -> "Cash"
    PaymentMethod.WALLET -> "Wallet"
    PaymentMethod.CARD -> "Card"
}

private fun String.htmlEscape(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

fun buildReceiptHtml(order: Order, vendor: Vendor?): String = buildString {
    append("""
<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  @page { margin: 8mm; size: 80mm auto; }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: 'Segoe UI', Arial, Helvetica, sans-serif;
    font-size: 12px;
    color: #1C1917;
    max-width: 80mm;
    margin: 0 auto;
    padding: 8px;
  }
  .header { text-align: center; margin-bottom: 12px; }
  .header h1 { font-size: 16px; font-weight: bold; margin-bottom: 2px; }
  .header p { font-size: 11px; color: #78716C; }
  .divider {
    border: none;
    border-top: 1px dashed #D6D3D1;
    margin: 10px 0;
  }
  .info-section {
    background: #FAFAF9;
    border-radius: 6px;
    padding: 8px;
    margin-bottom: 8px;
  }
  .info-row {
    display: flex;
    justify-content: space-between;
    margin-bottom: 4px;
    font-size: 11px;
  }
  .info-row .label { color: #78716C; }
  .info-row .value { font-weight: 500; }
  .info-row .value.bold { font-weight: 700; }
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 8px 0;
  }
  th {
    font-size: 10px;
    color: #78716C;
    font-weight: 500;
    text-align: left;
    padding: 4px 2px;
    border-bottom: 1px solid #E7E5E4;
  }
  th.qty, td.qty { text-align: center; width: 35px; }
  th.price, td.price { text-align: right; width: 75px; }
  td {
    font-size: 11px;
    padding: 4px 2px;
    vertical-align: middle;
  }
  td.name { font-weight: 500; }
  .totals { margin-top: 8px; }
  .totals .info-row { font-size: 12px; }
  .grand-total {
    background: #F0FDFA;
    border: 1px solid #CCFBF1;
    border-radius: 8px;
    padding: 10px 12px;
    margin-top: 8px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .grand-total .label { font-size: 14px; font-weight: 700; }
  .grand-total .value { font-size: 14px; font-weight: 700; color: #0D9488; }
  .notes {
    background: #FAFAF9;
    border-radius: 6px;
    padding: 8px;
    margin-top: 10px;
  }
  .notes .title { font-size: 10px; color: #78716C; margin-bottom: 2px; }
  .notes .text { font-size: 11px; color: #44403C; }
  .footer {
    text-align: center;
    font-style: italic;
    color: #A8A29E;
    font-size: 11px;
    margin-top: 16px;
    padding-bottom: 8px;
  }
  @media print {
    body { padding: 0; }
  }
</style>
</head>
<body>
""")

    // Header
    append("<div class='header'>")
    append("<h1>${(vendor?.name ?: "Restaurant").htmlEscape()}</h1>")
    vendor?.address?.let { append("<p>${it.htmlEscape()}</p>") }
    vendor?.contactPhone?.let { append("<p>${it.htmlEscape()}</p>") }
    append("</div>")

    append("<hr class='divider'>")

    // Order Info
    append("<div class='info-section'>")
    append("<div class='info-row'><span class='label'>Order #</span><span class='value bold'>#${order.id.takeLast(8).uppercase()}</span></div>")
    append("<div class='info-row'><span class='label'>Date</span><span class='value'>${formatDate(order.createdAt)}</span></div>")
    append("<div class='info-row'><span class='label'>Channel</span><span class='value'>${channelLabel(order.channel)}</span></div>")
    append("<div class='info-row'><span class='label'>Payment</span><span class='value'>${paymentLabel(order.paymentMethod)}</span></div>")
    append("<div class='info-row'><span class='label'>Cashier</span><span class='value'>${(order.cashierName ?: "-").htmlEscape()}</span></div>")
    append("</div>")

    // Client Info
    if (order.clientName != null || order.clientPhone != null || order.clientAddress != null) {
        append("<div class='info-section'>")
        order.clientName?.let {
            append("<div class='info-row'><span class='label'>Client</span><span class='value'>${it.htmlEscape()}</span></div>")
        }
        order.clientPhone?.let {
            append("<div class='info-row'><span class='label'>Phone</span><span class='value'>${it.htmlEscape()}</span></div>")
        }
        order.clientAddress?.let {
            append("<div class='info-row'><span class='label'>Address</span><span class='value'>${it.htmlEscape()}</span></div>")
        }
        append("</div>")
    }

    append("<hr class='divider'>")

    // Items Table
    append("<table>")
    append("<thead><tr><th>Item</th><th class='qty'>Qty</th><th class='price'>Price</th></tr></thead>")
    append("<tbody>")
    order.items.forEach { item ->
        val lineTotal = item.itemPriceSnapshot * item.quantity
        append("<tr>")
        append("<td class='name'>${item.itemNameSnapshot.htmlEscape()}</td>")
        append("<td class='qty'>${item.quantity}</td>")
        append("<td class='price'>${formatAmount(lineTotal)}</td>")
        append("</tr>")
    }
    append("</tbody></table>")

    append("<hr class='divider'>")

    // Totals
    append("<div class='totals'>")
    append("<div class='info-row'><span class='label'>Subtotal</span><span class='value'>${formatAmount(order.subtotal)}</span></div>")
    if (order.tax > 0.0) {
        append("<div class='info-row'><span class='label'>Tax</span><span class='value'>${formatAmount(order.tax)}</span></div>")
    }
    if (order.deliveryFee > 0.0) {
        append("<div class='info-row'><span class='label'>Delivery Fee</span><span class='value'>${formatAmount(order.deliveryFee)}</span></div>")
    }
    append("</div>")

    // Grand Total
    append("<div class='grand-total'>")
    append("<span class='label'>Total</span>")
    append("<span class='value'>${formatAmount(order.total)}</span>")
    append("</div>")

    // Notes
    order.notes?.let { notes ->
        append("<div class='notes'>")
        append("<div class='title'>Notes</div>")
        append("<div class='text'>${notes.htmlEscape()}</div>")
        append("</div>")
    }

    // Footer
    append("<div class='footer'>Thank you for your visit!</div>")

    append("</body></html>")
}
