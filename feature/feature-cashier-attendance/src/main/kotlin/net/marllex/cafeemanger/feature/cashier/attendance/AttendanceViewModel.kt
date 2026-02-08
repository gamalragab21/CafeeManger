package net.marllex.cafeemanger.feature.cashier.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.WorkerRepository
import net.marllex.cafeemanger.core.model.Attendance
import net.marllex.cafeemanger.core.model.AttendanceSummary
import net.marllex.cafeemanger.core.model.Worker
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel @Inject constructor(
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

        // Biometric action pending
        val pendingAction: PendingAction? = null,
    )

    sealed class PendingAction {
        data class CheckIn(val workerId: String, val workerName: String) : PendingAction()
        data class CheckOut(val attendanceId: String, val workerName: String) : PendingAction()
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
                    date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(java.util.Date())
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

    // Step 1: Request biometric, set pending action
    fun requestCheckIn(workerId: String, workerName: String) {
        _uiState.update { it.copy(pendingAction = PendingAction.CheckIn(workerId, workerName)) }
    }

    fun requestCheckOut(attendanceId: String, workerName: String) {
        _uiState.update { it.copy(pendingAction = PendingAction.CheckOut(attendanceId, workerName)) }
    }

    // Step 2: After biometric success, execute
    fun onBiometricSuccess() {
        val action = _uiState.value.pendingAction ?: return
        _uiState.update { it.copy(pendingAction = null) }

        viewModelScope.launch {
            when (action) {
                is PendingAction.CheckIn -> {
                    workerRepository.checkIn(action.workerId)
                        .onSuccess {
                            _uiState.update { it.copy(successMessage = "check_in_success") }
                            loadData()
                        }.onFailure { e ->
                            _uiState.update { it.copy(error = e.message) }
                        }
                }
                is PendingAction.CheckOut -> {
                    workerRepository.checkOut(action.attendanceId)
                        .onSuccess {
                            _uiState.update { it.copy(successMessage = "check_out_success") }
                            loadData()
                        }.onFailure { e ->
                            _uiState.update { it.copy(error = e.message) }
                        }
                }
            }
        }
    }

    fun onBiometricFailed() {
        _uiState.update { it.copy(pendingAction = null, error = "auth_failed") }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
