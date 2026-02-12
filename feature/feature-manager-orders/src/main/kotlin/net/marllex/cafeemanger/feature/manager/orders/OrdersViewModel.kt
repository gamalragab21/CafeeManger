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
import net.marllex.cafeemanger.core.model.OrderChannel
import net.marllex.cafeemanger.core.model.OrderItem
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.model.UserRole
import net.marllex.cafeemanger.core.network.dto.CreateOrderItemRequest
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
        // Edit order dialog
        val showEditOrderDialog: Boolean = false,
        val editingOrder: Order? = null,
        val editItems: List<OrderItem> = emptyList(),
        val editClientName: String = "",
        val editClientPhone: String = "",
        val editClientAddress: String = "",
        val editNotes: String = "",
        val isEditSaving: Boolean = false,
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
        val currentStatus = _uiState.value.selectedStatus
        // Clear status filter if it's incompatible with new channel
        val resetStatus = if (currentStatus != null && channel != null) {
            val availableStatuses = when (channel) {
                "DINE_IN" -> OrderStatus.getAvailableStatuses(OrderChannel.DINE_IN)
                "DELIVERY" -> OrderStatus.getAvailableStatuses(OrderChannel.DELIVERY)
                else -> OrderStatus.entries.toList()
            }
            if (availableStatuses.none { it.name == currentStatus }) null else currentStatus
        } else currentStatus
        _uiState.update { it.copy(selectedChannel = channel, selectedStatus = resetStatus) }
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

    // ─── Edit Order ────────────────────────────────────────────────
    fun showEditOrder(order: Order) {
        _uiState.update {
            it.copy(
                showEditOrderDialog = true,
                editingOrder = order,
                editItems = order.items,
                editClientName = order.clientName ?: "",
                editClientPhone = order.clientPhone ?: "",
                editClientAddress = order.clientAddress ?: "",
                editNotes = order.notes ?: "",
            )
        }
    }

    fun dismissEditOrderDialog() {
        _uiState.update { it.copy(showEditOrderDialog = false, editingOrder = null) }
    }

    fun updateEditClientName(v: String) = _uiState.update { it.copy(editClientName = v) }
    fun updateEditClientPhone(v: String) = _uiState.update { it.copy(editClientPhone = v) }
    fun updateEditClientAddress(v: String) = _uiState.update { it.copy(editClientAddress = v) }
    fun updateEditNotes(v: String) = _uiState.update { it.copy(editNotes = v) }

    fun updateEditItemQuantity(itemId: String, quantity: Int) {
        _uiState.update { state ->
            if (quantity <= 0) {
                state.copy(editItems = state.editItems.filter { it.id != itemId })
            } else {
                state.copy(editItems = state.editItems.map {
                    if (it.id == itemId) it.copy(quantity = quantity) else it
                })
            }
        }
    }

    fun removeEditItem(itemId: String) {
        _uiState.update { state ->
            state.copy(editItems = state.editItems.filter { it.id != itemId })
        }
    }

    fun saveEditOrder() {
        val s = _uiState.value
        val order = s.editingOrder ?: return
        if (s.editItems.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditSaving = true) }
            orderRepository.updateOrder(
                id = order.id,
                clientName = s.editClientName.ifBlank { null },
                clientPhone = s.editClientPhone.ifBlank { null },
                clientAddress = if (order.channel == OrderChannel.DELIVERY) s.editClientAddress.ifBlank { null } else null,
                notes = s.editNotes.ifBlank { null },
                items = s.editItems.map {
                    CreateOrderItemRequest(
                        itemId = it.itemId,
                        quantity = it.quantity,
                        note = it.note,
                    )
                },
            ).onSuccess {
                _uiState.update { it.copy(isEditSaving = false, showEditOrderDialog = false, editingOrder = null) }
                loadOrders()
            }.onFailure { e ->
                _uiState.update { it.copy(isEditSaving = false, error = e.message) }
            }
        }
    }
}
