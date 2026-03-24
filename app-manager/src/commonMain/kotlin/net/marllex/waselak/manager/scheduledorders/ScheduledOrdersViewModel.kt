package net.marllex.waselak.manager.scheduledorders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.ScheduledOrderRepository
import net.marllex.waselak.core.model.ScheduledOrder
import net.marllex.waselak.core.common.logging.AppLogger

class ScheduledOrdersViewModel(
    private val scheduledOrderRepository: ScheduledOrderRepository,
) : ViewModel() {
    private companion object { private const val TAG = "ScheduledOrders" }


    data class UiState(
        val orders: List<ScheduledOrder> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedStatus: String? = null,
    ) {
        val filteredOrders: List<ScheduledOrder>
            get() = if (selectedStatus == null) orders
            else orders.filter { it.status == selectedStatus }
        val activeCount: Int get() = orders.count { it.isActive }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        AppLogger.d(TAG, "load called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            scheduledOrderRepository.getScheduledOrders()
                .onSuccess { list -> _uiState.update { it.copy(orders = list, isLoading = false) } }
                .onFailure { e ->
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(isLoading = false, error = e.message) } }
        }
    }

    fun onStatusFilter(status: String?) { _uiState.update { it.copy(selectedStatus = status) } }

    fun updateStatus(id: String, status: String) {
        viewModelScope.launch {
            scheduledOrderRepository.updateStatus(id, status)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun delete(id: String) {
        AppLogger.d(TAG, "delete called")
        viewModelScope.launch {
            scheduledOrderRepository.deleteScheduledOrder(id)
                .onSuccess { load() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }
}
