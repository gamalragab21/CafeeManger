package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Cash Drawer Management domain models.
 * Tracks shift-based cash sessions with opening/closing balances and movements.
 */

@Serializable
data class CashDrawerSession(
    val id: String,
    val vendorId: String,
    val cashierId: String,
    val cashierName: String? = null,
    val openedAt: Long,
    val closedAt: Long? = null,
    val openingBalance: Double = 0.0,
    val closingBalance: Double? = null,
    val expectedBalance: Double? = null,
    val difference: Double? = null,
    val status: String = "OPEN",       // OPEN, CLOSED
    val notes: String? = null,
    val movements: List<CashMovement> = emptyList(),
    val createdAt: Long,
) {
    val isOpen: Boolean get() = status == "OPEN"
    val isClosed: Boolean get() = status == "CLOSED"
    val hasDiscrepancy: Boolean get() = difference != null && kotlin.math.abs(difference) > 0.01
    val discrepancyAmount: Double get() = difference ?: 0.0
}

@Serializable
data class CashMovement(
    val id: String,
    val sessionId: String,
    val vendorId: String,
    val type: String,         // CASH_IN, CASH_OUT, SALE, REFUND, ADJUSTMENT
    val amount: Double,
    val reason: String? = null,
    val orderId: String? = null,
    val createdBy: String,
    val createdByName: String? = null,
    val createdAt: Long,
) {
    val isCashIn: Boolean get() = type == "CASH_IN"
    val isCashOut: Boolean get() = type == "CASH_OUT"
    val isSale: Boolean get() = type == "SALE"
    val isRefund: Boolean get() = type == "REFUND"
    val isAdjustment: Boolean get() = type == "ADJUSTMENT"
    val isInstallmentPayment: Boolean get() = type == "INSTALLMENT_PAYMENT"
}

@Serializable
data class DrawerSummary(
    val sessionId: String,
    val openingBalance: Double = 0.0,
    val totalCashIn: Double = 0.0,
    val totalCashOut: Double = 0.0,
    val totalSales: Double = 0.0,
    val totalRefunds: Double = 0.0,
    val expectedBalance: Double = 0.0,
    val movementCount: Int = 0,
    val totalOrders: Int = 0,
    // Payment method breakdown
    val cashSales: Double = 0.0,
    val cardSales: Double = 0.0,
    val walletSales: Double = 0.0,
    val creditSales: Double = 0.0,
    val cashOrderCount: Int = 0,
    val cardOrderCount: Int = 0,
    val walletOrderCount: Int = 0,
    val creditOrderCount: Int = 0,
    // Installment payments collected in this session
    val installmentPayments: Double = 0.0,
    val installmentPaymentCount: Int = 0,
    // Channel breakdown
    val channels: List<ChannelSummary> = emptyList(),
)

@Serializable
data class ChannelSummary(
    val channel: String,
    val orderCount: Int = 0,
    val totalAmount: Double = 0.0,
)

enum class CashMovementType {
    CASH_IN, CASH_OUT, SALE, REFUND, ADJUSTMENT, INSTALLMENT_PAYMENT;

    companion object {
        fun fromString(value: String): CashMovementType =
            entries.firstOrNull { it.name == value.uppercase() } ?: CASH_IN
    }
}
