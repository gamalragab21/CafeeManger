package net.marllex.cafeemanger.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,
    val vendorId: String,
    val channel: OrderChannel,
    val status: OrderStatus,
    val tableId: String? = null,
    val cashierId: String,
    val deliveryUserId: String? = null,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val clientAddress: String? = null,
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val paymentMethod: PaymentMethod,
    val subtotal: Double,
    val tax: Double = 0.0,
    val total: Double,
    val notes: String? = null,
    val items: List<OrderItem> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long? = null
)

@Serializable
enum class OrderChannel {
    DINE_IN,
    DELIVERY
}

@Serializable
enum class OrderStatus {
    CREATED,
    CONFIRMED,
    IN_PREPARATION,
    SERVED,           // Dine-in only
    READY,            // Delivery only
    ASSIGNED,         // Delivery only
    OUT_FOR_DELIVERY, // Delivery only
    DELIVERED,        // Delivery only
    COMPLETED,
    CANCELED;

    fun canTransitionTo(newStatus: OrderStatus, channel: OrderChannel): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(CONFIRMED, CANCELED)
            CONFIRMED -> newStatus in listOf(IN_PREPARATION, CANCELED)
            IN_PREPARATION -> when (channel) {
                OrderChannel.DINE_IN -> newStatus in listOf(SERVED, CANCELED)
                OrderChannel.DELIVERY -> newStatus in listOf(READY, CANCELED)
            }
            SERVED -> newStatus in listOf(COMPLETED, CANCELED)
            READY -> newStatus in listOf(ASSIGNED, CANCELED)
            ASSIGNED -> newStatus in listOf(OUT_FOR_DELIVERY, CANCELED)
            OUT_FOR_DELIVERY -> newStatus in listOf(DELIVERED, CANCELED)
            DELIVERED -> newStatus == COMPLETED
            COMPLETED -> false
            CANCELED -> false
        }
    }

    companion object {
        fun getAvailableStatuses(channel: OrderChannel): List<OrderStatus> {
            return when (channel) {
                OrderChannel.DINE_IN -> listOf(
                    CREATED, CONFIRMED, IN_PREPARATION, SERVED, COMPLETED, CANCELED
                )
                OrderChannel.DELIVERY -> listOf(
                    CREATED, CONFIRMED, IN_PREPARATION, READY,
                    ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, COMPLETED, CANCELED
                )
            }
        }
    }
}

@Serializable
enum class PaymentMethod {
    CASH,
    WALLET,
    CARD
}
