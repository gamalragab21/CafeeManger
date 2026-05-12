package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,
    val vendorId: String,
    /**
     * Per-vendor per-day order counter. Resets each calendar day. UI
     * surfaces this as the primary human-readable identifier (Order #1,
     * #2, #3…). UUID `id` is the canonical opaque identifier.
     */
    val dailySeq: Int = 0,
    val dailySeqDate: String = "",
    val channel: OrderChannel,
    val status: OrderStatus,
    val tableId: String? = null,
    val tableNumber: String? = null,
    val cashierId: String,
    val cashierName: String? = null,
    val deliveryUserId: String? = null,
    val deliveryUserName: String? = null,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val clientAddress: String? = null,
    val customerId: String? = null,
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val paymentMethod: PaymentMethod,
    val paymentStatus: PaymentStatus = PaymentStatus.PENDING,
    val paymentTiming: PaymentTiming = PaymentTiming.PAY_NOW,
    val paymentConfirmedAt: Long? = null,
    val paymentConfirmedBy: String? = null,
    val subtotal: Double,
    val deliveryFee: Double = 0.0,
    val discount: Double = 0.0,
    val discountType: String = "FIXED",
    val tax: Double = 0.0,
    val taxPercent: Double = 0.0,
    val total: Double,
    val notes: String? = null,
    val offerId: String? = null,
    val items: List<OrderItem> = emptyList(),
    val pointsEarned: Int = 0,
    val pointsRedeemed: Int = 0,
    val discountReason: String? = null,
    val createdAt: Long,
    val updatedAt: Long? = null,
    val refundedAt: Long? = null,
    val refundedBy: String? = null,
    val refundReason: String? = null,
    val refundedAmount: Double = 0.0,
    val returnedItemCount: Int = 0,
    val syncStatus: String = "SYNCED",
    val doctorName: String? = null,
    val diagnosis: String? = null,
) {
    val netTotal: Double get() = total - refundedAmount
    val hasReturns: Boolean get() = returnedItemCount > 0

    /**
     * The order's primary human-readable identifier as shown in the UI
     * and on the printed receipt. Prefers the per-day counter set by the
     * backend (`#1`, `#2`, `#3`...); falls back to the last 6 characters
     * of the UUID for legacy orders that pre-date the daily_seq column.
     */
    val displayId: String get() = if (dailySeq > 0) "#$dailySeq" else "#${id.takeLast(6).uppercase()}"
}

@Serializable
enum class OrderChannel {
    DINE_IN,
    DELIVERY,
    TAKEAWAY,
    IN_STORE,
    PICKUP_LATER
}

@Serializable
enum class OrderStatus {
    CREATED,
    IN_PROGRESS,
    IN_PREPARATION,
    READY,
    SERVED,              // dine-in: food served to table
    ASSIGNED,            // delivery: driver assigned
    OUT_FOR_DELIVERY,    // delivery: on the way
    DELIVERED,           // delivery: delivered to customer
    DELIVERY_FAILED,     // delivery: failed to reach customer
    RETURNED,            // delivery: order returned to store
    PICKED_UP,           // takeaway: customer picked up order
    COMPLETED,           // final settlement done
    CANCELED,
    REFUNDED;            // completed order was refunded

    fun canTransitionTo(newStatus: OrderStatus, channel: OrderChannel): Boolean {
        return when (channel) {
            OrderChannel.DINE_IN -> canTransitionDineIn(newStatus)
            OrderChannel.DELIVERY -> canTransitionDelivery(newStatus)
            OrderChannel.TAKEAWAY -> canTransitionTakeaway(newStatus)
            OrderChannel.IN_STORE -> canTransitionInStore(newStatus)
            OrderChannel.PICKUP_LATER -> canTransitionPickupLater(newStatus)
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Per-channel transitions.
    //
    // COMPLETED is now reachable directly from every pre-terminal status
    // (CREATED, IN_PREPARATION, READY, SERVED/DELIVERED/PICKED_UP). Some
    // merchants run a "fast counter" flow where the cashier doesn't need
    // to walk every order through prep → ready → served — they want one
    // tap to mark the order done. Mid-flow statuses still exist for the
    // merchants who DO use them; this just opens an extra direct edge.
    //
    // Backend OrderRoutes.kt enforces "payment must be PAID before
    // COMPLETED" so the direct-complete path can't bypass payment.
    // ────────────────────────────────────────────────────────────────

    private fun canTransitionDineIn(newStatus: OrderStatus): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(IN_PREPARATION, COMPLETED, CANCELED)
            IN_PREPARATION -> newStatus in listOf(READY, COMPLETED, CANCELED)
            READY -> newStatus in listOf(SERVED, COMPLETED, CANCELED)
            SERVED -> newStatus in listOf(COMPLETED, CANCELED)
            COMPLETED -> newStatus == REFUNDED
            CANCELED -> false
            REFUNDED -> false
            else -> false
        }
    }

    private fun canTransitionDelivery(newStatus: OrderStatus): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(IN_PREPARATION, COMPLETED, CANCELED)
            IN_PREPARATION -> newStatus in listOf(READY, COMPLETED, CANCELED)
            READY -> newStatus in listOf(ASSIGNED, COMPLETED, CANCELED)
            ASSIGNED -> newStatus in listOf(OUT_FOR_DELIVERY, COMPLETED, CANCELED)
            OUT_FOR_DELIVERY -> newStatus in listOf(DELIVERED, DELIVERY_FAILED, COMPLETED, CANCELED)
            DELIVERY_FAILED -> newStatus in listOf(RETURNED, ASSIGNED, CANCELED)
            DELIVERED -> newStatus in listOf(COMPLETED, CANCELED)
            RETURNED -> false // terminal
            COMPLETED -> newStatus == REFUNDED
            CANCELED -> false
            REFUNDED -> false
            else -> false
        }
    }

    private fun canTransitionTakeaway(newStatus: OrderStatus): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(IN_PREPARATION, COMPLETED, CANCELED)
            IN_PREPARATION -> newStatus in listOf(READY, COMPLETED, CANCELED)
            READY -> newStatus in listOf(PICKED_UP, COMPLETED, CANCELED)
            PICKED_UP -> newStatus in listOf(COMPLETED, CANCELED)
            COMPLETED -> newStatus == REFUNDED
            CANCELED -> false
            REFUNDED -> false
            else -> false
        }
    }

    private fun canTransitionInStore(newStatus: OrderStatus): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(COMPLETED, CANCELED)
            COMPLETED -> newStatus == REFUNDED
            CANCELED -> false
            REFUNDED -> false
            else -> false
        }
    }

    private fun canTransitionPickupLater(newStatus: OrderStatus): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(IN_PREPARATION, COMPLETED, CANCELED)
            IN_PREPARATION -> newStatus in listOf(READY, COMPLETED, CANCELED)
            READY -> newStatus in listOf(PICKED_UP, COMPLETED, CANCELED)
            PICKED_UP -> newStatus in listOf(COMPLETED, CANCELED)
            COMPLETED -> newStatus == REFUNDED
            CANCELED -> false
            REFUNDED -> false
            else -> false
        }
    }

    companion object {
        fun getAvailableStatuses(channel: OrderChannel): List<OrderStatus> {
            return when (channel) {
                OrderChannel.DINE_IN -> listOf(CREATED, IN_PREPARATION, READY, SERVED, COMPLETED, CANCELED, REFUNDED)
                OrderChannel.DELIVERY -> listOf(CREATED, IN_PREPARATION, READY, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, RETURNED, COMPLETED, CANCELED, REFUNDED)
                OrderChannel.TAKEAWAY -> listOf(CREATED, IN_PREPARATION, READY, PICKED_UP, COMPLETED, CANCELED, REFUNDED)
                OrderChannel.IN_STORE -> listOf(CREATED, COMPLETED, CANCELED, REFUNDED)
                OrderChannel.PICKUP_LATER -> listOf(CREATED, IN_PREPARATION, READY, PICKED_UP, COMPLETED, CANCELED, REFUNDED)
            }
        }

        /** Parse status string with legacy name support (ON_TABLE → SERVED). */
        fun parse(value: String): OrderStatus = when (value) {
            "ON_TABLE" -> SERVED
            else -> valueOf(value)
        }
    }
}

@Serializable
enum class PaymentMethod {
    CASH,
    WALLET,
    CARD,
    SPLIT,
    CREDIT,
}

@Serializable
enum class PaymentStatus {
    PENDING,
    PAID,
    PARTIALLY_PAID,
    REFUNDED,
    FAILED
}

@Serializable
enum class PaymentTiming {
    PAY_NOW,
    PAY_LATER
}

data class ReceiptShareLink(
    val url: String,
    val token: String,
    val expiresAt: Long
)
