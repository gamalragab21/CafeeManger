package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Split Payment domain models.
 * Allows orders to be paid with multiple payment methods (e.g., part cash, part card).
 */

@Serializable
data class OrderPayment(
    val id: String,
    val orderId: String,
    val vendorId: String,
    val paymentMethod: String,   // CASH, CARD, WALLET
    val amount: Double,
    val paidBy: String? = null,
    val paidByName: String? = null,
    val note: String? = null,
    val createdAt: Long,
) {
    val isCash: Boolean get() = paymentMethod == "CASH"
    val isCard: Boolean get() = paymentMethod == "CARD"
    val isWallet: Boolean get() = paymentMethod == "WALLET"
}

@Serializable
data class SplitPaymentSummary(
    val orderId: String,
    val orderTotal: Double,
    val totalPaid: Double,
    val remaining: Double,
    val isFullyPaid: Boolean,
    val payments: List<OrderPayment> = emptyList(),
) {
    val paymentCount: Int get() = payments.size
    val paidPercentage: Double get() = if (orderTotal > 0) (totalPaid / orderTotal) * 100 else 0.0
}
