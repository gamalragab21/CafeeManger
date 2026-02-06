package net.marllex.cafeemanger.backend.domain.service

class OrderService {

    enum class OrderChannel { DINE_IN, DELIVERY }
    enum class OrderStatus {
        CREATED, CONFIRMED, IN_PREPARATION, SERVED, READY,
        ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, COMPLETED, CANCELED
    }

    fun validateStatusTransition(
        currentStatus: String,
        newStatus: String,
        channel: String,
        userRole: String
    ): Boolean {
        val current = OrderStatus.valueOf(currentStatus)
        val next = OrderStatus.valueOf(newStatus)
        val orderChannel = OrderChannel.valueOf(channel)

        // Manager can override any status
        if (userRole == "MANAGER") return true

        return when (current) {
            OrderStatus.CREATED -> next in listOf(OrderStatus.CONFIRMED, OrderStatus.CANCELED)
            OrderStatus.CONFIRMED -> next in listOf(OrderStatus.IN_PREPARATION, OrderStatus.CANCELED)
            OrderStatus.IN_PREPARATION -> when (orderChannel) {
                OrderChannel.DINE_IN -> next in listOf(OrderStatus.SERVED, OrderStatus.CANCELED)
                OrderChannel.DELIVERY -> next in listOf(OrderStatus.READY, OrderStatus.CANCELED)
            }
            OrderStatus.SERVED -> next in listOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)
            OrderStatus.READY -> next in listOf(OrderStatus.ASSIGNED, OrderStatus.CANCELED)
            OrderStatus.ASSIGNED -> {
                if (userRole == "DELIVERY") {
                    next in listOf(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CANCELED)
                } else false
            }
            OrderStatus.OUT_FOR_DELIVERY -> {
                if (userRole == "DELIVERY") {
                    next in listOf(OrderStatus.DELIVERED, OrderStatus.CANCELED)
                } else false
            }
            OrderStatus.DELIVERED -> next == OrderStatus.COMPLETED
            OrderStatus.COMPLETED -> false
            OrderStatus.CANCELED -> false
        }
    }
}
