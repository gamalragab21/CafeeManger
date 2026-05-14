package net.marllex.waselak.core.ui.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.PaymentStatus
import net.marllex.waselak.core.model.PaymentTiming
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.model.VendorTypeConfigs

/**
 * Pre-localised receipt content, ready to be rendered by any platform
 * printer. The desktop printer paints these lines directly with
 * Graphics2D (no HTML round-trip); the Android/iOS HTML builder consumes
 * the same model to emit single-language markup. Centralising the labels
 * means a single source of truth for "what does this row say in
 * Arabic / English".
 */
data class ReceiptModel(
    val isArabic: Boolean,
    /** Vendor display fields, already filtered for blanks. */
    val vendorName: String,
    val vendorAddress: String?,
    val vendorPhone: String?,
    val vendorWallet: String?,
    val vendorWhatsapp: String?,
    val vendorLogoUrl: String?,
    /**
     * The order's identifying label + value, surfaced separately from
     * the rest of the rows so the renderer can give it special visual
     * weight (boxed prominent row at the top of the order info section).
     */
    val orderIdLabel: String,
    val orderIdValue: String,
    /** Remaining order info as label-value pairs (excluding the order ID). */
    val orderRows: List<Pair<String, String>>,
    /** Client info as label-value pairs (may be empty). */
    val clientRows: List<Pair<String, String>>,
    /** Line items: name, qty, price. */
    val items: List<ItemRow>,
    /** Totals rows (subtotal, delivery fee, tax, discount, points), in order. */
    val totalsRows: List<Pair<String, String>>,
    val grandTotalLabel: String,
    val grandTotalValue: String,
    val refundRows: List<Pair<String, String>>,
    val netTotalLabel: String?,
    val netTotalValue: String?,
    val notesLabel: String?,
    val notesText: String?,
    val footerThanks: String,
    /** Branding line — "Powered by Waselak Team", same on every receipt. */
    val poweredBy: String = "Powered by Waselak Team — مدعوم بواسطة فريق وصلك",
) {
    data class ItemRow(val name: String, val qty: String, val price: String)
}

/**
 * Build a [ReceiptModel] from the order + vendor + language.
 * Identical semantics to [buildReceiptHtml] but returns the structured
 * data instead of HTML — used by the desktop direct-draw printer.
 */
fun buildReceiptModel(order: Order, vendor: Vendor?, language: String): ReceiptModel {
    val isAr = language == "ar"
    val vendorTypeConfig = VendorTypeConfigs.forType(vendor?.businessType ?: "RESTAURANT")
    val orderLabel = if (isAr) vendorTypeConfig.orderLabelAr else vendorTypeConfig.orderLabelEn

    fun L(ar: String, en: String) = if (isAr) ar else en

    // Surface the order ID label and value separately so renderers can
    // draw it as a prominent boxed row at the top of the order info
    // section. The remaining rows still flow as plain label-value pairs
    // below it.
    //
    // Prefer the per-day human-friendly counter (`daily_seq` — e.g. 1, 2,
    // 3 ... resetting each day) over the UUID tail. The merchant uses
    // this as the "Order #X" the cashier calls out to the customer.
    // Falls back to the last 8 chars of the UUID for legacy orders that
    // were created before the daily_seq feature was deployed.
    // Clean "Order #5" / "طلب #5" — the # lives in the value only so it
    // isn't doubled-up when concatenated against the label.
    val orderIdLabel = orderLabel
    val orderIdValue = if (order.dailySeq > 0) {
        "#${order.dailySeq}"
    } else {
        "#${order.id.takeLast(8).uppercase()}"
    }

    // Order info rows are deliberately a *minimal* set — the merchant
    // asked us to drop Status / Payment-Status / Payment-Timing from
    // the printed receipt because they were noise to the customer.
    val orderRows = buildList {
        add(L("التاريخ", "Date") to formatDate(order.createdAt))
        add(L("النوع", "Channel") to channelLabel(order.channel, isAr))
        order.tableNumber?.takeIf { it.isNotBlank() }?.let {
            add(L("ترابيزة", "Table") to it)
        }
        add(L("الدفع", "Payment") to paymentLabel(order.paymentMethod, isAr))
        add(L("الكاشير", "Cashier") to (order.cashierName ?: "-"))
        order.deliveryUserName?.takeIf { it.isNotBlank() }?.let {
            add(L("السائق", "Delivery") to it)
        }
        order.doctorName?.let { add(L("الطبيب", "Doctor") to it) }
        order.diagnosis?.let { add(L("التشخيص", "Diagnosis") to it) }
    }

    val clientRows = buildList {
        order.clientName?.let { add(L("العميل", "Client") to it) }
        order.clientPhone?.let { add(L("الهاتف", "Phone") to it) }
        order.clientAddress?.let { add(L("العنوان", "Address") to it) }
    }

    val items = order.items.map { item ->
        val variant = net.marllex.waselak.core.ui.util.VariantDisplayHelper
            .formatVariantSummary(item.variantOptionsSnapshot) ?: ""
        val name = if (variant.isNotBlank()) "${item.itemNameSnapshot} $variant" else item.itemNameSnapshot
        ReceiptModel.ItemRow(
            name = name,
            qty = item.quantity.toString(),
            price = formatAmount(item.itemPriceSnapshot * item.quantity),
        )
    }

    val totalsRows = buildList {
        add(L("الإجمالي قبل", "Subtotal") to formatAmount(order.subtotal))
        // Discounts and points stack just under the subtotal — they
        // modify what the customer pays before fees + tax are added.
        if (order.discount > 0.0) {
            add("الخصم / Discount" to "-${formatAmount(order.discount)}")
            order.discountReason?.takeIf { it.isNotBlank() }?.let {
                add(L("سبب الخصم", "Discount Reason") to it)
            }
        }
        if (order.pointsEarned > 0) {
            add(L("النقاط المكتسبة", "Points Earned") to "+${order.pointsEarned}")
        }
        if (order.pointsRedeemed > 0) {
            add(L("النقاط المستبدلة", "Points Redeemed") to "-${order.pointsRedeemed}")
        }
        // Delivery fee — STRICTLY hidden when zero, regardless of channel.
        // Bilingual label so Arabic + English customers can both read it
        // (merchants in Egypt frequently print one paper for both).
        if (order.deliveryFee > 0.0) {
            add("رسوم التوصيل / Delivery Fee" to formatAmount(order.deliveryFee))
        }
        // Tax — gated on the CURRENT vendor configuration (not the
        // historical value baked into the order). If the dashboard
        // has tax off / 0%, the row vanishes even for old orders.
        val vendorHasTax = (vendor?.taxEnabled == true) && (vendor.defaultTaxPercent > 0.0)
        if (vendorHasTax && order.tax > 0.0) {
            val pct = if (order.taxPercent > 0.0) " (${order.taxPercent.toInt()}%)" else ""
            add("الضريبة / Tax$pct" to formatAmount(order.tax))
        }
    }

    val refundRows = if (order.hasReturns) {
        listOf(
            L("المرتجع", "Refunded") + " (${order.returnedItemCount})"
                to "-${formatAmount(order.refundedAmount)}",
        )
    } else emptyList()

    return ReceiptModel(
        isArabic = isAr,
        vendorName = vendor?.name ?: "Restaurant",
        vendorAddress = vendor?.address?.takeIf { it.isNotBlank() },
        vendorPhone = vendor?.contactPhone?.takeIf { it.isNotBlank() },
        // Wallet + WhatsApp dropped from the printed receipt per merchant
        // request. Kept as nullable fields so existing renderers / HTML
        // markup don't break — they're just always null now.
        vendorWallet = null,
        vendorWhatsapp = null,
        vendorLogoUrl = vendor?.logoUrl?.takeIf { it.isNotBlank() },
        orderIdLabel = orderIdLabel,
        orderIdValue = orderIdValue,
        orderRows = orderRows,
        clientRows = clientRows,
        items = items,
        totalsRows = totalsRows,
        grandTotalLabel = L("الإجمالي", "Total"),
        grandTotalValue = formatAmount(order.total),
        refundRows = refundRows,
        netTotalLabel = if (order.hasReturns) L("الصافي", "Net Total") else null,
        netTotalValue = if (order.hasReturns) formatAmount(order.netTotal) else null,
        notesLabel = order.notes?.takeIf { it.isNotBlank() }?.let { L("ملاحظات", "Notes") },
        notesText = order.notes?.takeIf { it.isNotBlank() },
        footerThanks = L(
            "شكراً لزيارتكم! نتشرف بخدمتكم مرة أخرى",
            "Thank you for your visit! You are welcome again",
        ),
    )
}

// ─── helpers (also used by the HTML builder, kept private here) ─────────────

private fun formatAmount(amount: Double, currency: String = "EGP"): String {
    val rounded = kotlin.math.round(amount * 100) / 100.0
    val whole = rounded.toLong()
    val frac = ((rounded - whole) * 100 + 0.5).toInt()
    return "$whole.${frac.toString().padStart(2, '0')} $currency"
}

private fun formatDate(epochMs: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMs)
    val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val h12 = if (dt.hour == 0) 12 else if (dt.hour > 12) dt.hour - 12 else dt.hour
    val amPm = if (dt.hour < 12) "AM" else "PM"
    return "${dt.year}-${dt.monthNumber.toString().padStart(2, '0')}-${
        dt.dayOfMonth.toString().padStart(2, '0')
    }  ${h12.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')} $amPm"
}

private fun channelLabel(channel: OrderChannel, isAr: Boolean): String = when (channel) {
    OrderChannel.DINE_IN      -> if (isAr) "صالة" else "Dine In"
    OrderChannel.DELIVERY     -> if (isAr) "توصيل" else "Delivery"
    OrderChannel.TAKEAWAY     -> if (isAr) "تيك أواي" else "Takeaway"
    OrderChannel.IN_STORE     -> if (isAr) "داخل المحل" else "In-Store"
    OrderChannel.PICKUP_LATER -> if (isAr) "استلام لاحقاً" else "Pickup Later"
}

private fun paymentLabel(m: PaymentMethod, isAr: Boolean) = when (m) {
    PaymentMethod.CASH   -> if (isAr) "كاش" else "Cash"
    PaymentMethod.WALLET -> if (isAr) "محفظة" else "Wallet"
    PaymentMethod.CARD   -> if (isAr) "بطاقة" else "Card"
    PaymentMethod.SPLIT  -> if (isAr) "مقسوم" else "Split"
    PaymentMethod.CREDIT -> if (isAr) "آجل" else "Credit"
}

private fun paymentStatusLabel(s: PaymentStatus, isAr: Boolean) = when (s) {
    PaymentStatus.PENDING        -> if (isAr) "في الانتظار" else "Pending"
    PaymentStatus.PAID           -> if (isAr) "مدفوع" else "Paid"
    PaymentStatus.PARTIALLY_PAID -> if (isAr) "مدفوع جزئياً" else "Partially Paid"
    PaymentStatus.REFUNDED       -> if (isAr) "تم الاسترجاع" else "Refunded"
    PaymentStatus.FAILED         -> if (isAr) "فشل" else "Failed"
}

private fun paymentTimingLabel(t: PaymentTiming, isAr: Boolean) = when (t) {
    PaymentTiming.PAY_NOW   -> if (isAr) "الآن" else "Pay Now"
    PaymentTiming.PAY_LATER -> if (isAr) "لاحقاً" else "Pay Later"
}

private fun statusLabel(s: OrderStatus, isAr: Boolean) = when (s) {
    OrderStatus.CREATED          -> if (isAr) "تم الإنشاء" else "Created"
    OrderStatus.IN_PROGRESS      -> if (isAr) "جارٍ التحضير" else "In Progress"
    OrderStatus.IN_PREPARATION   -> if (isAr) "تحت التحضير" else "In Preparation"
    OrderStatus.READY            -> if (isAr) "جاهز" else "Ready"
    OrderStatus.SERVED           -> if (isAr) "تم التقديم" else "Served"
    OrderStatus.ASSIGNED         -> if (isAr) "تم التعيين" else "Assigned"
    OrderStatus.OUT_FOR_DELIVERY -> if (isAr) "في الطريق" else "Out for Delivery"
    OrderStatus.DELIVERED        -> if (isAr) "تم التوصيل" else "Delivered"
    OrderStatus.DELIVERY_FAILED  -> if (isAr) "فشل التوصيل" else "Delivery Failed"
    OrderStatus.RETURNED         -> if (isAr) "مرتجع" else "Returned"
    OrderStatus.PICKED_UP        -> if (isAr) "تم الاستلام" else "Picked Up"
    OrderStatus.COMPLETED        -> if (isAr) "مكتمل" else "Completed"
    OrderStatus.CANCELED         -> if (isAr) "ملغي" else "Canceled"
    OrderStatus.REFUNDED         -> if (isAr) "مرتجع كلياً" else "Refunded"
}
