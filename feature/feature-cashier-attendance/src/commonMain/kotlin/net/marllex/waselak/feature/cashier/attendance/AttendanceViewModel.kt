package net.marllex.waselak.feature.cashier.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.extensions.toLocalDateTimeKt
import net.marllex.waselak.core.common.extensions.formatAsDate
import net.marllex.waselak.core.data.sync.AttendanceSyncManager
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.Attendance
import net.marllex.waselak.core.model.AttendanceSummary
import net.marllex.waselak.core.model.Worker
import net.marllex.waselak.core.network.connectivity.NetworkMonitor
import net.marllex.waselak.core.network.isFeatureNotAvailableOrOffline
import net.marllex.waselak.core.common.crash.CrashReporter

class AttendanceViewModel constructor(
    private val workerRepository: WorkerRepository,
    private val networkMonitor: NetworkMonitor,
    private val syncManager: AttendanceSyncManager,
) : ViewModel() {

    data class UiState(
        val workers: List<Worker> = emptyList(),
        val todaySummary: List<AttendanceSummary> = emptyList(),
        val todayRecords: List<Attendance> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val successMessage: String? = null,
        val searchQuery: String = "",
        val isOffline: Boolean = false,
        val pendingCount: Long = 0,
        val isSyncing: Boolean = false,

        // Authentication dialogs
        val showPinDialog: Boolean = false,
        val showQrScanner: Boolean = false,
        val showQrErrorDialog: Boolean = false,
        val qrErrorMessage: String? = null,
        val showPinErrorDialog: Boolean = false,
        val pinErrorMessage: String? = null,
        val selectedWorker: Worker? = null,
        val selectedAttendanceId: String? = null,
        val authAction: AuthAction? = null,
        val isQrDisabled: Boolean = false,
        val showFeatureNotAvailable: Boolean = false,
        val featureNotAvailableMessage: String = "",
    )

    sealed class AuthAction {
        object CheckIn : AuthAction()
        object CheckOut : AuthAction()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeNetworkState()
        observePendingCount()
        observeSyncState()
        loadData()
    }

    private fun observeNetworkState() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOffline = !online, isQrDisabled = !online) }
                if (online) {
                    // Refresh data when coming back online
                    loadData()
                }
            }
        }
    }

    private fun observePendingCount() {
        viewModelScope.launch {
            workerRepository.getPendingAttendanceCount().collect { count ->
                _uiState.update { it.copy(pendingCount = count) }
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isSyncing = syncing) }
            }
        }
    }

    fun loadData() {
        CrashReporter.addBreadcrumb("loadData() called", "AttendanceViewModel")
        viewModelScope.launch {
            AppLogger.d("Attendance", "Loading attendance data, isOffline=${networkMonitor.isOnline.value.not()}")
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                if (networkMonitor.isOnline.value) {
                    workerRepository.refreshWorkers().getOrThrow()
                    val summary = workerRepository.getTodayAttendance().getOrThrow()
                    _uiState.update { it.copy(todaySummary = summary) }
                    val records = workerRepository.refreshAttendance(
                        date = Clock.System.now().toLocalDateTimeKt(TimeZone.currentSystemDefault()).formatAsDate()
                    ).getOrThrow()
                    _uiState.update { it.copy(todayRecords = records) }
                } else {
                    // Offline: load from local database only
                    val today = Clock.System.now().toLocalDateTimeKt(TimeZone.currentSystemDefault()).formatAsDate()
                    workerRepository.getAttendanceByDate(today).first().let { records ->
                        _uiState.update { it.copy(todayRecords = records) }
                    }
                }

                workerRepository.getActiveWorkers().first().let { workers ->
                    _uiState.update { it.copy(workers = workers, isLoading = false) }
                }
            } catch (e: Exception) {
                if (e.isFeatureNotAvailableOrOffline()) {
                    _uiState.update { it.copy(isLoading = false, showFeatureNotAvailable = true, featureNotAvailableMessage = e.message ?: "") }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // Show PIN dialog for check-in
    fun showPinDialogForCheckIn(worker: Worker) {
        AppLogger.i("Attendance", "User action: open PIN dialog for check-in, workerId=${worker.id}")
        _uiState.update {
            it.copy(
                showPinDialog = true,
                selectedWorker = worker,
                authAction = AuthAction.CheckIn,
                selectedAttendanceId = null
            )
        }
    }

    // Show PIN dialog for check-out
    fun showPinDialogForCheckOut(worker: Worker, attendanceId: String) {
        AppLogger.i("Attendance", "User action: open PIN dialog for check-out, workerId=${worker.id}, attendanceId=$attendanceId")
        _uiState.update {
            it.copy(
                showPinDialog = true,
                selectedWorker = worker,
                authAction = AuthAction.CheckOut,
                selectedAttendanceId = attendanceId
            )
        }
    }

    // Show QR scanner for check-in
    fun showQrScannerForCheckIn() {
        AppLogger.i("Attendance", "User action: open QR scanner for check-in")
        _uiState.update {
            it.copy(
                showQrScanner = true,
                authAction = AuthAction.CheckIn
            )
        }
    }

    // Show QR scanner for check-out
    fun showQrScannerForCheckOut() {
        AppLogger.i("Attendance", "User action: open QR scanner for check-out")
        _uiState.update {
            it.copy(
                showQrScanner = true,
                authAction = AuthAction.CheckOut
            )
        }
    }

    // Dismiss dialogs
    fun dismissPinDialog() {
        _uiState.update {
            it.copy(
                showPinDialog = false,
                selectedWorker = null,
                selectedAttendanceId = null,
                authAction = null
            )
        }
    }

    fun dismissQrScanner() {
        _uiState.update {
            it.copy(
                showQrScanner = false,
                authAction = null
            )
        }
    }

    fun dismissQrErrorDialog() {
        _uiState.update {
            it.copy(
                showQrErrorDialog = false,
                qrErrorMessage = null
            )
        }
    }

    fun dismissPinErrorDialog() {
        _uiState.update {
            it.copy(
                showPinErrorDialog = false,
                pinErrorMessage = null
            )
        }
    }

    fun retryQrScan() {
        _uiState.update {
            it.copy(
                showQrErrorDialog = false,
                qrErrorMessage = null,
                showQrScanner = true
            )
        }
    }

    fun retryPinEntry() {
        _uiState.update {
            it.copy(
                showPinErrorDialog = false,
                pinErrorMessage = null,
                showPinDialog = true
            )
        }
    }

    // Execute check-in with PIN
    fun checkInWithPin(workerId: String, pin: String) {
        AppLogger.d("Attendance", "Starting check-in with PIN: workerId=$workerId")
        val currentWorker = _uiState.value.selectedWorker
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showPinDialog = false) }
            workerRepository.checkInWithPin(workerId, pin)
                .onSuccess {
                    AppLogger.i("Attendance", "Check-in with PIN successful: workerId=$workerId")
                    _uiState.update {
                        it.copy(
                            successMessage = if (networkMonitor.isOnline.value) "check_in_success" else "offline_check_in_recorded",
                            selectedWorker = null,
                            authAction = null,
                            isLoading = false
                        )
                    }
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Attendance", "Check-in with PIN failed: workerId=$workerId", e)
                    _uiState.update {
                        it.copy(
                            showPinErrorDialog = true,
                            pinErrorMessage = e.message ?: "Unknown error occurred",
                            selectedWorker = currentWorker,
                            authAction = AuthAction.CheckIn,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // Execute check-out with PIN
    fun checkOutWithPin(attendanceId: String, pin: String) {
        AppLogger.d("Attendance", "Starting check-out with PIN: attendanceId=$attendanceId")
        val currentWorker = _uiState.value.selectedWorker
        val currentAttendanceId = attendanceId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showPinDialog = false) }
            workerRepository.checkOutWithPin(attendanceId, pin)
                .onSuccess {
                    AppLogger.i("Attendance", "Check-out with PIN successful: attendanceId=$attendanceId")
                    _uiState.update {
                        it.copy(
                            successMessage = if (networkMonitor.isOnline.value) "check_out_success" else "offline_check_out_recorded",
                            selectedWorker = null,
                            selectedAttendanceId = null,
                            authAction = null,
                            isLoading = false
                        )
                    }
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Attendance", "Check-out with PIN failed: attendanceId=$attendanceId", e)
                    _uiState.update {
                        it.copy(
                            showPinErrorDialog = true,
                            pinErrorMessage = e.message ?: "Unknown error occurred",
                            selectedWorker = currentWorker,
                            selectedAttendanceId = currentAttendanceId,
                            authAction = AuthAction.CheckOut,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // Execute check-in with QR code
    fun checkInWithQr(qrData: String) {
        AppLogger.d("Attendance", "Starting check-in with QR")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showQrScanner = false) }
            workerRepository.checkInWithQr(qrData)
                .onSuccess {
                    AppLogger.i("Attendance", "Check-in with QR successful")
                    _uiState.update {
                        it.copy(
                            successMessage = "check_in_success",
                            authAction = null,
                            isLoading = false
                        )
                    }
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Attendance", "Check-in with QR failed", e)
                    _uiState.update {
                        it.copy(
                            showQrErrorDialog = true,
                            qrErrorMessage = e.message ?: "Unknown error occurred",
                            authAction = null,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // Execute check-out with QR code
    fun checkOutWithQr(qrData: String) {
        AppLogger.d("Attendance", "Starting check-out with QR")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showQrScanner = false) }
            workerRepository.checkOutWithQr(qrData)
                .onSuccess {
                    AppLogger.i("Attendance", "Check-out with QR successful")
                    _uiState.update {
                        it.copy(
                            successMessage = "check_out_success",
                            authAction = null,
                            isLoading = false
                        )
                    }
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Attendance", "Check-out with QR failed", e)
                    _uiState.update {
                        it.copy(
                            showQrErrorDialog = true,
                            qrErrorMessage = e.message ?: "Unknown error occurred",
                            authAction = null,
                            isLoading = false
                        )
                    }
                }
        }
    }

    // Manual check-in (without PIN/QR)
    fun checkInManual(workerId: String) {
        AppLogger.d("Attendance", "Starting manual check-in: workerId=$workerId")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            workerRepository.checkIn(workerId)
                .onSuccess {
                    AppLogger.i("Attendance", "Manual check-in successful: workerId=$workerId")
                    _uiState.update { it.copy(successMessage = "check_in_success") }
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Attendance", "Manual check-in failed: workerId=$workerId", e)
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    // Manual check-out (without PIN/QR)
    fun checkOutManual(attendanceId: String) {
        AppLogger.d("Attendance", "Starting manual check-out: attendanceId=$attendanceId")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            workerRepository.checkOut(attendanceId)
                .onSuccess {
                    AppLogger.i("Attendance", "Manual check-out successful: attendanceId=$attendanceId")
                    _uiState.update { it.copy(successMessage = "check_out_success") }
                    loadData()
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e("Attendance", "Manual check-out failed: attendanceId=$attendanceId", e)
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun dismissFeatureNotAvailable() {
        _uiState.update { it.copy(showFeatureNotAvailable = false) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
