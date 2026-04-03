package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateInstallmentPlanRequest(
    @SerialName("customer_id") val customerId: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("down_payment") val downPayment: Double = 0.0,
    @SerialName("num_installments") val numInstallments: Int,
    @SerialName("late_fee_percent") val lateFeePercent: Double = 0.0,
    @SerialName("start_date") val startDate: Long? = null,
)

@Serializable
data class RecordInstallmentPaymentRequest(
    val amount: Double,
    @SerialName("payment_id") val paymentId: String? = null,
    val note: String? = null,
)

@Serializable
data class ApplyLateFeeRequest(
    @SerialName("payment_id") val paymentId: String,
)

@Serializable
data class UpdateInstallmentStatusRequest(
    val status: String,
)

@Serializable
data class InstallmentPlanResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("customer_id") val customerId: String,
    @SerialName("customer_name") val customerName: String? = null,
    @SerialName("customer_phone") val customerPhone: String? = null,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("down_payment") val downPayment: Double = 0.0,
    @SerialName("remaining_amount") val remainingAmount: Double,
    @SerialName("num_installments") val numInstallments: Int,
    @SerialName("installment_amount") val installmentAmount: Double,
    @SerialName("late_fee_percent") val lateFeePercent: Double = 0.0,
    val status: String,
    @SerialName("start_date") val startDate: Long,
    val payments: List<InstallmentPaymentResponse> = emptyList(),
    @SerialName("created_by") val createdBy: String = "",
    @SerialName("created_by_name") val createdByName: String? = null,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

@Serializable
data class InstallmentPaymentResponse(
    val id: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("due_date") val dueDate: Long,
    val amount: Double,
    @SerialName("paid_amount") val paidAmount: Double = 0.0,
    @SerialName("late_fee") val lateFee: Double = 0.0,
    val status: String,
    @SerialName("paid_at") val paidAt: Long? = null,
    @SerialName("paid_by") val paidBy: String? = null,
    @SerialName("paid_by_name") val paidByName: String? = null,
    val note: String? = null,
    @SerialName("late_fee_enabled") val lateFeeEnabled: Boolean = true,
    @SerialName("created_at") val createdAt: Long = 0,
)

@Serializable
data class InstallmentAnalyticsResponse(
    @SerialName("total_plans") val totalPlans: Int = 0,
    @SerialName("active_plans") val activePlans: Int = 0,
    @SerialName("completed_plans") val completedPlans: Int = 0,
    @SerialName("defaulted_plans") val defaultedPlans: Int = 0,
    @SerialName("total_revenue") val totalRevenue: Double = 0.0,
    @SerialName("collected_revenue") val collectedRevenue: Double = 0.0,
    @SerialName("pending_revenue") val pendingRevenue: Double = 0.0,
    @SerialName("overdue_revenue") val overdueRevenue: Double = 0.0,
    @SerialName("late_fees_collected") val lateFeesCollected: Double = 0.0,
)
