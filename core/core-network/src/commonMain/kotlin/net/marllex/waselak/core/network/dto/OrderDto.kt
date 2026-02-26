package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val channel: String,
    val status: String,
    @SerialName("table_id") val tableId: String? = null,
    @SerialName("table_number") val tableNumber: String? = null,
    @SerialName("cashier_id") val cashierId: String,
    @SerialName("cashier_name") val cashierName: String? = null,
    @SerialName("delivery_user_id") val deliveryUserId: String? = null,
    @SerialName("delivery_user_name") val deliveryUserName: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_phone") val clientPhone: String? = null,
    @SerialName("client_address") val clientAddress: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("geo_lat") val geoLat: Double? = null,
    @SerialName("geo_lng") val geoLng: Double? = null,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("payment_status") val paymentStatus: String = "PENDING",
    @SerialName("payment_timing") val paymentTiming: String = "PAY_NOW",
    @SerialName("payment_confirmed_at") val paymentConfirmedAt: Long? = null,
    @SerialName("payment_confirmed_by") val paymentConfirmedBy: String? = null,
    val subtotal: Double,
    @SerialName("delivery_fee") val deliveryFee: Double = 0.0,
    val discount: Double = 0.0,
    @SerialName("discount_type") val discountType: String = "FIXED",
    val tax: Double = 0.0,
    @SerialName("tax_percent") val taxPercent: Double = 0.0,
    val total: Double,
    val notes: String? = null,
    val items: List<OrderItemResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long? = null
)

@Serializable
data class OrderItemResponse(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("item_name_snapshot") val itemNameSnapshot: String,
    @SerialName("item_price_snapshot") val itemPriceSnapshot: Double,
    val quantity: Int,
    val note: String? = null
)

@Serializable
data class PaginatedOrdersResponse(
    val orders: List<OrderResponse>,
    val total: Int,
    @SerialName("has_more") val hasMore: Boolean
)

@Serializable
data class CreateOrderRequest(
    val channel: String,
    @SerialName("table_id") val tableId: String? = null,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_phone") val clientPhone: String? = null,
    @SerialName("client_address") val clientAddress: String? = null,
    @SerialName("customer_id") val customerId: String? = null,
    @SerialName("geo_lat") val geoLat: Double? = null,
    @SerialName("geo_lng") val geoLng: Double? = null,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("payment_timing") val paymentTiming: String = "PAY_NOW",
    @SerialName("delivery_fee") val deliveryFee: Double = 0.0,
    @SerialName("tax_place_id") val taxPlaceId: String? = null,
    val notes: String? = null,
    val items: List<CreateOrderItemRequest>
)

@Serializable
data class CreateOrderItemRequest(
    @SerialName("item_id") val itemId: String,
    val quantity: Int,
    val note: String? = null
)

@Serializable
data class UpdateOrderRequest(
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_phone") val clientPhone: String? = null,
    @SerialName("client_address") val clientAddress: String? = null,
    val notes: String? = null,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("delivery_fee") val deliveryFee: Double? = null,
    @SerialName("tax_place_id") val taxPlaceId: String? = null,
    val items: List<CreateOrderItemRequest>? = null,
)

@Serializable
data class UpdateOrderStatusRequest(
    val status: String
)

@Serializable
data class UpdatePaymentStatusRequest(
    @SerialName("payment_status") val paymentStatus: String,
    @SerialName("payment_method") val paymentMethod: String? = null,
)

@Serializable
data class AssignDeliveryRequest(
    @SerialName("delivery_user_id") val deliveryUserId: String
)

@Serializable
data class ShareReceiptResponse(
    val url: String,
    val token: String,
    @SerialName("expires_at") val expiresAt: Long
)

@Serializable
data class DeliveryDashboardItemResponse(
    @SerialName("delivery_user_id") val deliveryUserId: String,
    @SerialName("delivery_user_name") val deliveryUserName: String,
    @SerialName("delivery_user_phone") val deliveryUserPhone: String,
    val status: String,
    @SerialName("active_order_count") val activeOrderCount: Int,
    @SerialName("active_orders") val activeOrders: List<DeliveryOrderSummaryResponse>,
)

@Serializable
data class DeliveryOrderSummaryResponse(
    @SerialName("order_id") val orderId: String,
    val status: String,
    @SerialName("client_name") val clientName: String? = null,
    @SerialName("client_address") val clientAddress: String? = null,
    val total: Double,
    @SerialName("created_at") val createdAt: Long,
)
