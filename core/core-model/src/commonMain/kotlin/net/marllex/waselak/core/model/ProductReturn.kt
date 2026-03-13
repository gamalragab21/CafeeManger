package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductReturn(
    val id: String,
    val vendorId: String,
    val orderId: String,
    val customerId: String? = null,
    val returnType: String = "RETURN",      // RETURN, EXCHANGE
    val status: String = "PENDING",         // PENDING, APPROVED, REJECTED, COMPLETED
    val reason: String,
    val refundAmount: Double = 0.0,
    val refundMethod: String? = null,
    val processedBy: String? = null,
    val processedAt: Long? = null,
    val notes: String? = null,
    val items: List<ReturnItem> = emptyList(),
    val createdAt: Long? = null,
) {
    val isPending: Boolean get() = status == "PENDING"
    val isCompleted: Boolean get() = status == "COMPLETED"
    val isRejected: Boolean get() = status == "REJECTED"
    val isExchange: Boolean get() = returnType == "EXCHANGE"
}

@Serializable
data class ReturnItem(
    val id: String,
    val returnId: String,
    val orderItemId: String,
    val itemId: String,
    val itemName: String? = null,
    val quantity: Int,
    val reason: String? = null,             // DEFECTIVE, WRONG_ITEM, CHANGED_MIND, EXPIRED, OTHER
    val itemCondition: String = "GOOD",     // GOOD, DAMAGED, EXPIRED
    val restockable: Boolean = true,
    val refundAmount: Double = 0.0,
    val createdAt: Long? = null,
)

@Serializable
data class ReturnsSummary(
    val total: Int = 0,
    val pending: Int = 0,
    val completed: Int = 0,
    val rejected: Int = 0,
    val totalRefunded: Double = 0.0,
)
