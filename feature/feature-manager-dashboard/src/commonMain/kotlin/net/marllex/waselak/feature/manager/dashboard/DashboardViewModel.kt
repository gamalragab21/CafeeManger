package net.marllex.waselak.feature.manager.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import net.marllex.waselak.core.domain.repository.AnalyticsRepository
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.VendorRepository
import net.marllex.waselak.core.model.*
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.common.crash.CrashReporter

// ══════════════════════════════════════════════════════════════════════
// Section state — independent loading per section
// ══════════════════════════════════════════════════════════════════════

sealed class SectionState<out T> {
    data object Loading : SectionState<Nothing>()
    data class Success<T>(val data: T) : SectionState<T>()
    data class Error(val message: String) : SectionState<Nothing>()
}

// ══════════════════════════════════════════════════════════════════════
// UI State
// ══════════════════════════════════════════════════════════════════════

data class HomeDashboardUiState(
    // Branding
    val vendor: Vendor? = null,
    val userName: String? = null,
    // Section 1: Today Snapshot (KPI Cards)
    val executiveSummary: SectionState<ExecutiveSummary> = SectionState.Loading,
    // Section 2: Real-Time Alerts
    val alerts: SectionState<List<AnalyticsAlert>> = SectionState.Loading,
    // Section 3: Top Performance
    val topProducts: SectionState<List<ProductItem>> = SectionState.Loading,
    val bestCashier: SectionState<CashierPerformanceV2?> = SectionState.Loading,
    val bestDriver: SectionState<DeliveryPerformanceV2?> = SectionState.Loading,
    // Section 4: Stock Health
    val stockHealth: SectionState<StockOverview> = SectionState.Loading,
    // Section 5: Recent Orders (from local data)
    val recentOrders: List<Order> = emptyList(),
    // Global loading/error for initial data
    val isLoading: Boolean = true,
    val error: String? = null,
)

// ══════════════════════════════════════════════════════════════════════
// ViewModel
// ══════════════════════════════════════════════════════════════════════

class DashboardViewModel constructor(
    private val vendorRepository: VendorRepository,
    private val orderRepository: OrderRepository,
    private val authRepository: AuthRepository,
    private val analyticsRepository: AnalyticsRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Dashboard" }


    private val _uiState = MutableStateFlow(HomeDashboardUiState())
    val uiState: StateFlow<HomeDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboard()
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(userName = user?.name) }
            }
        }
    }

    fun loadDashboard() {
        AppLogger.d(TAG, "loadDashboard called")
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // Load vendor info
            try {
                vendorRepository.refreshVendor()
                vendorRepository.getMyVendor().collect { vendor ->
                    _uiState.update { it.copy(vendor = vendor, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // Load recent orders (local)
        viewModelScope.launch {
            try {
                orderRepository.refreshOrders()
                orderRepository.getOrders().collect { orders ->
                    _uiState.update {
                        it.copy(
                            recentOrders = orders.take(10),
                            isLoading = false,
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }

        // Calculate today's date range
        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val todayStart = now.toLocalDateTime(tz).date.atStartOfDayIn(tz).toEpochMilliseconds()
        val todayEnd = now.toEpochMilliseconds()

        // Load analytics sections in parallel — each section independent
        viewModelScope.launch { loadExecutiveSummary(todayStart, todayEnd) }
        viewModelScope.launch { loadAlerts(todayStart, todayEnd) }
        viewModelScope.launch { loadTopProducts(todayStart, todayEnd) }
        viewModelScope.launch { loadBestCashier(todayStart, todayEnd) }
        viewModelScope.launch { loadBestDriver(todayStart, todayEnd) }
        viewModelScope.launch { loadStockHealth() }
    }

    // ── Section loaders ────────────────────────────────────────────────

    private suspend fun loadExecutiveSummary(from: Long, to: Long) {
        _uiState.update { it.copy(executiveSummary = SectionState.Loading) }
        analyticsRepository.getExecutiveSummary(from, to)
            .onSuccess { data ->
                    AppLogger.i(TAG, "Data loaded successfully")
                _uiState.update { it.copy(executiveSummary = SectionState.Success(data)) }
            }
            .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e)
                _uiState.update {
                    it.copy(executiveSummary = SectionState.Error(e.message ?: "Failed to load"))
                }
            }
    }

    private suspend fun loadAlerts(from: Long, to: Long) {
        _uiState.update { it.copy(alerts = SectionState.Loading) }
        analyticsRepository.getAnalyticsAlerts(from, to)
            .onSuccess { data ->
                _uiState.update { it.copy(alerts = SectionState.Success(data)) }
            }
            .onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update {
                    it.copy(alerts = SectionState.Error(e.message ?: "Failed to load"))
                }
            }
    }

    private suspend fun loadTopProducts(from: Long, to: Long) {
        _uiState.update { it.copy(topProducts = SectionState.Loading) }
        analyticsRepository.getProductIntelligence(from, to, limit = 5)
            .onSuccess { data ->
                _uiState.update {
                    it.copy(topProducts = SectionState.Success(data.topSelling.take(5)))
                }
            }
            .onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update {
                    it.copy(topProducts = SectionState.Error(e.message ?: "Failed to load"))
                }
            }
    }

    private suspend fun loadBestCashier(from: Long, to: Long) {
        _uiState.update { it.copy(bestCashier = SectionState.Loading) }
        analyticsRepository.getCashierPerformanceV2(from, to)
            .onSuccess { cashiers ->
                val best = cashiers.maxByOrNull { it.revenue }
                _uiState.update { it.copy(bestCashier = SectionState.Success(best)) }
            }
            .onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update {
                    it.copy(bestCashier = SectionState.Error(e.message ?: "Failed to load"))
                }
            }
    }

    private suspend fun loadBestDriver(from: Long, to: Long) {
        _uiState.update { it.copy(bestDriver = SectionState.Loading) }
        analyticsRepository.getDeliveryPerformanceV2(from, to)
            .onSuccess { drivers ->
                val best = drivers.maxByOrNull { it.ordersCompleted }
                _uiState.update { it.copy(bestDriver = SectionState.Success(best)) }
            }
            .onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update {
                    it.copy(bestDriver = SectionState.Error(e.message ?: "Failed to load"))
                }
            }
    }

    private suspend fun loadStockHealth() {
        _uiState.update { it.copy(stockHealth = SectionState.Loading) }
        analyticsRepository.getStockOverview()
            .onSuccess { data ->
                _uiState.update { it.copy(stockHealth = SectionState.Success(data)) }
            }
            .onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update {
                    it.copy(stockHealth = SectionState.Error(e.message ?: "Failed to load"))
                }
            }
    }
}
