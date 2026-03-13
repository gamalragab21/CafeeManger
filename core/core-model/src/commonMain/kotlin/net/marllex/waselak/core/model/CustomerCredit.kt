package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

/**
 * Customer Credit Account domain models.
 * Allows customers to buy on credit (deferred payment) with limits.
 */

@Serializable
data class CustomerCredit(
    val id: String,
    val vendorId: String,
    val customerId: String,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val balance: Double = 0.0,         // Amount customer owes
    val creditLimit: Double = 500.0,
    val availableCredit: Double = 500.0,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val hasDebt: Boolean get() = balance > 0.01
    val isAtLimit: Boolean get() = availableCredit < 0.01
    val usagePercent: Double get() = if (creditLimit > 0) (balance / creditLimit) * 100 else 0.0
}

@Serializable
data class CreditTransaction(
    val id: String,
    val creditId: String,
    val vendorId: String,
    val orderId: String? = null,
    val type: String,                // CHARGE, PAYMENT, ADJUSTMENT
    val amount: Double,
    val previousBalance: Double,
    val newBalance: Double,
    val note: String? = null,
    val createdBy: String,
    val createdByName: String? = null,
    val createdAt: Long,
) {
    val isCharge: Boolean get() = type == "CHARGE"
    val isPayment: Boolean get() = type == "PAYMENT"
    val isAdjustment: Boolean get() = type == "ADJUSTMENT"
}

enum class CreditTransactionType {
    CHARGE, PAYMENT, ADJUSTMENT;

    companion object {
        fun fromString(value: String): CreditTransactionType =
            entries.firstOrNull { it.name == value.uppercase() } ?: CHARGE
    }
}
