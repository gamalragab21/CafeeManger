package net.marllex.waselak.core.model

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
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
    val paidAmount get() = payments.sumOf { it.paidAmount }
    val overdueCount get() = payments.count { it.status == "OVERDUE" }
    val partiallyPaidCount get() = payments.count { it.status == "PARTIALLY_PAID" }
    val totalLateFees get() = payments.sumOf { it.lateFee }
    val nextPayment get() = payments.firstOrNull {
        it.status == "PARTIALLY_PAID" || it.status == "OVERDUE" || it.status == "PENDING"
    }
    /** Payment whose due date falls in the current calendar month (or the next unpaid if none this month) */
    fun currentMonthPayment(nowMs: Long): InstallmentPayment? {
        // Switched off java.time so this compiles on iOS too — kotlinx.datetime
        // is multiplatform and the project already pulls it in for both
        // commonMain and iosMain. Logic is identical: epoch-ms → local date in
        // the system timezone, then a year*100+month integer makes month
        // comparison cheap. `monthNumber` (1-12) replaces JVM's monthValue.
        val tz = TimeZone.currentSystemDefault()
        val now = Instant.fromEpochMilliseconds(nowMs).toLocalDateTime(tz).date
        val thisMonth = now.year * 100 + now.monthNumber
        // First try: find unpaid payment due THIS month
        val thisMonthPayment = payments.firstOrNull { p ->
            p.needsPayment && run {
                val due = Instant.fromEpochMilliseconds(p.dueDate).toLocalDateTime(tz).date
                (due.year * 100 + due.monthNumber) == thisMonth
            }
        }
        // Fallback: first unpaid payment (overdue or upcoming)
        return thisMonthPayment ?: payments.firstOrNull { it.needsPayment }
    }

    val paidPaymentsCount get() = payments.count { it.status == "PAID" }
    val unpaidPaymentsCount get() = payments.count { it.needsPayment }
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
    val lateFeeEnabled: Boolean = true,
    val createdAt: Long = 0,
) {
    val isPending get() = status == "PENDING"
    val isPaid get() = status == "PAID"
    val isOverdue get() = status == "OVERDUE"
    val isPartiallyPaid get() = status == "PARTIALLY_PAID"
    val isWaived get() = status == "WAIVED"
    val totalDue get() = amount + lateFee
    val remainingDue get() = totalDue - paidAmount
    val canApplyLateFee get() = status in listOf("OVERDUE", "PARTIALLY_PAID", "PENDING") && lateFee == 0.0 && lateFeeEnabled
    val needsPayment get() = status in listOf("PENDING", "OVERDUE", "PARTIALLY_PAID")
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
