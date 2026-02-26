package net.marllex.waselak.manager.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.database.Pending_attendance
import net.marllex.waselak.core.database.dao.WorkerDao
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository

class OfflineSettingsViewModel(
    private val vendorRepository: VendorRepository,
    private val workerDao: WorkerDao,
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val enableOfflineMode: Boolean = false,
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val error: String? = null,
        val pendingRecords: List<Pending_attendance> = emptyList(),
        val failedRecords: List<Pending_attendance> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            vendorRepository.refreshVendor()
                .onSuccess { vendor ->
                    _uiState.update {
                        it.copy(
                            enableOfflineMode = vendor.enableOfflineMode,
                            isLoading = false,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
        loadPendingRecords()
    }

    private fun loadPendingRecords() {
        viewModelScope.launch {
            workerDao.getPendingAttendance(vendorId).collect { pending ->
                _uiState.update { it.copy(pendingRecords = pending) }
            }
        }
        viewModelScope.launch {
            workerDao.getFailedPendingAttendance(vendorId).collect { failed ->
                _uiState.update { it.copy(failedRecords = failed) }
            }
        }
    }

    fun toggleOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            vendorRepository.updateVendor(enableOfflineMode = enabled)
                .onSuccess { vendor ->
                    _uiState.update {
                        it.copy(enableOfflineMode = vendor.enableOfflineMode, isSaving = false)
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }

    fun retryFailed(id: String) {
        viewModelScope.launch {
            // Reset retry count by re-inserting with 0 retries
            val record = _uiState.value.failedRecords.find { it.id == id } ?: return@launch
            workerDao.insertPendingAttendance(
                record.copy(retry_count = 0)
            )
        }
    }

    fun deleteFailed(id: String) {
        viewModelScope.launch {
            workerDao.deletePendingAttendance(id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
