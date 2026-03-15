package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CashDrawerSessionResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("cashier_id") val cashierId: String,
    @SerialName("cashier_name") val cashierName: String? = null,
    @SerialName("opened_at") val openedAt: Long,
    @SerialName("closed_at") val closedAt: Long? = null,
    @SerialName("opening_balance") val openingBalance: Double = 0.0,
    @SerialName("closing_balance") val closingBalance: Double? = null,
    @SerialName("expected_balance") val expectedBalance: Double? = null,
    val difference: Double? = null,
    val status: String = "OPEN",
    val notes: String? = null,
    val movements: List<CashMovementResponse> = emptyList(),
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class CashMovementResponse(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("vendor_id") val vendorId: String,
    val type: String,
    val amount: Double,
    val reason: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("created_by") val createdBy: String,
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("created_at") val createdAt: Long,
)

@Serializable
data class OpenDrawerRequest(
    @SerialName("opening_balance") val openingBalance: Double = 0.0,
    val notes: String? = null,
)

@Serializable
data class CloseDrawerRequest(
    @SerialName("closing_balance") val closingBalance: Double,
    val notes: String? = null,
)

@Serializable
data class CreateCashMovementRequest(
    val type: String,
    val amount: Double,
    val reason: String? = null,
    @SerialName("order_id") val orderId: String? = null,
)

@Serializable
data class DrawerSummaryResponse(
    @SerialName("session_id") val sessionId: String,
    @SerialName("opening_balance") val openingBalance: Double = 0.0,
    @SerialName("total_cash_in") val totalCashIn: Double = 0.0,
    @SerialName("total_cash_out") val totalCashOut: Double = 0.0,
    @SerialName("total_sales") val totalSales: Double = 0.0,
    @SerialName("total_refunds") val totalRefunds: Double = 0.0,
    @SerialName("expected_balance") val expectedBalance: Double = 0.0,
    @SerialName("movement_count") val movementCount: Int = 0,
)
