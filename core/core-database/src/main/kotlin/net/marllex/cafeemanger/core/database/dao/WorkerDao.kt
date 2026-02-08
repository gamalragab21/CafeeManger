package net.marllex.cafeemanger.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.database.entity.*

@Dao
interface WorkerDao {

    // ─── Workers ─────────────────────────────────────────────────
    @Query("SELECT * FROM workers WHERE vendor_id = :vendorId ORDER BY full_name ASC")
    fun getWorkers(vendorId: String): Flow<List<WorkerEntity>>

    @Query("SELECT * FROM workers WHERE vendor_id = :vendorId AND active = 1 ORDER BY full_name ASC")
    fun getActiveWorkers(vendorId: String): Flow<List<WorkerEntity>>

    @Query("SELECT * FROM workers WHERE id = :id")
    fun getWorkerById(id: String): Flow<WorkerEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkers(workers: List<WorkerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorker(worker: WorkerEntity)

    @Update
    suspend fun updateWorker(worker: WorkerEntity)

    @Query("DELETE FROM workers WHERE id = :id")
    suspend fun deleteWorker(id: String)

    @Query("DELETE FROM workers WHERE vendor_id = :vendorId")
    suspend fun deleteAllWorkers(vendorId: String)

    // ─── Worker Roles ────────────────────────────────────────────
    @Query("SELECT * FROM worker_roles WHERE vendor_id = :vendorId ORDER BY name ASC")
    fun getWorkerRoles(vendorId: String): Flow<List<WorkerRoleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkerRoles(roles: List<WorkerRoleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkerRole(role: WorkerRoleEntity)

    @Query("DELETE FROM worker_roles WHERE id = :id")
    suspend fun deleteWorkerRole(id: String)

    @Query("DELETE FROM worker_roles WHERE vendor_id = :vendorId")
    suspend fun deleteAllWorkerRoles(vendorId: String)

    // ─── Attendance ──────────────────────────────────────────────
    @Query("SELECT * FROM attendance WHERE vendor_id = :vendorId ORDER BY date DESC, check_in DESC")
    fun getAttendance(vendorId: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE vendor_id = :vendorId AND date = :date ORDER BY check_in DESC")
    fun getAttendanceByDate(vendorId: String, date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE worker_id = :workerId ORDER BY date DESC")
    fun getAttendanceByWorker(workerId: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE worker_id = :workerId AND date BETWEEN :fromDate AND :toDate ORDER BY date DESC")
    fun getAttendanceByWorkerAndDateRange(
        workerId: String,
        fromDate: String,
        toDate: String
    ): Flow<List<AttendanceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceRecords(records: List<AttendanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceEntity)

    @Query("DELETE FROM attendance WHERE vendor_id = :vendorId")
    suspend fun deleteAllAttendance(vendorId: String)

    @Query("DELETE FROM attendance WHERE id = :id")
    suspend fun deleteAttendance(id: String)

    // ─── Salary Payments ─────────────────────────────────────────
    @Query("SELECT * FROM salary_payments WHERE vendor_id = :vendorId ORDER BY created_at DESC")
    fun getSalaryPayments(vendorId: String): Flow<List<SalaryPaymentEntity>>

    @Query("SELECT * FROM salary_payments WHERE worker_id = :workerId ORDER BY created_at DESC")
    fun getSalaryPaymentsByWorker(workerId: String): Flow<List<SalaryPaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryPayments(payments: List<SalaryPaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSalaryPayment(payment: SalaryPaymentEntity)

    @Query("DELETE FROM salary_payments WHERE vendor_id = :vendorId")
    suspend fun deleteAllSalaryPayments(vendorId: String)
}
