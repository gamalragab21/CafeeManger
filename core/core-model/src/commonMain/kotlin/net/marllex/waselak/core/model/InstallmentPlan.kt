package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class InstallmentPlan(
    val id: String = "",
    val vendorId: String = "",
    val customerId: String = "",
    val customerName: String? = null,
    val customerPhone: String? = null,
    val orderId: String? = null,
    val totalAmount: Double = 0.0,
    val downPayment: Double = 0.0,
    val remainingAmount: Double = 0.0,
    val numInstallments: Int = 0,
    val installmentAmount: Double = 0.0,
    val lateFeePercent: Double = 0.0,
    val status: String = "ACTIVE",
    val startDate: Long = 0,
    val payments: List<InstallmentPayment> = emptyList(),
    val createdBy: String = "",
    val createdByName: String? = null,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val isActive get() = status == "ACTIVE"
    val isCompleted get() = status == "COMPLETED"
    val isDefaulted get() = status == "DEFAULTED"
    val isCancelled get() = status == "CANCELLED"
    val paidAmount get() = payments.filter { it.status == "PAID" }.sumOf { it.paidAmount }
    val overdueCount get() = payments.count { it.status == "OVERDUE" }
    val nextPayment get() = payments.firstOrNull { it.status == "PENDING" || it.status == "OVERDUE" }
}

@Serializable
data class InstallmentPayment(
    val id: String = "",
    val planId: String = "",
    val dueDate: Long = 0,
    val amount: Double = 0.0,
    val paidAmount: Double = 0.0,
    val lateFee: Double = 0.0,
    val status: String = "PENDING",
    val paidAt: Long? = null,
    val paidBy: String? = null,
    val paidByName: String? = null,
    val note: String? = null,
    val createdAt: Long = 0,
) {
    val isPending get() = status == "PENDING"
    val isPaid get() = status == "PAID"
    val isOverdue get() = status == "OVERDUE"
    val isWaived get() = status == "WAIVED"
    val totalDue get() = amount + lateFee
}

@Serializable
data class InstallmentAnalytics(
    val totalPlans: Int = 0,
    val activePlans: Int = 0,
    val completedPlans: Int = 0,
    val defaultedPlans: Int = 0,
    val totalRevenue: Double = 0.0,
    val collectedRevenue: Double = 0.0,
    val pendingRevenue: Double = 0.0,
    val overdueRevenue: Double = 0.0,
    val lateFeesCollected: Double = 0.0,
)
