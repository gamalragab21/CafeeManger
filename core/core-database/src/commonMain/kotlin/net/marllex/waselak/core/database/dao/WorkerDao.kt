package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Workers
import net.marllex.waselak.core.database.Worker_roles
import net.marllex.waselak.core.database.Attendance
import net.marllex.waselak.core.database.Salary_payments
import net.marllex.waselak.core.database.Overtime_entries
import net.marllex.waselak.core.database.Pending_attendance

class WorkerDao(private val db: WaselakDatabase) {
    private val workerQueries get() = db.workerQueries
    private val roleQueries get() = db.workerRoleQueries
    private val attendanceQueries get() = db.attendanceQueries
    private val salaryQueries get() = db.salaryPaymentQueries
    private val overtimeQueries get() = db.overtimeQueries
    private val pendingAttendanceQueries get() = db.pendingAttendanceQueries

    // ─── Workers ─────────────────────────────────────────────────
    fun getWorkers(vendorId: String): Flow<List<Workers>> =
        workerQueries.getWorkers(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getActiveWorkers(vendorId: String): Flow<List<Workers>> =
        workerQueries.getActiveWorkers(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getWorkerById(id: String): Flow<Workers?> =
        workerQueries.getWorkerById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun insertWorkers(workers: List<Workers>) {
        db.transaction {
            workers.forEach { worker -> insertWorkerInternal(worker) }
        }
    }

    suspend fun insertWorker(worker: Workers) {
        insertWorkerInternal(worker)
    }

    private fun insertWorkerInternal(worker: Workers) {
        workerQueries.insertWorker(
            id = worker.id,
            vendor_id = worker.vendor_id,
            worker_id = worker.worker_id,
            full_name = worker.full_name,
            phone = worker.phone,
            description = worker.description,
            role = worker.role,
            salary_type = worker.salary_type,
            salary_amount = worker.salary_amount,
            active = worker.active,
            user_id = worker.user_id,
            is_login_enabled = worker.is_login_enabled,
            has_pin = worker.has_pin,
            pin_sha256 = worker.pin_sha256,
            qr_code_version = worker.qr_code_version,
            pin_updated_at = worker.pin_updated_at,
            created_at = worker.created_at,
            updated_at = worker.updated_at
        )
    }

    suspend fun deleteWorker(id: String) {
        workerQueries.deleteWorker(id)
    }

    suspend fun deleteAllWorkers(vendorId: String) {
        workerQueries.deleteAllWorkers(vendorId)
    }

    // ─── Worker Roles ────────────────────────────────────────────
    fun getWorkerRoles(vendorId: String): Flow<List<Worker_roles>> =
        roleQueries.getWorkerRoles(vendorId).asFlow().mapToList(Dispatchers.Default)

    suspend fun insertWorkerRoles(roles: List<Worker_roles>) {
        db.transaction {
            roles.forEach { role ->
                roleQueries.insertWorkerRole(
                    id = role.id,
                    vendor_id = role.vendor_id,
                    name = role.name,
                    description = role.description,
                    created_at = role.created_at
                )
            }
        }
    }

    suspend fun insertWorkerRole(role: Worker_roles) {
        roleQueries.insertWorkerRole(
            id = role.id,
            vendor_id = role.vendor_id,
            name = role.name,
            description = role.description,
            created_at = role.created_at
        )
    }

    suspend fun deleteWorkerRole(id: String) {
        roleQueries.deleteWorkerRole(id)
    }

    suspend fun deleteAllWorkerRoles(vendorId: String) {
        roleQueries.deleteAllWorkerRoles(vendorId)
    }

    // ─── Attendance ──────────────────────────────────────────────
    fun getAttendance(vendorId: String): Flow<List<Attendance>> =
        attendanceQueries.getAttendance(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getAttendanceByDate(vendorId: String, date: String): Flow<List<Attendance>> =
        attendanceQueries.getAttendanceByDate(vendorId, date).asFlow().mapToList(Dispatchers.Default)

    fun getAttendanceByWorker(workerId: String): Flow<List<Attendance>> =
        attendanceQueries.getAttendanceByWorker(workerId).asFlow().mapToList(Dispatchers.Default)

    fun getAttendanceByWorkerAndDateRange(
        workerId: String,
        fromDate: String,
        toDate: String
    ): Flow<List<Attendance>> =
        attendanceQueries.getAttendanceByWorkerAndDateRange(workerId, fromDate, toDate)
            .asFlow().mapToList(Dispatchers.Default)

    suspend fun insertAttendanceRecords(records: List<Attendance>) {
        db.transaction {
            records.forEach { record -> insertAttendanceInternal(record) }
        }
    }

    suspend fun insertAttendance(record: Attendance) {
        insertAttendanceInternal(record)
    }

    private fun insertAttendanceInternal(record: Attendance) {
        attendanceQueries.insertAttendance(
            id = record.id,
            vendor_id = record.vendor_id,
            worker_id = record.worker_id,
            worker_name = record.worker_name,
            worker_role = record.worker_role,
            date = record.date,
            check_in = record.check_in,
            check_out = record.check_out,
            worked_minutes = record.worked_minutes,
            recorded_by = record.recorded_by,
            note = record.note,
            created_at = record.created_at
        )
    }

    suspend fun deleteAllAttendance(vendorId: String) {
        attendanceQueries.deleteAllAttendance(vendorId)
    }

    suspend fun deleteAttendance(id: String) {
        attendanceQueries.deleteAttendance(id)
    }

    // ─── Salary Payments ─────────────────────────────────────────
    fun getSalaryPayments(vendorId: String): Flow<List<Salary_payments>> =
        salaryQueries.getSalaryPayments(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getSalaryPaymentsByWorker(workerId: String): Flow<List<Salary_payments>> =
        salaryQueries.getSalaryPaymentsByWorker(workerId).asFlow().mapToList(Dispatchers.Default)

    suspend fun insertSalaryPayments(payments: List<Salary_payments>) {
        db.transaction {
            payments.forEach { payment ->
                salaryQueries.insertSalaryPayment(
                    id = payment.id,
                    vendor_id = payment.vendor_id,
                    worker_id = payment.worker_id,
                    worker_name = payment.worker_name,
                    period_type = payment.period_type,
                    period_start = payment.period_start,
                    period_end = payment.period_end,
                    worked_days = payment.worked_days,
                    worked_hours = payment.worked_hours,
                    amount = payment.amount,
                    paid = payment.paid,
                    paid_at = payment.paid_at,
                    paid_by = payment.paid_by,
                    note = payment.note,
                    created_at = payment.created_at
                )
            }
        }
    }

    suspend fun insertSalaryPayment(payment: Salary_payments) {
        salaryQueries.insertSalaryPayment(
            id = payment.id,
            vendor_id = payment.vendor_id,
            worker_id = payment.worker_id,
            worker_name = payment.worker_name,
            period_type = payment.period_type,
            period_start = payment.period_start,
            period_end = payment.period_end,
            worked_days = payment.worked_days,
            worked_hours = payment.worked_hours,
            amount = payment.amount,
            paid = payment.paid,
            paid_at = payment.paid_at,
            paid_by = payment.paid_by,
            note = payment.note,
            created_at = payment.created_at
        )
    }

    suspend fun deleteAllSalaryPayments(vendorId: String) {
        salaryQueries.deleteAllSalaryPayments(vendorId)
    }

    // ─── Overtime ──────────────────────────────────────────────────
    fun getOvertimeByVendor(vendorId: String): Flow<List<Overtime_entries>> =
        overtimeQueries.getOvertimeByVendor(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getOvertimeByWorker(workerId: String): Flow<List<Overtime_entries>> =
        overtimeQueries.getOvertimeByWorker(workerId).asFlow().mapToList(Dispatchers.Default)

    suspend fun insertOvertimeEntries(entries: List<Overtime_entries>) {
        db.transaction {
            entries.forEach { entry ->
                overtimeQueries.insertOvertime(
                    id = entry.id, vendor_id = entry.vendor_id,
                    worker_id = entry.worker_id, worker_name = entry.worker_name,
                    date = entry.date, hours = entry.hours,
                    rate_per_hour = entry.rate_per_hour, amount = entry.amount,
                    note = entry.note, created_by = entry.created_by,
                    created_at = entry.created_at
                )
            }
        }
    }

    suspend fun insertOvertime(entry: Overtime_entries) {
        overtimeQueries.insertOvertime(
            id = entry.id, vendor_id = entry.vendor_id,
            worker_id = entry.worker_id, worker_name = entry.worker_name,
            date = entry.date, hours = entry.hours,
            rate_per_hour = entry.rate_per_hour, amount = entry.amount,
            note = entry.note, created_by = entry.created_by,
            created_at = entry.created_at
        )
    }

    suspend fun deleteOvertime(id: String) {
        overtimeQueries.deleteOvertime(id)
    }

    suspend fun deleteAllOvertime(vendorId: String) {
        overtimeQueries.deleteAllOvertime(vendorId)
    }

    // ─── Pending Attendance (Offline Sync) ────────────────────────
    fun getPendingAttendance(vendorId: String): Flow<List<Pending_attendance>> =
        pendingAttendanceQueries.getPendingByVendor(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getPendingAttendanceCount(vendorId: String): Flow<Long> =
        pendingAttendanceQueries.getPendingCount(vendorId).asFlow()
            .mapToOneOrNull(Dispatchers.Default)
            .map { it ?: 0L }

    suspend fun getAllPendingAttendance(): List<Pending_attendance> =
        pendingAttendanceQueries.getAllPending().executeAsList()

    suspend fun insertPendingAttendance(record: Pending_attendance) {
        pendingAttendanceQueries.insertPending(
            id = record.id,
            vendor_id = record.vendor_id,
            worker_id = record.worker_id,
            worker_name = record.worker_name,
            worker_role = record.worker_role,
            action = record.action,
            date = record.date,
            timestamp = record.timestamp,
            linked_attendance_id = record.linked_attendance_id,
            note = record.note,
            retry_count = record.retry_count,
            created_at = record.created_at,
        )
    }

    suspend fun deletePendingAttendance(id: String) {
        pendingAttendanceQueries.deletePending(id)
    }

    suspend fun deleteAllPendingAttendance(vendorId: String) {
        pendingAttendanceQueries.deleteAllPending(vendorId)
    }

    suspend fun incrementPendingRetryCount(id: String) {
        pendingAttendanceQueries.incrementRetryCount(id)
    }
}
