package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Supplier & Purchase Order domain models.
 * Manages vendor supply chain: suppliers, purchase orders, stock receiving.
 */

@Serializable
data class Supplier(
    val id: String,
    val vendorId: String,
    val name: String,
    val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class PurchaseOrder(
    val id: String,
    val vendorId: String,
    val supplierId: String,
    val supplierName: String? = null,
    val orderNumber: String,
    val status: String = "DRAFT",    // DRAFT, SUBMITTED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED
    val notes: String? = null,
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    val expectedDeliveryDate: String? = null,
    val receivedAt: Long? = null,
    val createdBy: String,
    val items: List<PurchaseOrderItem> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isDraft: Boolean get() = status == "DRAFT"
    val isSubmitted: Boolean get() = status == "SUBMITTED"
    val isPartiallyReceived: Boolean get() = status == "PARTIALLY_RECEIVED"
    val isReceived: Boolean get() = status == "RECEIVED"
    val isCancelled: Boolean get() = status == "CANCELLED"
    val canReceive: Boolean get() = status in listOf("SUBMITTED", "PARTIALLY_RECEIVED")
    val itemCount: Int get() = items.size
}

@Serializable
data class PurchaseOrderItem(
    val id: String,
    val purchaseOrderId: String,
    val stockId: String,
    val stockName: String? = null,
    val requestedQuantity: Double,
    val receivedQuantity: Double = 0.0,
    val unitCost: Double = 0.0,
    val totalCost: Double = 0.0,
    val unit: String = "PIECE",
    val notes: String? = null,
    val createdAt: Long,
) {
    val isFullyReceived: Boolean get() = receivedQuantity >= requestedQuantity
    val pendingQuantity: Double get() = (requestedQuantity - receivedQuantity).coerceAtLeast(0.0)
    val receivedPercent: Double get() = if (requestedQuantity > 0) (receivedQuantity / requestedQuantity) * 100 else 0.0
}
