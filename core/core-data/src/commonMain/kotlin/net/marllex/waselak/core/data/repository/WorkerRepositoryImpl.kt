package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.database.Pending_attendance
import net.marllex.waselak.core.database.dao.VendorDao
import net.marllex.waselak.core.database.dao.WorkerDao
import net.marllex.waselak.core.database.mapper.*
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.*
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.connectivity.NetworkMonitor
import net.marllex.waselak.core.network.datasource.WorkerNetworkDataSource
import net.marllex.waselak.core.network.dto.*
import net.marllex.waselak.core.network.mapper.toDomain
import net.marllex.waselak.core.network.security.HmacSigner
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class WorkerRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val workerDao: WorkerDao,
    private val authRepository: AuthRepository,
    private val workerNetworkDataSource: WorkerNetworkDataSource,
    private val networkMonitor: NetworkMonitor,
    private val vendorDao: VendorDao,
) : WorkerRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""
    private val userId: String get() = authRepository.getCurrentUserId() ?: ""

    // ─── Workers ─────────────────────────────────────────────────

    override fun getWorkers(): Flow<List<Worker>> {
        AppLogger.d("WorkerRepo", "Reading all workers from local DB")
        return workerDao.getWorkers(vendorId).map { list -> list.map { it.toDomain() } }
    }

    override fun getActiveWorkers(): Flow<List<Worker>> {
        AppLogger.d("WorkerRepo", "Reading active workers from local DB")
        return workerDao.getActiveWorkers(vendorId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun refreshWorkers(): Result<List<Worker>> = runCatching {
        AppLogger.d("WorkerRepo", "Refreshing workers")
        val response = api.getWorkers()
        val workers = response.map { it.toDomain() }
        AppLogger.d("WorkerRepo", "Saving ${workers.size} workers to local DB")
        workerDao.deleteAllWorkers(vendorId)
        workerDao.insertWorkers(workers.map { it.toDbEntity() })
        AppLogger.i("WorkerRepo", "Fetched and saved ${workers.size} workers")
        workers
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to refresh workers", e)
    }

    override suspend fun createWorker(
        fullName: String, phone: String?, description: String?,
        photoUrl: String?,
        role: String, salaryType: SalaryType, salaryAmount: Double,
        isLoginEnabled: Boolean, password: String?, loginRole: String?,
        pin: String
    ): Result<Worker> = runCatching {
        AppLogger.d("WorkerRepo", "Creating worker: name=$fullName, role=$role")
        val response = api.createWorker(
            CreateWorkerRequest(
                fullName = fullName, phone = phone, description = description,
                photoUrl = photoUrl,
                role = role, salaryType = salaryType.name, salaryAmount = salaryAmount,
                isLoginEnabled = isLoginEnabled, password = password, loginRole = loginRole,
                pin = pin
            )
        )
        val worker = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving created worker to local DB: id=${worker.id}")
        workerDao.insertWorker(worker.toDbEntity())
        AppLogger.i("WorkerRepo", "Worker created: id=${worker.id}, name=$fullName")
        worker
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to create worker: name=$fullName, role=$role", e)
    }

    override suspend fun updateWorker(
        id: String, fullName: String?, phone: String?,
        description: String?, photoUrl: String?, role: String?, salaryType: String?,
        salaryAmount: Double?, pin: String?, active: Boolean?
    ): Result<Worker> = runCatching {
        AppLogger.d("WorkerRepo", "Updating worker: id=$id")
        val response = api.updateWorker(
            id, UpdateWorkerRequest(
                fullName = fullName, phone = phone, description = description,
                photoUrl = photoUrl,
                role = role, salaryType = salaryType, salaryAmount = salaryAmount,
                pin = pin, active = active
            )
        )
        val worker = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving updated worker to local DB: id=${worker.id}")
        workerDao.insertWorker(worker.toDbEntity())
        AppLogger.i("WorkerRepo", "Worker updated: id=${worker.id}")
        worker
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to update worker: id=$id", e)
    }

    override suspend fun deleteWorker(id: String): Result<Unit> = runCatching {
        AppLogger.d("WorkerRepo", "Deleting worker: id=$id")
        api.deleteWorker(id)
        AppLogger.d("WorkerRepo", "Removing worker from local DB: id=$id")
        workerDao.deleteWorker(id)
        AppLogger.i("WorkerRepo", "Worker deleted: id=$id")
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to delete worker: id=$id", e)
    }

    override suspend fun uploadImage(imageBytes: ByteArray, fileName: String): Result<String> = runCatching {
        AppLogger.d("WorkerRepo", "Uploading worker image: $fileName, size=${imageBytes.size} bytes")
        val response = api.uploadImage(imageBytes, fileName)
        AppLogger.i("WorkerRepo", "Worker image uploaded: url=${response.url}")
        response.url
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to upload worker image: $fileName", e)
    }

    // ─── Worker Roles ────────────────────────────────────────────

    override fun getWorkerRoles(): Flow<List<WorkerRole>> {
        AppLogger.d("WorkerRepo", "Reading worker roles from local DB")
        return workerDao.getWorkerRoles(vendorId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun refreshWorkerRoles(): Result<List<WorkerRole>> = runCatching {
        AppLogger.d("WorkerRepo", "Refreshing worker roles")
        val response = api.getWorkerRoles()
        val roles = response.map { it.toDomain() }
        AppLogger.d("WorkerRepo", "Saving ${roles.size} worker roles to local DB")
        workerDao.deleteAllWorkerRoles(vendorId)
        workerDao.insertWorkerRoles(roles.map { it.toDbEntity() })
        AppLogger.i("WorkerRepo", "Fetched and saved ${roles.size} worker roles")
        roles
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to refresh worker roles", e)
    }

    override suspend fun createWorkerRole(name: String, description: String?): Result<WorkerRole> = runCatching {
        AppLogger.d("WorkerRepo", "Creating worker role: name=$name")
        val response = api.createWorkerRole(CreateWorkerRoleRequest(name, description))
        val role = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving created worker role to local DB: id=${role.id}")
        workerDao.insertWorkerRole(role.toDbEntity())
        AppLogger.i("WorkerRepo", "Worker role created: id=${role.id}, name=$name")
        role
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to create worker role: name=$name", e)
    }

    override suspend fun deleteWorkerRole(id: String): Result<Unit> = runCatching {
        AppLogger.d("WorkerRepo", "Deleting worker role: id=$id")
        api.deleteWorkerRole(id)
        AppLogger.d("WorkerRepo", "Removing worker role from local DB: id=$id")
        workerDao.deleteWorkerRole(id)
        AppLogger.i("WorkerRepo", "Worker role deleted: id=$id")
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to delete worker role: id=$id", e)
    }

    // ─── Attendance ──────────────────────────────────────────────

    override fun getAttendanceByDate(date: String): Flow<List<Attendance>> {
        AppLogger.d("WorkerRepo", "Reading attendance from local DB: date=$date")
        return workerDao.getAttendanceByDate(vendorId, date).map { list -> list.map { it.toDomain() } }
    }

    override fun getAttendanceByWorker(workerId: String): Flow<List<Attendance>> {
        AppLogger.d("WorkerRepo", "Reading attendance from local DB: workerId=$workerId")
        return workerDao.getAttendanceByWorker(workerId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun refreshAttendance(
        workerId: String?, date: String?,
        fromDate: String?, toDate: String?
    ): Result<List<Attendance>> = runCatching {
        AppLogger.d("WorkerRepo", "Refreshing attendance: workerId=$workerId, date=$date")
        val response = api.getAttendance(workerId, date, fromDate, toDate)
        val records = response.map { it.toDomain() }
        AppLogger.d("WorkerRepo", "Saving ${records.size} attendance records to local DB")
        workerDao.deleteAllAttendance(vendorId)
        workerDao.insertAttendanceRecords(records.map { it.toDbEntity() })
        AppLogger.i("WorkerRepo", "Fetched and saved ${records.size} attendance records")
        records
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to refresh attendance: workerId=$workerId, date=$date", e)
    }

    override suspend fun getTodayAttendance(): Result<List<AttendanceSummary>> = runCatching {
        AppLogger.d("WorkerRepo", "Fetching today's attendance from API")
        val summaries = api.getTodayAttendance().map { it.toDomain() }
        AppLogger.i("WorkerRepo", "Fetched ${summaries.size} today attendance summaries")
        summaries
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to fetch today's attendance", e)
    }

    override suspend fun getAttendanceSummary(
        workerId: String, fromDate: String?, toDate: String?
    ): Result<AttendanceSummary> = runCatching {
        AppLogger.d("WorkerRepo", "Fetching attendance summary: workerId=$workerId, from=$fromDate, to=$toDate")
        val summary = api.getAttendanceSummary(workerId, fromDate, toDate).toDomain()
        AppLogger.i("WorkerRepo", "Fetched attendance summary for workerId=$workerId")
        summary
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to fetch attendance summary: workerId=$workerId", e)
    }

    override suspend fun checkIn(workerId: String, note: String?): Result<Attendance> = runCatching {
        AppLogger.d("WorkerRepo", "Check-in: workerId=$workerId")
        val response = api.checkIn(CheckInRequest(workerId, note))
        val attendance = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving check-in to local DB: id=${attendance.id}")
        workerDao.insertAttendance(attendance.toDbEntity())
        AppLogger.i("WorkerRepo", "Check-in recorded: id=${attendance.id}")
        attendance
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to check-in: workerId=$workerId", e)
    }

    override suspend fun checkOut(attendanceId: String, note: String?): Result<Attendance> = runCatching {
        AppLogger.d("WorkerRepo", "Check-out: attendanceId=$attendanceId")
        val response = api.checkOut(attendanceId, CheckOutRequest(note))
        val attendance = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving check-out to local DB: id=${attendance.id}")
        workerDao.insertAttendance(attendance.toDbEntity())
        AppLogger.i("WorkerRepo", "Check-out recorded: attendanceId=$attendanceId")
        attendance
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to check-out: attendanceId=$attendanceId", e)
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun checkInWithPin(workerId: String, pin: String): Result<Attendance> {
        AppLogger.d("WorkerRepo", "Check-in with PIN: workerId=$workerId")
        if (networkMonitor.isOnline.value) {
            val onlineResult = runCatching {
                val response = api.checkInWithPin(CheckInWithPinRequest(workerId, pin))
                val attendance = response.toDomain()
                workerDao.insertAttendance(attendance.toDbEntity())
                AppLogger.i("WorkerRepo", "Check-in with PIN successful")
                attendance
            }
            if (onlineResult.isSuccess) return onlineResult
            val error = onlineResult.exceptionOrNull()
            AppLogger.e("WorkerRepo", "Check-in with PIN failed online", error)
            // Only fall through to offline if it's a network/connectivity error
            // Business logic errors (wrong PIN, etc.) should be returned directly
            val isNetworkError = error is java.io.IOException ||
                error?.cause is java.io.IOException
            if (!isNetworkError) return onlineResult
            val vendor = vendorDao.getVendorById(vendorId).firstOrNull()?.toDomain()
            if (vendor?.enableOfflineMode != true) return onlineResult
            AppLogger.i("WorkerRepo", "Falling back to offline check-in with PIN")
        }

        // Offline check-in with local PIN verification
        return runCatching {
            val vendor = vendorDao.getVendorById(vendorId).firstOrNull()?.toDomain()
            if (vendor?.enableOfflineMode != true) {
                throw IllegalStateException("Offline mode is disabled")
            }

            val worker = workerDao.getWorkerById(workerId).firstOrNull()?.toDomain()
                ?: throw IllegalStateException("Worker not found locally")

            val storedHash = worker.pinSha256
                ?: throw IllegalStateException("Worker PIN not available offline")

            val inputHash = HmacSigner.sha256(pin)
            if (inputHash != storedHash) {
                throw IllegalStateException("Incorrect PIN")
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            val localId = Uuid.random().toString()

            val attendance = Attendance(
                id = localId,
                vendorId = vendorId,
                workerId = workerId,
                workerName = worker.fullName,
                workerRole = worker.role,
                date = today,
                checkIn = now,
                checkOut = null,
                workedMinutes = null,
                recordedBy = userId,
                note = null,
                createdAt = now,
            )
            workerDao.insertAttendance(attendance.toDbEntity())

            workerDao.insertPendingAttendance(
                Pending_attendance(
                    id = Uuid.random().toString(),
                    vendor_id = vendorId,
                    worker_id = workerId,
                    worker_name = worker.fullName,
                    worker_role = worker.role,
                    action = "CHECK_IN",
                    date = today,
                    timestamp = now,
                    linked_attendance_id = localId,
                    note = null,
                    retry_count = 0,
                    created_at = now,
                )
            )

            AppLogger.i("WorkerRepo", "Offline check-in with PIN: workerId=$workerId")
            attendance
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun checkOutWithPin(attendanceId: String, pin: String): Result<Attendance> {
        AppLogger.d("WorkerRepo", "Check-out with PIN: attendanceId=$attendanceId")
        if (networkMonitor.isOnline.value) {
            val onlineResult = runCatching {
                val response = api.checkOutWithPin(attendanceId, CheckOutWithPinRequest(pin))
                val attendance = response.toDomain()
                workerDao.insertAttendance(attendance.toDbEntity())
                AppLogger.i("WorkerRepo", "Check-out with PIN successful")
                attendance
            }
            if (onlineResult.isSuccess) return onlineResult
            val error = onlineResult.exceptionOrNull()
            AppLogger.e("WorkerRepo", "Check-out with PIN failed online", error)
            val isNetworkError = error is java.io.IOException ||
                error?.cause is java.io.IOException
            if (!isNetworkError) return onlineResult
            val vendor = vendorDao.getVendorById(vendorId).firstOrNull()?.toDomain()
            if (vendor?.enableOfflineMode != true) return onlineResult
            AppLogger.i("WorkerRepo", "Falling back to offline check-out with PIN")
        }

        // Offline check-out: find the local attendance record and update it
        return runCatching {
            val vendor = vendorDao.getVendorById(vendorId).firstOrNull()?.toDomain()
            if (vendor?.enableOfflineMode != true) {
                throw IllegalStateException("Offline mode is disabled")
            }

            val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
            val todayRecords = workerDao.getAttendanceByDate(vendorId, today).firstOrNull() ?: emptyList()
            val existingRecord = todayRecords.find { it.id == attendanceId }?.toDomain()
                ?: throw IllegalStateException("Attendance record not found")

            val worker = workerDao.getWorkerById(existingRecord.workerId).firstOrNull()?.toDomain()
                ?: throw IllegalStateException("Worker not found locally")

            val storedHash = worker.pinSha256
                ?: throw IllegalStateException("Worker PIN not available offline")

            val inputHash = HmacSigner.sha256(pin)
            if (inputHash != storedHash) {
                throw IllegalStateException("Incorrect PIN")
            }

            val now = Clock.System.now().toEpochMilliseconds()
            val workedMinutes = ((now - existingRecord.checkIn) / 60_000).toInt()

            val updatedAttendance = existingRecord.copy(
                checkOut = now,
                workedMinutes = workedMinutes,
            )
            workerDao.insertAttendance(updatedAttendance.toDbEntity())

            workerDao.insertPendingAttendance(
                Pending_attendance(
                    id = Uuid.random().toString(),
                    vendor_id = vendorId,
                    worker_id = existingRecord.workerId,
                    worker_name = worker.fullName,
                    worker_role = worker.role,
                    action = "CHECK_OUT",
                    date = today,
                    timestamp = now,
                    linked_attendance_id = attendanceId,
                    note = null,
                    retry_count = 0,
                    created_at = now,
                )
            )

            AppLogger.i("WorkerRepo", "Offline check-out with PIN: attendanceId=$attendanceId")
            updatedAttendance
        }
    }

    override suspend fun checkInWithQr(qrData: String): Result<Attendance> = runCatching {
        AppLogger.d("WorkerRepo", "Check-in with QR")
        val response = api.checkInWithQr(CheckInWithQrRequest(qrData))
        val attendance = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving QR check-in to local DB: id=${attendance.id}")
        workerDao.insertAttendance(attendance.toDbEntity())
        AppLogger.i("WorkerRepo", "QR check-in recorded: id=${attendance.id}")
        attendance
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to check-in with QR", e)
    }

    override suspend fun checkOutWithQr(qrData: String): Result<Attendance> = runCatching {
        AppLogger.d("WorkerRepo", "Check-out with QR")
        val response = api.checkOutWithQr(CheckOutWithQrRequest(qrData))
        val attendance = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving QR check-out to local DB: id=${attendance.id}")
        workerDao.insertAttendance(attendance.toDbEntity())
        AppLogger.i("WorkerRepo", "QR check-out recorded: id=${attendance.id}")
        attendance
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to check-out with QR", e)
    }

    override suspend fun deleteAttendance(id: String): Result<Unit> = runCatching {
        AppLogger.d("WorkerRepo", "Deleting attendance: id=$id")
        api.deleteAttendance(id)
        AppLogger.d("WorkerRepo", "Removing attendance from local DB: id=$id")
        workerDao.deleteAttendance(id)
        AppLogger.i("WorkerRepo", "Attendance deleted: id=$id")
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to delete attendance: id=$id", e)
    }

    override fun getPendingAttendanceCount(): Flow<Long> {
        AppLogger.d("WorkerRepo", "Reading pending attendance count from local DB")
        return workerDao.getPendingAttendanceCount(vendorId)
    }

    // ─── Salary Payments ─────────────────────────────────────────

    override fun getSalaryPayments(): Flow<List<SalaryPayment>> {
        AppLogger.d("WorkerRepo", "Reading salary payments from local DB")
        return workerDao.getSalaryPayments(vendorId).map { list -> list.map { it.toDomain() } }
    }

    override fun getSalaryPaymentsByWorker(workerId: String): Flow<List<SalaryPayment>> {
        AppLogger.d("WorkerRepo", "Reading salary payments from local DB: workerId=$workerId")
        return workerDao.getSalaryPaymentsByWorker(workerId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun refreshSalaryPayments(
        workerId: String?, paid: Boolean?, periodType: String?
    ): Result<List<SalaryPayment>> = runCatching {
        AppLogger.d("WorkerRepo", "Refreshing salary payments")
        val response = api.getSalaryPayments(workerId, paid, periodType)
        val payments = response.map { it.toDomain() }
        AppLogger.d("WorkerRepo", "Saving ${payments.size} salary payments to local DB")
        workerDao.deleteAllSalaryPayments(vendorId)
        workerDao.insertSalaryPayments(payments.map { it.toDbEntity() })
        AppLogger.i("WorkerRepo", "Fetched and saved ${payments.size} salary payments")
        payments
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to refresh salary payments", e)
    }

    override suspend fun markPaid(id: String, note: String?): Result<SalaryPayment> = runCatching {
        AppLogger.d("WorkerRepo", "Marking salary paid: id=$id")
        val response = api.markSalaryPaid(id, MarkPaidRequest(note))
        val payment = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving salary payment to local DB: id=${payment.id}")
        workerDao.insertSalaryPayment(payment.toDbEntity())
        AppLogger.i("WorkerRepo", "Salary marked paid: id=$id")
        payment
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to mark salary paid: id=$id", e)
    }

    override suspend fun markUnpaid(id: String): Result<SalaryPayment> = runCatching {
        AppLogger.d("WorkerRepo", "Marking salary unpaid: id=$id")
        val response = api.markSalaryUnpaid(id)
        val payment = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving salary payment to local DB: id=${payment.id}")
        workerDao.insertSalaryPayment(payment.toDbEntity())
        AppLogger.i("WorkerRepo", "Salary marked unpaid: id=$id")
        payment
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to mark salary unpaid: id=$id", e)
    }

    override suspend fun batchPaySalaries(
        paymentIds: List<String>, note: String?
    ): Result<List<SalaryPayment>> = runCatching {
        AppLogger.d("WorkerRepo", "Batch pay: ${paymentIds.size} payments")
        val response = api.batchPaySalaries(BatchPayRequest(paymentIds, note))
        val payments = response.map { it.toDomain() }
        AppLogger.d("WorkerRepo", "Saving ${payments.size} batch payments to local DB")
        workerDao.insertSalaryPayments(payments.map { it.toDbEntity() })
        AppLogger.i("WorkerRepo", "Batch pay completed: ${payments.size} payments processed")
        payments
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to batch pay salaries: ${paymentIds.size} payments", e)
    }

    // ─── PIN & QR Code Management ────────────────────────────────

    override suspend fun updateWorkerPin(workerId: String, pin: String): Result<Unit> = runCatching {
        AppLogger.d("WorkerRepo", "Updating worker PIN: workerId=$workerId")
        api.updateWorkerPin(workerId, UpdatePinRequest(pin))
        // Refresh worker to get updated hasPin and pinUpdatedAt
        AppLogger.d("WorkerRepo", "Refreshing worker data after PIN update: workerId=$workerId")
        val response = api.getWorker(workerId)
        workerDao.insertWorker(response.toDomain().toDbEntity())
        AppLogger.i("WorkerRepo", "Worker PIN updated: workerId=$workerId")
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to update worker PIN: workerId=$workerId", e)
    }

    override suspend fun getWorkerQrCode(workerId: String): Result<ByteArray> = runCatching {
        AppLogger.d("WorkerRepo", "Getting QR code for worker=$workerId")
        val qrBytes = workerNetworkDataSource.getWorkerQrCode(workerId)
        AppLogger.i("WorkerRepo", "QR code fetched: workerId=$workerId, size=${qrBytes.size} bytes")
        qrBytes
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to get QR code: workerId=$workerId", e)
    }

    override suspend fun regenerateWorkerQrCode(workerId: String): Result<Unit> = runCatching {
        AppLogger.d("WorkerRepo", "Regenerating QR code for worker=$workerId")
        api.regenerateWorkerQrCode(workerId)
        // Refresh worker to get updated qrCodeVersion
        AppLogger.d("WorkerRepo", "Refreshing worker data after QR regeneration: workerId=$workerId")
        val response = api.getWorker(workerId)
        workerDao.insertWorker(response.toDomain().toDbEntity())
        AppLogger.i("WorkerRepo", "QR code regenerated: workerId=$workerId")
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to regenerate QR code: workerId=$workerId", e)
    }

    // ─── Overtime ──────────────────────────────────────────────────

    override fun getOvertimeByWorker(workerId: String): Flow<List<Overtime>> {
        AppLogger.d("WorkerRepo", "Reading overtime from local DB: workerId=$workerId")
        return workerDao.getOvertimeByWorker(workerId).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun refreshOvertime(
        workerId: String?, fromDate: String?, toDate: String?
    ): Result<List<Overtime>> = runCatching {
        AppLogger.d("WorkerRepo", "Refreshing overtime")
        val response = api.getOvertime(workerId, fromDate, toDate)
        val entries = response.map { it.toDomain() }
        AppLogger.d("WorkerRepo", "Saving ${entries.size} overtime entries to local DB")
        workerDao.deleteAllOvertime(vendorId)
        workerDao.insertOvertimeEntries(entries.map { it.toDbEntity() })
        AppLogger.i("WorkerRepo", "Fetched and saved ${entries.size} overtime entries")
        entries
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to refresh overtime", e)
    }

    override suspend fun createOvertime(
        workerId: String, date: String, hours: Double, ratePerHour: Double, note: String?
    ): Result<Overtime> = runCatching {
        AppLogger.d("WorkerRepo", "Creating overtime: workerId=$workerId, hours=$hours")
        val response = api.createOvertime(
            CreateOvertimeRequest(
                workerId = workerId, date = date,
                hours = hours, ratePerHour = ratePerHour, note = note
            )
        )
        val entry = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving created overtime to local DB: id=${entry.id}")
        workerDao.insertOvertime(entry.toDbEntity())
        AppLogger.i("WorkerRepo", "Overtime created: id=${entry.id}, workerId=$workerId, hours=$hours")
        entry
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to create overtime: workerId=$workerId, hours=$hours", e)
    }

    override suspend fun updateOvertime(
        id: String, ratePerHour: Double?, note: String?
    ): Result<Overtime> = runCatching {
        AppLogger.d("WorkerRepo", "Updating overtime: id=$id")
        val response = api.updateOvertime(
            id, UpdateOvertimeRequest(ratePerHour = ratePerHour, note = note)
        )
        val entry = response.toDomain()
        AppLogger.d("WorkerRepo", "Saving updated overtime to local DB: id=${entry.id}")
        workerDao.insertOvertime(entry.toDbEntity())
        AppLogger.i("WorkerRepo", "Overtime updated: id=$id")
        entry
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to update overtime: id=$id", e)
    }

    override suspend fun deleteOvertime(id: String): Result<Unit> = runCatching {
        AppLogger.d("WorkerRepo", "Deleting overtime: id=$id")
        api.deleteOvertime(id)
        AppLogger.d("WorkerRepo", "Removing overtime from local DB: id=$id")
        workerDao.deleteOvertime(id)
        AppLogger.i("WorkerRepo", "Overtime deleted: id=$id")
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to delete overtime: id=$id", e)
    }

    override suspend fun markOvertimePaid(id: String): Result<Overtime> = runCatching {
        AppLogger.d("WorkerRepo", "Marking overtime paid: id=$id")
        val response = api.markOvertimePaid(id)
        val entry = response.toDomain()
        workerDao.insertOvertime(entry.toDbEntity())
        AppLogger.i("WorkerRepo", "Overtime marked paid: id=$id")
        entry
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to mark overtime paid: id=$id", e)
    }

    override suspend fun markOvertimeUnpaid(id: String): Result<Overtime> = runCatching {
        AppLogger.d("WorkerRepo", "Marking overtime unpaid: id=$id")
        val response = api.markOvertimeUnpaid(id)
        val entry = response.toDomain()
        workerDao.insertOvertime(entry.toDbEntity())
        AppLogger.i("WorkerRepo", "Overtime marked unpaid: id=$id")
        entry
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to mark overtime unpaid: id=$id", e)
    }

    override suspend fun batchPayOvertime(ids: List<String>): Result<List<Overtime>> = runCatching {
        AppLogger.d("WorkerRepo", "Batch paying overtime: count=${ids.size}")
        val response = api.batchPayOvertime(ids)
        val entries = response.map { it.toDomain() }
        workerDao.insertOvertimeEntries(entries.map { it.toDbEntity() })
        AppLogger.i("WorkerRepo", "Batch pay overtime completed: count=${entries.size}")
        entries
    }.onFailure { e ->
        AppLogger.e("WorkerRepo", "Failed to batch pay overtime", e)
    }
}
