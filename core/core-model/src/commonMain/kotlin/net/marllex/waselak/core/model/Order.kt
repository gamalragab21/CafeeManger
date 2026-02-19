package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Order(
    val id: String,
    val vendorId: String,
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
    DELIVERY,
    TAKEAWAY
}

@Serializable
enum class OrderStatus {
    CREATED,
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
    CANCELED;

    fun canTransitionTo(newStatus: OrderStatus, channel: OrderChannel): Boolean {
        return when (channel) {
            OrderChannel.DINE_IN -> canTransitionDineIn(newStatus)
            OrderChannel.DELIVERY -> canTransitionDelivery(newStatus)
            OrderChannel.TAKEAWAY -> canTransitionTakeaway(newStatus)
        }
    }

    private fun canTransitionDineIn(newStatus: OrderStatus): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(IN_PREPARATION, CANCELED)
            IN_PREPARATION -> newStatus in listOf(READY, CANCELED)
            READY -> newStatus in listOf(SERVED, CANCELED)
            SERVED -> newStatus in listOf(COMPLETED, CANCELED)
            COMPLETED -> false
            CANCELED -> false
            else -> false
        }
    }

    private fun canTransitionDelivery(newStatus: OrderStatus): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(IN_PREPARATION, CANCELED)
            IN_PREPARATION -> newStatus in listOf(READY, CANCELED)
            READY -> newStatus in listOf(ASSIGNED, CANCELED)
            ASSIGNED -> newStatus in listOf(OUT_FOR_DELIVERY, CANCELED)
            OUT_FOR_DELIVERY -> newStatus in listOf(DELIVERED, DELIVERY_FAILED, CANCELED)
            DELIVERY_FAILED -> newStatus in listOf(RETURNED, ASSIGNED, CANCELED)
            DELIVERED -> newStatus in listOf(COMPLETED, CANCELED)
            RETURNED -> false // terminal
            COMPLETED -> false
            CANCELED -> false
            else -> false
        }
    }

    private fun canTransitionTakeaway(newStatus: OrderStatus): Boolean {
        return when (this) {
            CREATED -> newStatus in listOf(IN_PREPARATION, CANCELED)
            IN_PREPARATION -> newStatus in listOf(READY, CANCELED)
            READY -> newStatus in listOf(PICKED_UP, CANCELED)
            PICKED_UP -> newStatus in listOf(COMPLETED, CANCELED)
            COMPLETED -> false
            CANCELED -> false
            else -> false
        }
    }

    companion object {
        fun getAvailableStatuses(channel: OrderChannel): List<OrderStatus> {
            return when (channel) {
                OrderChannel.DINE_IN -> listOf(CREATED, IN_PREPARATION, READY, SERVED, COMPLETED, CANCELED)
                OrderChannel.DELIVERY -> listOf(CREATED, IN_PREPARATION, READY, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, DELIVERY_FAILED, RETURNED, COMPLETED, CANCELED)
                OrderChannel.TAKEAWAY -> listOf(CREATED, IN_PREPARATION, READY, PICKED_UP, COMPLETED, CANCELED)
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

data class ReceiptShareLink(
    val url: String,
    val token: String,
    val expiresAt: Long
)
