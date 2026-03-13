package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Scheduled Order / Pre-Order domain models.
 * Allows customers to place orders for future pickup/delivery.
 */

@Serializable
data class ScheduledOrder(
    val id: String,
    val vendorId: String,
    val customerId: String? = null,
    val clientName: String? = null,
    val clientPhone: String? = null,
    val channel: String = "PICKUP_LATER",
    val scheduledFor: Long,
    val reminderSentAt: Long? = null,
    val status: String = "SCHEDULED",   // SCHEDULED, CONFIRMED, PREPARING, READY, COMPLETED, CANCELLED
    val notes: String? = null,
    val paymentMethod: String = "CASH",
    val paymentStatus: String = "PENDING",
    val subtotal: Double = 0.0,
    val total: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val orderId: String? = null,
    val createdBy: String,
    val items: List<ScheduledOrderItem> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isScheduled: Boolean get() = status == "SCHEDULED"
    val isConfirmed: Boolean get() = status == "CONFIRMED"
    val isPreparing: Boolean get() = status == "PREPARING"
    val isReady: Boolean get() = status == "READY"
    val isCompleted: Boolean get() = status == "COMPLETED"
    val isCancelled: Boolean get() = status == "CANCELLED"
    val isActive: Boolean get() = status in listOf("SCHEDULED", "CONFIRMED", "PREPARING", "READY")
    val isConverted: Boolean get() = orderId != null
    val itemCount: Int get() = items.sumOf { it.quantity }
}

@Serializable
data class ScheduledOrderItem(
    val id: String,
    val scheduledOrderId: String,
    val itemId: String,
    val itemName: String,
    val itemPrice: Double,
    val quantity: Int,
    val note: String? = null,
    val variantOptions: String? = null,
    val createdAt: Long,
)
