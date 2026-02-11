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
import net.marllex.cafeemanger.core.domain.repository.UserManagementRepository
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.model.UserRole
import javax.inject.Inject

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val userRepository: UserManagementRepository,
) : ViewModel() {

    data class UiState(
        val orders: List<Order> = emptyList(),
        val selectedStatus: String? = null,
        val selectedChannel: String? = null,
        val selectedCashierId: String? = null,
        val selectedDeliveryUserId: String? = null,
        val fromDate: Long? = null,
        val toDate: Long? = null,
        val cashiers: List<net.marllex.cafeemanger.core.model.User> = emptyList(),
        val deliveryUsers: List<net.marllex.cafeemanger.core.model.User> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        // Delivery assignment dialog
        val showAssignDeliveryDialog: Boolean = false,
        val assignOrderId: String? = null,
    ) {
        val hasActiveFilters: Boolean
            get() = selectedStatus != null || selectedChannel != null ||
                    selectedCashierId != null || selectedDeliveryUserId != null ||
                    fromDate != null || toDate != null
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadOrders()
        loadUsers()
    }

    private fun loadUsers() {
        viewModelScope.launch {
            // Refresh from API first to populate local DB
            userRepository.refreshUsers()
        }
        viewModelScope.launch {
            userRepository.getUsers(UserRole.CASHIER).collect { cashiers ->
                _uiState.update { it.copy(cashiers = cashiers) }
            }
        }
        viewModelScope.launch {
            userRepository.getUsers(UserRole.DELIVERY).collect { deliveryUsers ->
                _uiState.update { it.copy(deliveryUsers = deliveryUsers) }
            }
        }
    }

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

    fun clearAllFilters() {
        _uiState.update {
            it.copy(
                selectedStatus = null,
                selectedChannel = null,
                selectedCashierId = null,
                selectedDeliveryUserId = null,
                fromDate = null,
                toDate = null
            )
        }
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
        // If transitioning to ASSIGNED on a delivery order, show the delivery person picker
        if (newStatus == OrderStatus.ASSIGNED) {
            val order = _uiState.value.orders.find { it.id == orderId }
            if (order?.channel == net.marllex.cafeemanger.core.model.OrderChannel.DELIVERY) {
                _uiState.update {
                    it.copy(showAssignDeliveryDialog = true, assignOrderId = orderId)
                }
                return
            }
        }
        viewModelScope.launch {
            orderRepository.updateOrderStatus(orderId, newStatus).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun dismissAssignDeliveryDialog() {
        _uiState.update { it.copy(showAssignDeliveryDialog = false, assignOrderId = null) }
    }

    fun assignDeliveryUser(deliveryUserId: String) {
        val orderId = _uiState.value.assignOrderId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(showAssignDeliveryDialog = false, assignOrderId = null) }
            orderRepository.assignDeliveryUser(orderId, deliveryUserId).onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
}
