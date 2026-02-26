package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.marllex.waselak.core.data.offline.OfflineModeManager
import net.marllex.waselak.core.data.sync.CheckInPayload
import net.marllex.waselak.core.data.sync.CheckOutPayload
import net.marllex.waselak.core.database.Pending_sync
import net.marllex.waselak.core.database.dao.PendingSyncDao
import net.marllex.waselak.core.database.dao.WorkerDao
import net.marllex.waselak.core.database.mapper.*
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.*
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.datasource.WorkerNetworkDataSource
import net.marllex.waselak.core.network.dto.*
import net.marllex.waselak.core.network.mapper.toDomain

class WorkerRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val workerDao: WorkerDao,
    private val authRepository: AuthRepository,
    private val workerNetworkDataSource: WorkerNetworkDataSource,
    private val offlineModeManager: OfflineModeManager,
    private val pendingSyncDao: PendingSyncDao,
) : WorkerRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    // ─── Workers ─────────────────────────────────────────────────

    override fun getWorkers(): Flow<List<Worker>> =
        workerDao.getWorkers(vendorId).map { list -> list.map { it.toDomain() } }

    override fun getActiveWorkers(): Flow<List<Worker>> =
        workerDao.getActiveWorkers(vendorId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshWorkers(): Result<List<Worker>> = runCatching {
        val response = api.getWorkers()
        val workers = response.map { it.toDomain() }
        workerDao.deleteAllWorkers(vendorId)
        workerDao.insertWorkers(workers.map { it.toDbEntity() })
        workers
    }

    override suspend fun createWorker(
        fullName: String, phone: String?, description: String?,
        role: String, salaryType: SalaryType, salaryAmount: Double,
        isLoginEnabled: Boolean, password: String?, loginRole: String?,
        pin: String
    ): Result<Worker> = runCatching {
        val response = api.createWorker(
            CreateWorkerRequest(
                fullName = fullName, phone = phone, description = description,
                role = role, salaryType = salaryType.name, salaryAmount = salaryAmount,
                isLoginEnabled = isLoginEnabled, password = password, loginRole = loginRole,
                pin = pin
            )
        )
        val worker = response.toDomain()
        workerDao.insertWorker(worker.toDbEntity())
        worker
    }

    override suspend fun updateWorker(
        id: String, fullName: String?, phone: String?,
        description: String?, role: String?, salaryType: String?,
        salaryAmount: Double?, pin: String?, active: Boolean?
    ): Result<Worker> = runCatching {
        val response = api.updateWorker(
            id, UpdateWorkerRequest(
                fullName = fullName, phone = phone, description = description,
                role = role, salaryType = salaryType, salaryAmount = salaryAmount,
                pin = pin, active = active
            )
        )
        val worker = response.toDomain()
        workerDao.insertWorker(worker.toDbEntity())
        worker
    }

    override suspend fun deleteWorker(id: String): Result<Unit> = runCatching {
        api.deleteWorker(id)
        workerDao.deleteWorker(id)
    }

    // ─── Worker Roles ────────────────────────────────────────────

    override fun getWorkerRoles(): Flow<List<WorkerRole>> =
        workerDao.getWorkerRoles(vendorId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshWorkerRoles(): Result<List<WorkerRole>> = runCatching {
        val response = api.getWorkerRoles()
        val roles = response.map { it.toDomain() }
        workerDao.deleteAllWorkerRoles(vendorId)
        workerDao.insertWorkerRoles(roles.map { it.toDbEntity() })
        roles
    }

    override suspend fun createWorkerRole(name: String, description: String?): Result<WorkerRole> = runCatching {
        val response = api.createWorkerRole(CreateWorkerRoleRequest(name, description))
        val role = response.toDomain()
        workerDao.insertWorkerRole(role.toDbEntity())
        role
    }

    override suspend fun deleteWorkerRole(id: String): Result<Unit> = runCatching {
        api.deleteWorkerRole(id)
        workerDao.deleteWorkerRole(id)
    }

    // ─── Attendance ──────────────────────────────────────────────

    override fun getAttendanceByDate(date: String): Flow<List<Attendance>> =
        workerDao.getAttendanceByDate(vendorId, date).map { list -> list.map { it.toDomain() } }

    override fun getAttendanceByWorker(workerId: String): Flow<List<Attendance>> =
        workerDao.getAttendanceByWorker(workerId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshAttendance(
        workerId: String?, date: String?,
        fromDate: String?, toDate: String?
    ): Result<List<Attendance>> = runCatching {
        val response = api.getAttendance(workerId, date, fromDate, toDate)
        val records = response.map { it.toDomain() }
        workerDao.deleteAllAttendance(vendorId)
        workerDao.insertAttendanceRecords(records.map { it.toDbEntity() })
        records
    }

    override suspend fun getTodayAttendance(): Result<List<AttendanceSummary>> = runCatching {
        api.getTodayAttendance().map { it.toDomain() }
    }

    override suspend fun getAttendanceSummary(
        workerId: String, fromDate: String?, toDate: String?
    ): Result<AttendanceSummary> = runCatching {
        api.getAttendanceSummary(workerId, fromDate, toDate).toDomain()
    }

    override suspend fun checkIn(workerId: String, note: String?): Result<Attendance> = runCatching {
        if (offlineModeManager.isOfflineActive.value) {
            createOfflineCheckIn(workerId, null)
        } else {
            val response = api.checkIn(CheckInRequest(workerId, note))
            val attendance = response.toDomain()
            workerDao.insertAttendance(attendance.toDbEntity())
            attendance
        }
    }

    override suspend fun checkOut(attendanceId: String, note: String?): Result<Attendance> = runCatching {
        if (offlineModeManager.isOfflineActive.value) {
            createOfflineCheckOut(attendanceId, null)
        } else {
            val response = api.checkOut(attendanceId, CheckOutRequest(note))
            val attendance = response.toDomain()
            workerDao.insertAttendance(attendance.toDbEntity())
            attendance
        }
    }

    override suspend fun checkInWithPin(workerId: String, pin: String): Result<Attendance> = runCatching {
        if (offlineModeManager.isOfflineActive.value) {
            createOfflineCheckIn(workerId, pin)
        } else {
            val response = api.checkInWithPin(CheckInWithPinRequest(workerId, pin))
            val attendance = response.toDomain()
            workerDao.insertAttendance(attendance.toDbEntity())
            attendance
        }
    }

    override suspend fun checkOutWithPin(attendanceId: String, pin: String): Result<Attendance> = runCatching {
        if (offlineModeManager.isOfflineActive.value) {
            createOfflineCheckOut(attendanceId, pin)
        } else {
            val response = api.checkOutWithPin(attendanceId, CheckOutWithPinRequest(pin))
            val attendance = response.toDomain()
            workerDao.insertAttendance(attendance.toDbEntity())
            attendance
        }
    }

    private suspend fun createOfflineCheckIn(workerId: String, pin: String?): Attendance {
        val now = Clock.System.now().toEpochMilliseconds()
        val localId = "offline-checkin-$now"
        val today = Clock.System.now()
            .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
        val attendance = Attendance(
            id = localId, vendorId = vendorId, workerId = workerId,
            date = today, checkIn = now,
            recordedBy = authRepository.getCurrentUserId() ?: "",
            authMethod = if (pin != null) "PIN" else "MANUAL",
            createdAt = now, syncStatus = "PENDING_SYNC",
        )
        workerDao.insertAttendance(attendance.toDbEntity())
        pendingSyncDao.insertPending(Pending_sync(
            id = localId, type = "CHECK_IN",
            payload = json.encodeToString(CheckInPayload(workerId, pin)),
            created_at = now, retry_count = 0, last_error = null,
        ))
        return attendance
    }

    private suspend fun createOfflineCheckOut(attendanceId: String, pin: String?): Attendance {
        val now = Clock.System.now().toEpochMilliseconds()
        val localId = "offline-checkout-$now"
        // We need workerId from the existing attendance record — use attendanceId prefix
        val attendance = Attendance(
            id = localId, vendorId = vendorId, workerId = "",
            date = Clock.System.now()
                .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
            checkIn = now, checkOut = now,
            recordedBy = authRepository.getCurrentUserId() ?: "",
            authMethod = if (pin != null) "PIN" else "MANUAL",
            createdAt = now, syncStatus = "PENDING_SYNC",
        )
        workerDao.insertAttendance(attendance.toDbEntity())
        pendingSyncDao.insertPending(Pending_sync(
            id = localId, type = "CHECK_OUT",
            payload = json.encodeToString(CheckOutPayload(attendanceId, "", pin)),
            created_at = now, retry_count = 0, last_error = null,
        ))
        return attendance
    }

    override suspend fun checkInWithQr(qrData: String): Result<Attendance> = runCatching {
        val response = api.checkInWithQr(CheckInWithQrRequest(qrData))
        val attendance = response.toDomain()
        workerDao.insertAttendance(attendance.toDbEntity())
        attendance
    }

    override suspend fun checkOutWithQr(qrData: String): Result<Attendance> = runCatching {
        val response = api.checkOutWithQr(CheckOutWithQrRequest(qrData))
        val attendance = response.toDomain()
        workerDao.insertAttendance(attendance.toDbEntity())
        attendance
    }

    override suspend fun deleteAttendance(id: String): Result<Unit> = runCatching {
        api.deleteAttendance(id)
        workerDao.deleteAttendance(id)
    }

    // ─── Salary Payments ─────────────────────────────────────────

    override fun getSalaryPayments(): Flow<List<SalaryPayment>> =
        workerDao.getSalaryPayments(vendorId).map { list -> list.map { it.toDomain() } }

    override fun getSalaryPaymentsByWorker(workerId: String): Flow<List<SalaryPayment>> =
        workerDao.getSalaryPaymentsByWorker(workerId).map { list -> list.map { it.toDomain() } }

    override suspend fun refreshSalaryPayments(
        workerId: String?, paid: Boolean?, periodType: String?
    ): Result<List<SalaryPayment>> = runCatching {
        val response = api.getSalaryPayments(workerId, paid, periodType)
        val payments = response.map { it.toDomain() }
        workerDao.deleteAllSalaryPayments(vendorId)
        workerDao.insertSalaryPayments(payments.map { it.toDbEntity() })
        payments
    }

    override suspend fun markPaid(id: String, note: String?): Result<SalaryPayment> = runCatching {
        val response = api.markSalaryPaid(id, MarkPaidRequest(note))
        val payment = response.toDomain()
        workerDao.insertSalaryPayment(payment.toDbEntity())
        payment
    }

    override suspend fun markUnpaid(id: String): Result<SalaryPayment> = runCatching {
        val response = api.markSalaryUnpaid(id)
        val payment = response.toDomain()
        workerDao.insertSalaryPayment(payment.toDbEntity())
        payment
    }

    override suspend fun batchPaySalaries(
        paymentIds: List<String>, note: String?
    ): Result<List<SalaryPayment>> = runCatching {
        val response = api.batchPaySalaries(BatchPayRequest(paymentIds, note))
        val payments = response.map { it.toDomain() }
        workerDao.insertSalaryPayments(payments.map { it.toDbEntity() })
        payments
    }

    // ─── PIN & QR Code Management ────────────────────────────────

    override suspend fun updateWorkerPin(workerId: String, pin: String): Result<Unit> = runCatching {
        api.updateWorkerPin(workerId, UpdatePinRequest(pin))
        // Refresh worker to get updated hasPin and pinUpdatedAt
        val response = api.getWorker(workerId)
        workerDao.insertWorker(response.toDomain().toDbEntity())
    }

    override suspend fun getWorkerQrCode(workerId: String): Result<ByteArray> = runCatching {
        workerNetworkDataSource.getWorkerQrCode(workerId)
    }

    override suspend fun regenerateWorkerQrCode(workerId: String): Result<Unit> = runCatching {
        api.regenerateWorkerQrCode(workerId)
        // Refresh worker to get updated qrCodeVersion
        val response = api.getWorker(workerId)
        workerDao.insertWorker(response.toDomain().toDbEntity())
    }
}
