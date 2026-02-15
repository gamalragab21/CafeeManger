package net.marllex.waselak.feature.cashier.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import net.marllex.waselak.core.common.extensions.toLocalDateTimeKt
import net.marllex.waselak.core.common.extensions.formatAsDate
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.Attendance
import net.marllex.waselak.core.model.AttendanceSummary
import net.marllex.waselak.core.model.Worker

class AttendanceViewModel constructor(
    private val workerRepository: WorkerRepository,
) : ViewModel() {

    data class UiState(
        val workers: List<Worker> = emptyList(),
        val todaySummary: List<AttendanceSummary> = emptyList(),
        val todayRecords: List<Attendance> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val successMessage: String? = null,
        val searchQuery: String = "",
        
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
    )

    sealed class AuthAction {
        object CheckIn : AuthAction()
        object CheckOut : AuthAction()
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                workerRepository.refreshWorkers()
                workerRepository.getTodayAttendance().onSuccess { summary ->
                    _uiState.update { it.copy(todaySummary = summary) }
                }
                workerRepository.refreshAttendance(
                    date = Clock.System.now().toLocalDateTimeKt(TimeZone.currentSystemDefault()).formatAsDate()
                ).onSuccess { records ->
                    _uiState.update { it.copy(todayRecords = records) }
                }

                workerRepository.getActiveWorkers().first().let { workers ->
                    _uiState.update { it.copy(workers = workers, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // Show PIN dialog for check-in
    fun showPinDialogForCheckIn(worker: Worker) {
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
        _uiState.update {
            it.copy(
                showQrScanner = true,
                authAction = AuthAction.CheckIn
            )
        }
    }

    // Show QR scanner for check-out
    fun showQrScannerForCheckOut() {
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
        val currentWorker = _uiState.value.selectedWorker
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showPinDialog = false) }
            workerRepository.checkInWithPin(workerId, pin)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            successMessage = "check_in_success",
                            selectedWorker = null,
                            authAction = null,
                            isLoading = false
                        )
                    }
                    loadData()
                }.onFailure { e ->
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
        val currentWorker = _uiState.value.selectedWorker
        val currentAttendanceId = attendanceId
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showPinDialog = false) }
            workerRepository.checkOutWithPin(attendanceId, pin)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            successMessage = "check_out_success",
                            selectedWorker = null,
                            selectedAttendanceId = null,
                            authAction = null,
                            isLoading = false
                        )
                    }
                    loadData()
                }.onFailure { e ->
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showQrScanner = false) }
            workerRepository.checkInWithQr(qrData)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            successMessage = "check_in_success",
                            authAction = null,
                            isLoading = false
                        )
                    }
                    loadData()
                }.onFailure { e ->
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showQrScanner = false) }
            workerRepository.checkOutWithQr(qrData)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            successMessage = "check_out_success",
                            authAction = null,
                            isLoading = false
                        )
                    }
                    loadData()
                }.onFailure { e ->
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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            workerRepository.checkIn(workerId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "check_in_success") }
                    loadData()
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    // Manual check-out (without PIN/QR)
    fun checkOutManual(attendanceId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            workerRepository.checkOut(attendanceId)
                .onSuccess {
                    _uiState.update { it.copy(successMessage = "check_out_success") }
                    loadData()
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
