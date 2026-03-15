package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Supplier DTOs ─────────────────────────────────────────────

@Serializable
data class SupplierResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val name: String,
    @SerialName("contact_name") val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class CreateSupplierRequest(
    val name: String,
    @SerialName("contact_name") val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateSupplierRequest(
    val name: String? = null,
    @SerialName("contact_name") val contactName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val active: Boolean? = null,
)

// ─── Purchase Order DTOs ───────────────────────────────────────

@Serializable
data class PurchaseOrderResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("supplier_id") val supplierId: String,
    @SerialName("supplier_name") val supplierName: String? = null,
    @SerialName("order_number") val orderNumber: String,
    val status: String = "DRAFT",
    val notes: String? = null,
    val subtotal: Double = 0.0,
    val tax: Double = 0.0,
    val total: Double = 0.0,
    @SerialName("expected_delivery_date") val expectedDeliveryDate: String? = null,
    @SerialName("received_at") val receivedAt: Long? = null,
    @SerialName("created_by") val createdBy: String,
    val items: List<PurchaseOrderItemResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class PurchaseOrderItemResponse(
    val id: String,
    @SerialName("purchase_order_id") val purchaseOrderId: String,
    @SerialName("stock_id") val stockId: String,
    @SerialName("stock_name") val stockName: String? = null,
    @SerialName("requested_quantity") val requestedQuantity: Double,
    @SerialName("received_quantity") val receivedQuantity: Double = 0.0,
    @SerialName("unit_cost") val unitCost: Double = 0.0,
    @SerialName("total_cost") val totalCost: Double = 0.0,
    val unit: String = "PIECE",
    val notes: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreatePurchaseOrderRequest(
    @SerialName("supplier_id") val supplierId: String,
    val notes: String? = null,
    @SerialName("expected_delivery_date") val expectedDeliveryDate: String? = null,
    val items: List<CreatePurchaseOrderItemRequest>,
)

@Serializable
data class CreatePurchaseOrderItemRequest(
    @SerialName("stock_id") val stockId: String,
    @SerialName("requested_quantity") val requestedQuantity: Double,
    @SerialName("unit_cost") val unitCost: Double = 0.0,
    val unit: String = "PIECE",
    val notes: String? = null,
)

@Serializable
data class ReceivePurchaseOrderRequest(
    val items: List<ReceiveItemRequest>,
    val notes: String? = null,
)

@Serializable
data class ReceiveItemRequest(
    @SerialName("purchase_order_item_id") val purchaseOrderItemId: String,
    @SerialName("received_quantity") val receivedQuantity: Double,
    @SerialName("batch_number") val batchNumber: String? = null,
    @SerialName("expiry_date") val expiryDate: String? = null,
)
