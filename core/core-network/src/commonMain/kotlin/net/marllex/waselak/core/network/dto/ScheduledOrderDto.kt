package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ScheduledOrderResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_phone") val clientPhone: String? = null,
    val channel: String = "PICKUP_LATER",
    @SerialName("scheduled_for") val scheduledFor: Long,
    @SerialName("reminder_sent_at") val reminderSentAt: Long? = null,
    val status: String = "SCHEDULED",
    val notes: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "CASH",
    @SerialName("payment_status") val paymentStatus: String = "PENDING",
    val subtotal: Double = 0.0,
    val total: Double = 0.0,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    @SerialName("delivery_fee") val deliveryFee: Double = 0.0,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("created_by") val createdBy: String,
    val items: List<ScheduledOrderItemResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class ScheduledOrderItemResponse(
    val id: String,
    @SerialName("scheduled_order_id") val scheduledOrderId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("item_price") val itemPrice: Double,
    val quantity: Int,
    val note: String? = null,
    @SerialName("variant_options") val variantOptions: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreateScheduledOrderRequest(
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_phone") val clientPhone: String? = null,
    val channel: String = "PICKUP_LATER",
    @SerialName("scheduled_for") val scheduledFor: Long,
    val notes: String? = null,
    @SerialName("payment_method") val paymentMethod: String = "CASH",
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    @SerialName("delivery_fee") val deliveryFee: Double = 0.0,
    val items: List<CreateScheduledOrderItemRequest>,
)

@Serializable
data class CreateScheduledOrderItemRequest(
    @SerialName("item_id") val itemId: String,
    val quantity: Int,
    val note: String? = null,
    @SerialName("variant_options") val variantOptions: String? = null,
)

@Serializable
data class UpdateScheduledOrderStatusRequest(
    val status: String,
    val notes: String? = null,
)
