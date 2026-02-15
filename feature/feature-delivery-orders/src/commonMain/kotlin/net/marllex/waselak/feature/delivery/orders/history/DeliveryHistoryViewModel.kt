package net.marllex.waselak.feature.delivery.orders.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.model.Order

class DeliveryHistoryViewModel constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    data class UiState(
        val grouped: Map<String, List<Order>> = emptyMap(),
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            orderRepository.refreshMyDeliveryOrders(status = "COMPLETED")
                .onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
            orderRepository.getMyDeliveryOrders(status = "COMPLETED").collect { orders ->
                val grouped = orders.groupBy { epochDay(it.createdAt) }
                    .toSortedMap(compareByDescending { it }) // latest day first
                _uiState.update { it.copy(grouped = grouped, isLoading = false) }
            }
        }
    }

    private fun epochDay(epochMs: Long): String {
        val local = java.time.Instant.ofEpochMilli(epochMs)
            .atZone(java.time.ZoneId.systemDefault())
        return local.toLocalDate().toString()
    }
}
