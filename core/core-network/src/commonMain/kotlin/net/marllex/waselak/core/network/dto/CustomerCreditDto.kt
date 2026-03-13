package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerCreditResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    val balance: Double = 0.0,
    @SerialName("credit_limit") val creditLimit: Double = 500.0,
    @SerialName("available_credit") val availableCredit: Double = 500.0,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class CreditTransactionResponse(
    val id: String,
    @SerialName("credit_id") val creditId: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("order_id") val orderId: String? = null,
    val type: String,
    val amount: Double,
    @SerialName("previous_balance") val previousBalance: Double,
    @SerialName("new_balance") val newBalance: Double,
    val note: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class SetCreditLimitRequest(
    @SerialName("credit_limit") val creditLimit: Double,
)

@Serializable
data class CreditChargeRequest(
    val amount: Double,
    @SerialName("order_id") val orderId: String? = null,
    val note: String? = null,
)

@Serializable
data class CreditPaymentRequest(
    val amount: Double,
    val note: String? = null,
)
