package net.marllex.waselak.manager.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.database.Pending_attendance
import net.marllex.waselak.core.database.Pending_sync
import net.marllex.waselak.core.database.dao.PendingSyncDao
import net.marllex.waselak.core.database.dao.WorkerDao
import net.marllex.waselak.core.data.sync.SyncScheduler
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.VendorRepository

class OfflineSettingsViewModel(
    private val vendorRepository: VendorRepository,
    private val workerDao: WorkerDao,
    private val authRepository: AuthRepository,
    private val pendingSyncDao: PendingSyncDao,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    data class UiState(
        val enableOfflineMode: Boolean = false,
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val isSyncing: Boolean = false,
        val error: String? = null,
        val lastSyncResult: String? = null,
        val pendingRecords: List<Pending_attendance> = emptyList(),
        val failedRecords: List<Pending_attendance> = emptyList(),
        val pendingSyncItems: List<Pending_sync> = emptyList(),
        val failedSyncItems: List<Pending_sync> = emptyList(),
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
        // Attendance pending records
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
        // Order/Payment pending sync records
        viewModelScope.launch {
            pendingSyncDao.getAllPending().collect { items ->
                _uiState.update {
                    it.copy(
                        pendingSyncItems = items.filter { item -> (item.retry_count ?: 0) < 3 },
                        failedSyncItems = items.filter { item -> (item.retry_count ?: 0) >= 3 },
                    )
                }
            }
        }
        // Last sync result
        viewModelScope.launch {
            syncScheduler.lastSyncResult.collect { result ->
                _uiState.update { it.copy(lastSyncResult = result) }
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

    fun syncNow() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            syncScheduler.triggerManualSync()
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    fun retryFailed(id: String) {
        viewModelScope.launch {
            val record = _uiState.value.failedRecords.find { it.id == id } ?: return@launch
            workerDao.insertPendingAttendance(
                record.copy(retry_count = 0)
            )
        }
    }

    fun retrySyncItem(id: String) {
        viewModelScope.launch {
            pendingSyncDao.updateRetry(id, 0, null)
        }
    }

    fun deleteFailed(id: String) {
        viewModelScope.launch {
            workerDao.deletePendingAttendance(id)
        }
    }

    fun deleteSyncItem(id: String) {
        viewModelScope.launch {
            pendingSyncDao.deletePending(id)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
