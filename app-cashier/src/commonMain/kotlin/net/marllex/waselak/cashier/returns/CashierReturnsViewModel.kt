package net.marllex.waselak.cashier.returns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.ReturnRepository
import net.marllex.waselak.core.model.ProductReturn
import net.marllex.waselak.core.model.ReturnsSummary
import net.marllex.waselak.core.common.logging.AppLogger

class ReturnsViewModel(
    private val returnRepository: ReturnRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Returns" }


    data class UiState(
        val returns: List<ProductReturn> = emptyList(),
        val summary: ReturnsSummary = ReturnsSummary(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedStatus: String? = null,
    ) {
        val filteredReturns: List<ProductReturn>
            get() = if (selectedStatus == null) returns
            else returns.filter { it.status == selectedStatus }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            returnRepository.getReturns()
                .onSuccess { list -> _uiState.update { it.copy(returns = list, isLoading = false) } }
                .onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
        viewModelScope.launch {
            returnRepository.getSummary()
                .onSuccess { s -> _uiState.update { it.copy(summary = s) } }
        }
    }

    fun onStatusFilter(status: String?) { _uiState.update { it.copy(selectedStatus = status) } }

    fun processReturn(id: String, status: String) {
        AppLogger.d(TAG, "processReturn called")
        viewModelScope.launch {
            returnRepository.processReturn(id, status)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
