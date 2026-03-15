package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductReturnResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("return_type") val returnType: String = "RETURN",
    val status: String = "PENDING",
    val reason: String,
    @SerialName("refund_amount") val refundAmount: Double = 0.0,
    @SerialName("refund_method") val refundMethod: String? = null,
    @SerialName("processed_by") val processedBy: String? = null,
    @SerialName("processed_at") val processedAt: Long? = null,
    val notes: String? = null,
    val items: List<ReturnItemResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class ReturnItemResponse(
    val id: String,
    @SerialName("return_id") val returnId: String,
    @SerialName("order_item_id") val orderItemId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String? = null,
    val quantity: Int,
    val reason: String? = null,
    @SerialName("item_condition") val itemCondition: String = "GOOD",
    val restockable: Boolean = true,
    @SerialName("refund_amount") val refundAmount: Double = 0.0,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class CreateReturnRequest(
    @SerialName("order_id") val orderId: String,
    @SerialName("return_type") val returnType: String = "RETURN",
    val reason: String,
    @SerialName("refund_method") val refundMethod: String? = null,
    val notes: String? = null,
    val items: List<CreateReturnItemRequest>,
)

@Serializable
data class CreateReturnItemRequest(
    @SerialName("order_item_id") val orderItemId: String,
    val quantity: Int,
    val reason: String? = null,
    @SerialName("item_condition") val itemCondition: String = "GOOD",
    val restockable: Boolean = true,
)

@Serializable
data class ProcessReturnRequest(
    val status: String,
    val notes: String? = null,
)

@Serializable
data class ReturnsSummaryResponse(
    val total: Int = 0,
    val pending: Int = 0,
    val completed: Int = 0,
    val rejected: Int = 0,
    @SerialName("total_refunded") val totalRefunded: Double = 0.0,
)
