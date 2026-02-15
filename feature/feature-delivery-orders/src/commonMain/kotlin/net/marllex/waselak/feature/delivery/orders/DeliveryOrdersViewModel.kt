package net.marllex.waselak.feature.delivery.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderStatus

class DeliveryOrdersViewModel constructor(
    private val orderRepository: OrderRepository,
    private val vendorRepository: VendorRepository,
) : ViewModel() {

    data class UiState(
        val orders: List<Order> = emptyList(),
        val availableOrders: List<Order> = emptyList(),
        val selectedTab: Int = 0, // 0=My Orders, 1=Available
        val selectedStatus: String? = null,
        val isLoading: Boolean = true,
        val error: String? = null,
        val isSharing: Boolean = false,
        val shareUrl: String? = null,
        val vendorName: String = "",
        val vendorLogoUrl: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadOrders() }

    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            // Load vendor info for logo
            vendorRepository.refreshVendor().onSuccess { vendor ->
                _uiState.update { it.copy(vendorName = vendor.name, vendorLogoUrl = vendor.logoUrl) }
            }
            // Refresh my orders from API
            orderRepository.refreshMyDeliveryOrders(_uiState.value.selectedStatus)
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            // Load available orders
            orderRepository.getAvailableDeliveryOrders()
                .onSuccess { available ->
                    _uiState.update { it.copy(availableOrders = available) }
                }
            // Then observe local DB for my orders
            orderRepository.getMyDeliveryOrders(_uiState.value.selectedStatus)
                .catch { e -> _uiState.update { it.copy(isLoading = false, error = e.message) } }
                .collect { orders -> _uiState.update { it.copy(orders = orders, isLoading = false) } }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun filterByStatus(status: String?) {
        _uiState.update { it.copy(selectedStatus = status) }
        loadOrders()
    }

    fun pickupOrder(orderId: String) {
        viewModelScope.launch {
            // Self-assign: the backend will use the authenticated user's ID
            orderRepository.assignDeliveryUser(orderId, "self")
                .onSuccess { loadOrders() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun updateStatus(orderId: String, newStatus: OrderStatus) {
        viewModelScope.launch {
            orderRepository.updateOrderStatus(orderId, newStatus)
                .onSuccess { loadOrders() }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun shareReceipt(orderId: String, onLink: (String) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSharing = true, error = null) }
            orderRepository.shareReceipt(orderId)
                .onSuccess { link ->
                    _uiState.update { it.copy(isSharing = false, shareUrl = link.url) }
                    onLink(link.url)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSharing = false, error = e.message) }
                }
        }
    }
}
