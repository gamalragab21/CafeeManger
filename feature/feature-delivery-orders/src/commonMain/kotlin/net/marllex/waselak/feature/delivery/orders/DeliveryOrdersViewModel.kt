package net.marllex.waselak.feature.delivery.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.model.PaymentStatus

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
        // Pagination
        val currentOffset: Int = 0,
        val totalCount: Int = 0,
        val hasMore: Boolean = false,
        val isLoadingMore: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { loadOrders() }

    fun loadOrders() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentOffset = 0) }
            // Load vendor info for logo
            vendorRepository.refreshVendor().onSuccess { vendor ->
                _uiState.update { it.copy(vendorName = vendor.name, vendorLogoUrl = vendor.logoUrl) }
            }
            // Refresh my orders from API with pagination
            orderRepository.refreshMyDeliveryOrders(_uiState.value.selectedStatus, limit = PAGE_SIZE, offset = 0)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            orders = result.data,
                            isLoading = false,
                            currentOffset = result.data.size,
                            totalCount = result.total,
                            hasMore = result.hasMore,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
            // Load available orders (separate, non-paginated)
            orderRepository.getAvailableDeliveryOrders()
                .onSuccess { available ->
                    _uiState.update { it.copy(availableOrders = available) }
                }
        }
    }

    fun loadMoreOrders() {
        val s = _uiState.value
        if (s.isLoadingMore || !s.hasMore) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            orderRepository.refreshMyDeliveryOrders(s.selectedStatus, limit = PAGE_SIZE, offset = s.currentOffset)
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            orders = it.orders + result.data,
                            isLoadingMore = false,
                            currentOffset = it.currentOffset + result.data.size,
                            totalCount = result.total,
                            hasMore = result.hasMore,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoadingMore = false, error = e.message) }
                }
        }
    }

    companion object {
        private const val PAGE_SIZE = 50
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

    fun confirmPayment(orderId: String) {
        viewModelScope.launch {
            orderRepository.updatePaymentStatus(orderId, PaymentStatus.PAID)
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
