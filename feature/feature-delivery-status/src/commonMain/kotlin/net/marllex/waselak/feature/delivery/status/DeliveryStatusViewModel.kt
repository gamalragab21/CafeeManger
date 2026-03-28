package net.marllex.waselak.feature.delivery.status

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class DeliveryStatusViewModel constructor(
    savedStateHandle: SavedStateHandle,
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private companion object { private const val TAG = "DeliveryStatus" }


    data class UiState(
        val order: Order? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isUpdating: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val orderId: String = savedStateHandle["orderId"] ?: ""

    init {
        if (orderId.isNotBlank()) loadOrder()
    }

    fun loadOrder() {
        AppLogger.d(TAG, "loadOrder called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            orderRepository.getOrderById(orderId).collect { order ->
                _uiState.update { it.copy(order = order, isLoading = false) }
            }
        }
    }

    fun updateStatus(newStatus: OrderStatus) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            orderRepository.updateOrderStatus(orderId, newStatus)
                .onSuccess {
                    AppLogger.i(TAG, "Data loaded successfully")
                    _uiState.update { it.copy(isUpdating = false) }
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e)
                    _uiState.update { it.copy(isUpdating = false, error = e.message) }
                }
        }
    }
}
