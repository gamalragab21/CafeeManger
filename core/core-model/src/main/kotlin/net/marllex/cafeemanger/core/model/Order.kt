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
    val cashierName: String? = null,
    val deliveryUserId: String? = null,
    val deliveryUserName: String? = null,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val clientAddress: String? = null,
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val paymentMethod: PaymentMethod,
    val subtotal: Double,
    val deliveryFee: Double = 0.0,
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
    IN_PREPARATION,
    READY,            // ready to handoff / assign
    ASSIGNED,         // delivery assigned
    OUT_FOR_DELIVERY, // on the way
    DELIVERED,        // delivered to customer
    COMPLETED,        // final settlement done
    CANCELED;

    fun canTransitionTo(newStatus: OrderStatus, channel: OrderChannel): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(IN_PREPARATION, CANCELED)
            IN_PREPARATION -> newStatus in listOf(READY, CANCELED)
            READY -> newStatus in listOf(ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, COMPLETED, CANCELED)
            ASSIGNED -> newStatus in listOf(OUT_FOR_DELIVERY, DELIVERED, COMPLETED, CANCELED)
            OUT_FOR_DELIVERY -> newStatus in listOf(DELIVERED, COMPLETED, CANCELED)
            DELIVERED -> newStatus in listOf(COMPLETED, CANCELED)
            COMPLETED -> false
            CANCELED -> false
        }
    }

    companion object {
        fun getAvailableStatuses(channel: OrderChannel): List<OrderStatus> {
            return listOf(
                CREATED,
                IN_PREPARATION,
                READY,
                ASSIGNED,
                OUT_FOR_DELIVERY,
                DELIVERED,
                COMPLETED,
                CANCELED
            )
        }
    }
}

@Serializable
enum class PaymentMethod {
    CASH,
    WALLET,
    CARD
}

data class ReceiptShareLink(
    val url: String,
    val token: String,
    val expiresAt: Long
)
