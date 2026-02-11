package net.marllex.cafeemanger.core.model

data class DeliveryPersonStatus(
    val userId: String,
    val name: String,
    val phone: String,
    val status: DeliveryAvailability,
    val activeOrderCount: Int,
    val activeOrders: List<DeliveryOrderSummary>,
)

enum class DeliveryAvailability {
    AVAILABLE, BUSY
}

data class DeliveryOrderSummary(
    val orderId: String,
    val status: OrderStatus,
    val clientName: String?,
    val clientAddress: String?,
    val total: Double,
    val createdAt: Long,
)
