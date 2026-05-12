package net.marllex.waselak.backend.domain.service

class OrderService {

    enum class OrderChannel { DINE_IN, DELIVERY, TAKEAWAY, IN_STORE, PICKUP_LATER }
    enum class OrderStatus {
        CREATED, IN_PREPARATION, READY,
        SERVED,              // dine-in: food served to table (renamed from ON_TABLE)
        ASSIGNED, OUT_FOR_DELIVERY, DELIVERED,
        DELIVERY_FAILED,     // delivery failed to reach customer
        RETURNED,            // order returned to store after failed delivery
        PICKED_UP,           // takeaway: customer picked up order
        COMPLETED, CANCELED, REFUNDED
    }

    fun validateStatusTransition(
        currentStatus: String,
        newStatus: String,
        channel: String,
        userRole: String
    ): Boolean {
        val current = OrderStatus.valueOf(currentStatus)
        val next = OrderStatus.valueOf(newStatus)

        // Manager can override any status
        if (userRole == "MANAGER") return true

        return when (channel) {
            "DINE_IN" -> validateDineIn(current, next)
            "DELIVERY" -> validateDelivery(current, next)
            "TAKEAWAY" -> validateTakeaway(current, next)
            "IN_STORE" -> validateInStore(current, next)
            "PICKUP_LATER" -> validatePickupLater(current, next)
            else -> false
        }
    }

    // ────────────────────────────────────────────────────────────────
    // Per-channel transition tables.
    //
    // COMPLETED is reachable directly from every pre-terminal status —
    // some merchants run "fast counter" sales where the cashier wants
    // one tap to close an order without walking it through prep → ready
    // → served. The OrderRoutes patch handler still blocks COMPLETED
    // when paymentStatus != PAID, so this shortcut can't bypass payment.
    // ────────────────────────────────────────────────────────────────

    private fun validateTakeaway(current: OrderStatus, next: OrderStatus): Boolean {
        return when (current) {
            OrderStatus.CREATED -> next in listOf(OrderStatus.IN_PREPARATION, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.IN_PREPARATION -> next in listOf(OrderStatus.READY, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.READY -> next in listOf(OrderStatus.PICKED_UP, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.PICKED_UP -> next in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.COMPLETED -> next == OrderStatus.REFUNDED
            OrderStatus.CANCELED -> false
            OrderStatus.REFUNDED -> false
            else -> false
        }
    }

    private fun validateDineIn(current: OrderStatus, next: OrderStatus): Boolean {
        return when (current) {
            OrderStatus.CREATED -> next in listOf(OrderStatus.IN_PREPARATION, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.IN_PREPARATION -> next in listOf(OrderStatus.READY, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.READY -> next in listOf(OrderStatus.SERVED, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.SERVED -> next in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.COMPLETED -> next == OrderStatus.REFUNDED
            OrderStatus.CANCELED -> false
            OrderStatus.REFUNDED -> false
            else -> false
        }
    }

    private fun validateInStore(current: OrderStatus, next: OrderStatus): Boolean {
        return when (current) {
            OrderStatus.CREATED -> next in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.COMPLETED -> next == OrderStatus.REFUNDED
            OrderStatus.CANCELED -> false
            OrderStatus.REFUNDED -> false
            else -> false
        }
    }

    private fun validatePickupLater(current: OrderStatus, next: OrderStatus): Boolean {
        return when (current) {
            OrderStatus.CREATED -> next in listOf(OrderStatus.IN_PREPARATION, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.IN_PREPARATION -> next in listOf(OrderStatus.READY, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.READY -> next in listOf(OrderStatus.PICKED_UP, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.PICKED_UP -> next in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.COMPLETED -> next == OrderStatus.REFUNDED
            OrderStatus.CANCELED -> false
            OrderStatus.REFUNDED -> false
            else -> false
        }
    }

    private fun validateDelivery(current: OrderStatus, next: OrderStatus): Boolean {
        return when (current) {
            OrderStatus.CREATED -> next in listOf(OrderStatus.IN_PREPARATION, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.IN_PREPARATION -> next in listOf(OrderStatus.READY, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.READY -> next in listOf(OrderStatus.ASSIGNED, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.ASSIGNED -> next in listOf(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.OUT_FOR_DELIVERY -> next in listOf(OrderStatus.DELIVERED, OrderStatus.DELIVERY_FAILED, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.DELIVERY_FAILED -> next in listOf(OrderStatus.RETURNED, OrderStatus.ASSIGNED, OrderStatus.CANCELED)
            OrderStatus.DELIVERED -> next in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.RETURNED -> false // terminal
            OrderStatus.COMPLETED -> next == OrderStatus.REFUNDED
            OrderStatus.CANCELED -> false
            OrderStatus.REFUNDED -> false
            else -> false
        }
    }
}
