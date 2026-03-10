package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.marllex.waselak.core.model.PointsTransaction

@Serializable
data class CustomerResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val name: String? = null,
    val phone: String,
    val notes: String? = null,
    @SerialName("order_count") val orderCount: Int = 0,
    @SerialName("total_spent") val totalSpent: Double = 0.0,
    @SerialName("points_balance") val pointsBalance: Int = 0,
    @SerialName("last_order_at") val lastOrderAt: Long? = null,
    val addresses: List<CustomerAddressResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class CustomerAddressResponse(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    val label: String? = null,
    val address: String,
    @SerialName("geo_lat") val geoLat: Double? = null,
    @SerialName("geo_lng") val geoLng: Double? = null,
    @SerialName("delivery_zone_id") val deliveryZoneId: String? = null,
    @SerialName("delivery_fee") val deliveryFee: Double? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreateCustomerRequest(
    val phone: String,
    val name: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateCustomerRequest(
    val name: String? = null,
    val phone: String? = null,
    val notes: String? = null,
)

@Serializable
data class CreateCustomerAddressRequest(
    val label: String? = null,
    val address: String,
    @SerialName("geo_lat") val geoLat: Double? = null,
    @SerialName("geo_lng") val geoLng: Double? = null,
    @SerialName("delivery_zone_id") val deliveryZoneId: String? = null,
    @SerialName("is_default") val isDefault: Boolean = false,
)

@Serializable
data class CustomerOrderHistoryResponse(
    val orders: List<OrderResponse>,
    val total: Int,
)

@Serializable
data class PointsTransactionResponse(
    val id: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("order_id") val orderId: String? = null,
    val type: String,
    val points: Int,
    val description: String? = null,
    @SerialName("created_at") val createdAt: Long,
) {
    fun toDomain() = PointsTransaction(
        id = id, customerId = customerId, vendorId = vendorId,
        orderId = orderId, type = type, points = points,
        description = description, createdAt = createdAt,
    )
}
