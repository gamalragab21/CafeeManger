package net.marllex.waselak.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Worker DTOs ─────────────────────────────────────────────────

@Serializable
data class WorkerResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    val description: String? = null,
    val role: String,
    @SerialName("salary_type") val salaryType: String,
    @SerialName("salary_amount") val salaryAmount: Double,
    val active: Boolean = true,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("is_login_enabled") val isLoginEnabled: Boolean = false,
    @SerialName("has_pin") val hasPin: Boolean = false,
    @SerialName("qr_code_version") val qrCodeVersion: Int = 1,
    @SerialName("pin_updated_at") val pinUpdatedAt: Long? = null,
    @SerialName("created_at") val createdAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

@Serializable
data class CreateWorkerRequest(
    @SerialName("full_name") val fullName: String,
    val phone: String? = null,
    val description: String? = null,
    val role: String,
    @SerialName("salary_type") val salaryType: String,
    @SerialName("salary_amount") val salaryAmount: Double = 0.0,
    @SerialName("is_login_enabled") val isLoginEnabled: Boolean = false,
    val password: String? = null,
    @SerialName("login_role") val loginRole: String? = null,
    val pin: String,
)

@Serializable
data class UpdateWorkerRequest(
    @SerialName("full_name") val fullName: String? = null,
    val phone: String? = null,
    val description: String? = null,
    val role: String? = null,
    @SerialName("salary_type") val salaryType: String? = null,
    @SerialName("salary_amount") val salaryAmount: Double? = null,
    val pin: String? = null,
    val active: Boolean? = null,
)

// ─── Worker Role DTOs ────────────────────────────────────────────

@Serializable
data class WorkerRoleResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    val name: String,
    val description: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class CreateWorkerRoleRequest(
    val name: String,
    val description: String? = null,
)

@Serializable
data class UpdatePinRequest(
    val pin: String,
)

@Serializable
data class QrCodeResponse(
    @SerialName("qr_code_version") val qrCodeVersion: Int,
    @SerialName("qr_code_data") val qrCodeData: String,
)

// ─── Attendance DTOs ─────────────────────────────────────────────

@Serializable
data class AttendanceResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("worker_name") val workerName: String? = null,
    @SerialName("worker_role") val workerRole: String? = null,
    val date: String,
    @SerialName("check_in") val checkIn: Long,
    @SerialName("check_out") val checkOut: Long? = null,
    @SerialName("worked_minutes") val workedMinutes: Int? = null,
    @SerialName("recorded_by") val recordedBy: String,
    @SerialName("auth_method") val authMethod: String = "MANUAL",
    val note: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class CheckInRequest(
    @SerialName("worker_id") val workerId: String,
    val note: String? = null,
)

@Serializable
data class CheckInWithPinRequest(
    @SerialName("worker_id") val workerId: String,
    val pin: String,
)

@Serializable
data class CheckOutWithPinRequest(
    val pin: String,
)

@Serializable
data class CheckInWithQrRequest(
    @SerialName("qr_data") val qrData: String,
)

@Serializable
data class CheckOutWithQrRequest(
    @SerialName("qr_data") val qrData: String,
)

@Serializable
data class CheckOutRequest(
    val note: String? = null,
)

@Serializable
data class AttendanceSummaryResponse(
    @SerialName("worker_id") val workerId: String,
    @SerialName("worker_name") val workerName: String,
    @SerialName("worker_role") val workerRole: String,
    @SerialName("total_days") val totalDays: Int,
    @SerialName("total_worked_minutes") val totalWorkedMinutes: Int,
    @SerialName("present_today") val presentToday: Boolean,
)

// ─── Salary Payment DTOs ─────────────────────────────────────────

@Serializable
data class SalaryPaymentResponse(
    val id: String,
    @SerialName("vendor_id") val vendorId: String,
    @SerialName("worker_id") val workerId: String,
    @SerialName("worker_name") val workerName: String? = null,
    @SerialName("period_type") val periodType: String,
    @SerialName("period_start") val periodStart: String,
    @SerialName("period_end") val periodEnd: String,
    @SerialName("worked_days") val workedDays: Int,
    @SerialName("worked_hours") val workedHours: Int? = null,
    val amount: Double,
    val paid: Boolean = false,
    @SerialName("paid_at") val paidAt: Long? = null,
    @SerialName("paid_by") val paidBy: String? = null,
    val note: String? = null,
    @SerialName("created_at") val createdAt: Long? = null,
)

@Serializable
data class MarkPaidRequest(
    val note: String? = null,
)

@Serializable
data class BatchPayRequest(
    @SerialName("payment_ids") val paymentIds: List<String>,
    val note: String? = null,
)
