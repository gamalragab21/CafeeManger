package net.marllex.cafeemanger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workers")
data class WorkerEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    val phone: String?,
    val description: String?,
    val role: String,
    @ColumnInfo(name = "salary_type") val salaryType: String,
    @ColumnInfo(name = "salary_amount") val salaryAmount: Double,
    val active: Boolean,
    @ColumnInfo(name = "user_id") val userId: String? = null,
    @ColumnInfo(name = "is_login_enabled") val isLoginEnabled: Boolean = false,
    @ColumnInfo(name = "has_pin") val hasPin: Boolean = false,
    @ColumnInfo(name = "qr_code_version") val qrCodeVersion: Int = 1,
    @ColumnInfo(name = "pin_updated_at") val pinUpdatedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?,
)

@Entity(tableName = "worker_roles")
data class WorkerRoleEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    val name: String,
    val description: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
)

@Entity(tableName = "attendance")
data class AttendanceEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "worker_name") val workerName: String?,
    @ColumnInfo(name = "worker_role") val workerRole: String?,
    val date: String,
    @ColumnInfo(name = "check_in") val checkIn: Long,
    @ColumnInfo(name = "check_out") val checkOut: Long?,
    @ColumnInfo(name = "worked_minutes") val workedMinutes: Int?,
    @ColumnInfo(name = "recorded_by") val recordedBy: String,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
)

@Entity(tableName = "salary_payments")
data class SalaryPaymentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    @ColumnInfo(name = "worker_id") val workerId: String,
    @ColumnInfo(name = "worker_name") val workerName: String?,
    @ColumnInfo(name = "period_type") val periodType: String,
    @ColumnInfo(name = "period_start") val periodStart: String,
    @ColumnInfo(name = "period_end") val periodEnd: String,
    @ColumnInfo(name = "worked_days") val workedDays: Int,
    @ColumnInfo(name = "worked_hours") val workedHours: Int?,
    val amount: Double,
    val paid: Boolean,
    @ColumnInfo(name = "paid_at") val paidAt: Long?,
    @ColumnInfo(name = "paid_by") val paidBy: String?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long?,
)
