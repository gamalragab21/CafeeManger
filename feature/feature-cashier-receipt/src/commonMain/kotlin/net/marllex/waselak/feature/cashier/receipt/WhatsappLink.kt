package net.marllex.waselak.feature.cashier.receipt

/**
 * Build a `https://wa.me/<phone>?text=<msg>` deep link that opens
 * WhatsApp (web, mobile app, or desktop) directly into a chat with the
 * given phone number and a prefilled message.
 *
 * Phone normalisation mirrors the backend CRM logic
 * (CrmRoutes.kt:480) — strip non-digits, then prefix `"2"` for
 * Egyptian local-format numbers (11 digits starting with `0`). Numbers
 * already in international form (starting with `2`) or short enough to
 * already be international (≤ 10 digits) pass through unchanged.
 *
 * The message is URL-encoded per RFC 3986 — letters, digits, `-_.~`
 * pass through, everything else becomes `%XX` UTF-8 hex. Spaces
 * become `%20` (NOT `+`); WhatsApp's parser rejects `+` for spaces in
 * the `text` parameter on some clients.
 */
internal fun buildWhatsappLink(phone: String, message: String): String {
    val cleaned = phone.filter { it.isDigit() }
    val waNumber = if (cleaned.startsWith("2") || cleaned.length <= 10) cleaned else "2$cleaned"
    return "https://wa.me/$waNumber?text=${urlEncodeComponent(message)}"
}

private fun urlEncodeComponent(s: String): String = buildString(s.length * 2) {
    for (b in s.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        if (
            (c in 0x30..0x39) ||           // 0-9
            (c in 0x41..0x5A) ||           // A-Z
            (c in 0x61..0x7A) ||           // a-z
            c == 0x2D || c == 0x5F ||      // - _
            c == 0x2E || c == 0x7E         // . ~
        ) {
            append(c.toChar())
        } else {
            append('%')
            append(c.toString(16).uppercase().padStart(2, '0'))
        }
    }
}

/**
 * Build the WhatsApp message body for a receipt. Bilingual (Arabic +
 * English) so the customer can read it in whichever language they use
 * day to day. Includes vendor name, order ID, total, and the
 * digital-receipt link if the merchant has digital receipts enabled.
 */
internal fun whatsappReceiptMessage(
    vendorName: String,
    orderIdValue: String,
    total: String,
    currency: String,
    shareUrl: String?,
    language: String,
): String {
    val isAr = language == "ar"
    val greeting = if (isAr) "أهلاً بك في $vendorName 👋" else "Welcome to $vendorName 👋"
    val orderLine = if (isAr) "رقم الطلب: $orderIdValue" else "Order: $orderIdValue"
    val totalLine = if (isAr) "الإجمالي: $total $currency" else "Total: $total $currency"
    val thanks = if (isAr) "شكراً لزيارتكم! 🙏" else "Thank you for your order! 🙏"
    val linkLine = shareUrl?.let {
        if (isAr) "تفاصيل الفاتورة:\n$it" else "View receipt:\n$it"
    }
    return buildString {
        appendLine(greeting)
        appendLine()
        appendLine(orderLine)
        appendLine(totalLine)
        if (linkLine != null) {
            appendLine()
            appendLine(linkLine)
        }
        appendLine()
        append(thanks)
    }
}
