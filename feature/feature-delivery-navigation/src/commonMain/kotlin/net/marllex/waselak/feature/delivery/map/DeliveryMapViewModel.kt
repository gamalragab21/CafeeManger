package net.marllex.waselak.feature.delivery.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

class DeliveryMapViewModel constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {
    private companion object { private const val TAG = "DeliveryMap" }


    data class UiState(
        val activeOrders: List<Order> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadActiveDeliveries() }

    fun loadActiveDeliveries() {
        AppLogger.d(TAG, "loadActiveDeliveries called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            orderRepository.getMyDeliveryOrders("OUT_FOR_DELIVERY")
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { orders ->
                    _uiState.update { it.copy(activeOrders = orders, isLoading = false) }
                }
        }
    }
}
