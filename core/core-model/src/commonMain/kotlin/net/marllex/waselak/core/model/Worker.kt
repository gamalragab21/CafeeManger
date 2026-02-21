package net.marllex.waselak.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Worker(
    val id: String,
    val vendorId: String,
    val workerId: String, // Human-readable ID (e.g. WRK-001)
    val fullName: String,
    val phone: String? = null,
    val description: String? = null,
    val role: String,
    val salaryType: SalaryType = SalaryType.DAILY,
    val salaryAmount: Double = 0.0,
    val active: Boolean = true,
    val userId: String? = null,
    val isLoginEnabled: Boolean = false,
    val hasPin: Boolean = false,
    val qrCodeVersion: Int = 1,
    val pinUpdatedAt: Long? = null,
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
)

@Serializable
enum class SalaryType {
    DAILY,
    WEEKLY,
    MONTHLY
}

@Serializable
data class WorkerRole(
    val id: String,
    val vendorId: String,
    val name: String,
    val description: String? = null,
    val createdAt: Long? = null,
)

@Serializable
data class Attendance(
    val id: String,
    val vendorId: String,
    val workerId: String,
    val workerName: String? = null,
    val workerRole: String? = null,
    val date: String, // YYYY-MM-DD
    val checkIn: Long,
    val checkOut: Long? = null,
    val workedMinutes: Int? = null,
    val recordedBy: String,
    val authMethod: String = "MANUAL",
    val note: String? = null,
    val createdAt: Long? = null,
) {
    val isCheckedOut: Boolean get() = checkOut != null
    val workedHoursFormatted: String get() {
        val mins = workedMinutes ?: 0
        val h = mins / 60
        val m = mins % 60
        return "${h}h ${m}m"
    }
}

@Serializable
data class AttendanceSummary(
    val workerId: String,
    val workerName: String,
    val workerRole: String,
    val totalDays: Int,
    val totalWorkedMinutes: Int,
    val presentToday: Boolean,
    val attendedToday: Boolean = false,
) {
    val totalWorkedHours: Double get() = totalWorkedMinutes / 60.0
}

@Serializable
data class SalaryPayment(
    val id: String,
    val vendorId: String,
    val workerId: String,
    val workerName: String? = null,
    val periodType: String, // DAY, WEEK, MONTH
    val periodStart: String, // YYYY-MM-DD
    val periodEnd: String,
    val workedDays: Int,
    val workedHours: Int? = null,
    val amount: Double,
    val paid: Boolean = false,
    val paidAt: Long? = null,
    val paidBy: String? = null,
    val note: String? = null,
    val createdAt: Long? = null,
)

