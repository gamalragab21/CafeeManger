package net.marllex.cafeemanger.feature.manager.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.OrderRepository
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderStatus
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
) : ViewModel() {

    data class UiState(
        val orders: List<Order> = emptyList(),
        val selectedStatus: String? = null,
        val selectedChannel: String? = null,
        val selectedCashierId: String? = null,
        val selectedDeliveryUserId: String? = null,
        val fromDate: Long? = null,
        val toDate: Long? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadOrders() }

    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val s = _uiState.value
            orderRepository.refreshOrders(
                status = s.selectedStatus,
                channel = s.selectedChannel,
                cashierId = s.selectedCashierId,
                deliveryUserId = s.selectedDeliveryUserId,
                from = s.fromDate,
                to = s.toDate
            )
            orderRepository.getOrders(s.selectedStatus, s.selectedChannel)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { orders -> _uiState.update { it.copy(orders = orders, isLoading = false) } }
        }
    }

    fun filterByStatus(status: String?) {
        _uiState.update { it.copy(selectedStatus = status) }
        loadOrders()
    }

    fun filterByChannel(channel: String?) {
        _uiState.update { it.copy(selectedChannel = channel) }
        loadOrders()
    }

    fun filterByCashier(cashierId: String?) {
        _uiState.update { it.copy(selectedCashierId = cashierId) }
        loadOrders()
    }

    fun filterByDelivery(deliveryUserId: String?) {
        _uiState.update { it.copy(selectedDeliveryUserId = deliveryUserId) }
        loadOrders()
    }

    fun filterByDateRange(from: Long?, to: Long?) {
        _uiState.update { it.copy(fromDate = from, toDate = to) }
        loadOrders()
    }

    fun shareReceipt(orderId: String, onLink: (String) -> Unit) {
        viewModelScope.launch {
            orderRepository.shareReceipt(orderId)
                .onSuccess { link -> onLink(link.url) }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun updateOrderStatus(orderId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            orderRepository.updateOrderStatus(orderId, newStatus).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
