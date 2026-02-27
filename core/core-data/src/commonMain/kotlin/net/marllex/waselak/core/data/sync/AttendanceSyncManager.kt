package net.marllex.waselak.core.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.core.database.dao.WorkerDao
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.network.ApiException
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.connectivity.NetworkMonitor
import net.marllex.waselak.core.network.dto.CheckInRequest
import net.marllex.waselak.core.network.dto.CheckOutRequest
import net.marllex.waselak.core.network.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity

class AttendanceSyncManager(
    private val networkMonitor: NetworkMonitor,
    private val api: WaselakApiClient,
    private val workerDao: WorkerDao,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    companion object {
        private const val MAX_RETRIES = 3
    }

    init {
        startObserving()
    }

    private fun startObserving() {
        scope.launch {
            networkMonitor.isOnline
                .collect { online ->
                    if (online) {
                        syncPendingRecords()
                    }
                }
        }
    }

    suspend fun syncPendingRecords() {
        if (_isSyncing.value) return
        _isSyncing.value = true

        try {
            val pending = workerDao.getAllPendingAttendance()
            if (pending.isEmpty()) return

            for (record in pending) {
                if (record.retry_count >= MAX_RETRIES) continue

                try {
                    when (record.action) {
                        "CHECK_IN" -> {
                            val response = api.checkIn(
                                CheckInRequest(
                                    workerId = record.worker_id,
                                    note = record.note,
                                )
                            )
                            val attendance = response.toDomain()
                            // Update local attendance record with server ID
                            record.linked_attendance_id?.let { localId ->
                                workerDao.deleteAttendance(localId)
                            }
                            workerDao.insertAttendance(attendance.toDbEntity())
                            workerDao.deletePendingAttendance(record.id)
                        }
                        "CHECK_OUT" -> {
                            // For check-out, we need the server attendance ID
                            // If the check-in was also offline, it may have already been synced
                            // and the local ID replaced. Try to find the server record.
                            val attendanceId = record.linked_attendance_id ?: continue
                            val response = api.checkOut(
                                attendanceId,
                                CheckOutRequest(note = record.note),
                            )
                            val attendance = response.toDomain()
                            workerDao.insertAttendance(attendance.toDbEntity())
                            workerDao.deletePendingAttendance(record.id)
                        }
                    }
                } catch (e: Exception) {
                    val statusCode = extractStatusCode(e)
                    if (statusCode == 409 || statusCode == 400) {
                        // Conflict or bad request - record already exists or invalid
                        workerDao.deletePendingAttendance(record.id)
                    } else {
                        workerDao.incrementPendingRetryCount(record.id)
                    }
                }
            }

            // Refresh attendance from server to get clean state
            try {
                val response = api.getAttendance(null, null, null, null)
                val records = response.map { it.toDomain() }
                workerDao.deleteAllAttendance(vendorId)
                workerDao.insertAttendanceRecords(records.map { it.toDbEntity() })
            } catch (_: Exception) {
                // Non-critical: UI will still show local data
            }
        } finally {
            _isSyncing.value = false
        }
    }

    private fun extractStatusCode(e: Exception): Int? {
        return (e as? ApiException)?.statusCode
    }
}
