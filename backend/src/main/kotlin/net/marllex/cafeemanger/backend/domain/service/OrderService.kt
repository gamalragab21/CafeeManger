package net.marllex.cafeemanger.backend.domain.service

class OrderService {

    enum class OrderChannel { DINE_IN, DELIVERY }
    enum class OrderStatus {
        CREATED, IN_PREPARATION, READY, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, COMPLETED, CANCELED
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

        return when (current) {
            OrderStatus.CREATED -> next in listOf(OrderStatus.IN_PREPARATION, OrderStatus.CANCELED)
            OrderStatus.IN_PREPARATION -> next in listOf(OrderStatus.READY, OrderStatus.CANCELED)
            OrderStatus.READY -> next in listOf(OrderStatus.ASSIGNED, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.ASSIGNED -> next in listOf(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.OUT_FOR_DELIVERY -> next in listOf(OrderStatus.DELIVERED, OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.DELIVERED -> next in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.COMPLETED -> false
            OrderStatus.CANCELED -> false
        }
    }
}
