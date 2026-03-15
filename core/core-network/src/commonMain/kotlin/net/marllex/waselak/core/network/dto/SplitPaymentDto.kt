package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OrderPaymentResponse(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("payment_method") val paymentMethod: String,
    val amount: Double,
    @SerialName("paid_by") val paidBy: String? = null,
    @SerialName("paid_by_name") val paidByName: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CreateOrderPaymentRequest(
    @SerialName("payment_method") val paymentMethod: String,
    val amount: Double,
    val note: String? = null,
)

@Serializable
data class SplitPaymentSummaryResponse(
    @SerialName("order_id") val orderId: String,
    @SerialName("order_total") val orderTotal: Double,
    @SerialName("total_paid") val totalPaid: Double,
    val remaining: Double,
    @SerialName("is_fully_paid") val isFullyPaid: Boolean,
    val payments: List<OrderPaymentResponse> = emptyList(),
)
