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
import net.marllex.cafeemanger.core.domain.repository.StockRepository
import net.marllex.cafeemanger.core.domain.repository.VendorRepository
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.Stock
import net.marllex.cafeemanger.core.model.Vendor
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val vendorRepository: VendorRepository,
    private val orderRepository: OrderRepository,
    private val stockRepository: StockRepository,
) : ViewModel() {

    data class UiState(
        val vendor: Vendor? = null,
        val recentOrders: List<Order> = emptyList(),
        val activeOrdersCount: Int = 0,
        val todayOrdersCount: Int = 0,
        val todayRevenue: Double = 0.0,
        // Stock summary
        val totalStockItems: Int = 0,
        val lowStockCount: Int = 0,
        val outOfStockCount: Int = 0,
        val totalStockValue: Double = 0.0,
        // Detailed data for expandable cards
        val activeOrders: List<Order> = emptyList(),
        val todayOrders: List<Order> = emptyList(),
        val lowStockItems: List<Stock> = emptyList(),
        val outOfStockItems: List<Stock> = emptyList(),
        val todayRevenueByPayment: Map<String, Double> = emptyMap(),
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
            stockRepository.refreshStock()

            combine(
                vendorRepository.getMyVendor(),
                orderRepository.getOrders(),
                stockRepository.getAllStock(),
            ) { vendor, orders, stocks ->
                val activeOrders = orders.filter { order ->
                    order.status.name !in listOf("COMPLETED", "CANCELED")
                }
                val todayStart = System.currentTimeMillis() - (System.currentTimeMillis() % (24 * 60 * 60 * 1000))
                val todayOrders = orders.filter { it.createdAt >= todayStart }
                val completedToday = todayOrders.filter { it.status.name == "COMPLETED" }
                val todayRevenue = completedToday.sumOf { it.total }
                val todayRevenueByPayment = completedToday
                    .groupBy { it.paymentMethod.name }
                    .mapValues { (_, v) -> v.sumOf { it.total } }

                // Calculate stock summary
                val lowStockItems = stocks.filter { it.isLowStock && !it.isOutOfStock && it.alertEnabled }
                val outOfStockItems = stocks.filter { it.isOutOfStock && it.alertEnabled }
                val totalStockValue = stocks.sumOf { it.totalValue }

                UiState(
                    vendor = vendor,
                    recentOrders = orders.take(10),
                    activeOrdersCount = activeOrders.size,
                    todayOrdersCount = todayOrders.size,
                    todayRevenue = todayRevenue,
                    totalStockItems = stocks.size,
                    lowStockCount = lowStockItems.size,
                    outOfStockCount = outOfStockItems.size,
                    totalStockValue = totalStockValue,
                    activeOrders = activeOrders,
                    todayOrders = todayOrders,
                    lowStockItems = lowStockItems,
                    outOfStockItems = outOfStockItems,
                    todayRevenueByPayment = todayRevenueByPayment,
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
