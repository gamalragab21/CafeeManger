package net.marllex.cafeemanger.feature.manager.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.OrderRepository
import net.marllex.cafeemanger.core.domain.repository.VendorRepository
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.Vendor
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vendorRepository: VendorRepository,
    private val orderRepository: OrderRepository,
) : ViewModel() {

    data class UiState(
        val vendor: Vendor? = null,
        val recentOrders: List<Order> = emptyList(),
        val activeOrdersCount: Int = 0,
        val todayOrdersCount: Int = 0,
        val todayRevenue: Double = 0.0,
        val isLoading: Boolean = true,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            vendorRepository.refreshVendor()
            orderRepository.refreshOrders()

            combine(
                vendorRepository.getMyVendor(),
                orderRepository.getOrders(),
            ) { vendor, orders ->
                val activeOrders = orders.filter { order ->
                    order.status.name !in listOf("COMPLETED", "CANCELED")
                }
                val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000))
                val todayOrders = orders.filter { it.createdAt >= todayStart }
                val todayRevenue = todayOrders
                    .filter { it.status.name == "COMPLETED" }
                    .sumOf { it.total }

                UiState(
                    vendor = vendor,
                    recentOrders = orders.take(10),
                    activeOrdersCount = activeOrders.size,
                    todayOrdersCount = todayOrders.size,
                    todayRevenue = todayRevenue,
                    isLoading = false,
                )
            }.catch { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
