package net.marllex.cafeemanger.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.model.*

interface WorkerRepository {
    // Workers
    fun getWorkers(): Flow<List<Worker>>
    fun getActiveWorkers(): Flow<List<Worker>>
    suspend fun refreshWorkers(): Result<List<Worker>>
    suspend fun createWorker(
        fullName: String, phone: String?, description: String?,
        role: String, salaryType: SalaryType, salaryAmount: Double,
        isLoginEnabled: Boolean = false, password: String? = null, loginRole: String? = null,
        pin: String
    ): Result<Worker>
    suspend fun updateWorker(
        id: String, fullName: String?, phone: String?,
        description: String?, role: String?, salaryType: String?,
        salaryAmount: Double?, pin: String? = null, active: Boolean?
    ): Result<Worker>
    suspend fun deleteWorker(id: String): Result<Unit>
    
    // PIN & QR Code Management
    suspend fun updateWorkerPin(workerId: String, pin: String): Result<Unit>
    suspend fun getWorkerQrCode(workerId: String): Result<ByteArray>
    suspend fun regenerateWorkerQrCode(workerId: String): Result<Unit>

    // Worker Roles
    fun getWorkerRoles(): Flow<List<WorkerRole>>
    suspend fun refreshWorkerRoles(): Result<List<WorkerRole>>
    suspend fun createWorkerRole(name: String, description: String?): Result<WorkerRole>
    suspend fun deleteWorkerRole(id: String): Result<Unit>

    // Attendance
    fun getAttendanceByDate(date: String): Flow<List<Attendance>>
    fun getAttendanceByWorker(workerId: String): Flow<List<Attendance>>
    suspend fun refreshAttendance(
        workerId: String? = null, date: String? = null,
        fromDate: String? = null, toDate: String? = null
    ): Result<List<Attendance>>
    suspend fun getTodayAttendance(): Result<List<AttendanceSummary>>
    suspend fun getAttendanceSummary(
        workerId: String, fromDate: String? = null, toDate: String? = null
    ): Result<AttendanceSummary>
    suspend fun checkIn(workerId: String, note: String? = null): Result<Attendance>
    suspend fun checkOut(attendanceId: String, note: String? = null): Result<Attendance>
    suspend fun checkInWithPin(workerId: String, pin: String): Result<Attendance>
    suspend fun checkOutWithPin(attendanceId: String, pin: String): Result<Attendance>
    suspend fun checkInWithQr(qrData: String): Result<Attendance>
    suspend fun checkOutWithQr(qrData: String): Result<Attendance>
    suspend fun deleteAttendance(id: String): Result<Unit>

    // Salary Payments
    fun getSalaryPayments(): Flow<List<SalaryPayment>>
    fun getSalaryPaymentsByWorker(workerId: String): Flow<List<SalaryPayment>>
    suspend fun refreshSalaryPayments(
        workerId: String? = null, paid: Boolean? = null, periodType: String? = null
    ): Result<List<SalaryPayment>>
    suspend fun markPaid(id: String, note: String? = null): Result<SalaryPayment>
    suspend fun markUnpaid(id: String): Result<SalaryPayment>
    suspend fun batchPaySalaries(paymentIds: List<String>, note: String? = null): Result<List<SalaryPayment>>
}
