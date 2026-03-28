package net.marllex.waselak.feature.manager.staff

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.domain.repository.WorkerRepository
import net.marllex.waselak.core.model.Vendor
import net.marllex.waselak.core.model.Worker
import net.marllex.waselak.core.common.crash.CrashReporter

class WorkerQrCodeViewModel constructor(
    private val workerRepository: WorkerRepository,
    private val vendorRepository: VendorRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val worker: Worker? = null,
        val vendor: Vendor? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val successMessage: String? = null,
        val showRegenerateDialog: Boolean = false,
    )

    private val workerId: String = checkNotNull(savedStateHandle["workerId"])

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadWorker()
    }

    private fun loadWorker() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val vendor = vendorRepository.refreshVendor().getOrNull()

                workerRepository.refreshWorkers().onSuccess { workers ->
                    val worker = workers.find { it.id == workerId }
                    if (worker == null) {
                        _uiState.update { it.copy(isLoading = false, error = "Worker not found") }
                        return@launch
                    }
                    _uiState.update {
                        it.copy(worker = worker, vendor = vendor, isLoading = false)
                    }
                }.onFailure { e ->
                    CrashReporter.captureException(e)
                    _uiState.update {
                        it.copy(isLoading = false, error = "Failed to load worker: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun showRegenerateDialog() {
        _uiState.update { it.copy(showRegenerateDialog = true) }
    }

    fun dismissRegenerateDialog() {
        _uiState.update { it.copy(showRegenerateDialog = false) }
    }

    fun regenerateQrCode() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showRegenerateDialog = false) }

            workerRepository.regenerateWorkerQrCode(workerId).onSuccess {
                _uiState.update { it.copy(successMessage = "QR code regenerated successfully") }
                loadWorker()
            }.onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to regenerate QR code: ${e.message}")
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}
