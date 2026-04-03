package net.marllex.waselak.admin.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.marllex.waselak.admin.network.*
import net.marllex.waselak.admin.ui.components.DateRange
import net.marllex.waselak.admin.ui.components.DateRangePeriod
import net.marllex.waselak.admin.util.UiMessage
import waselak.app_admin.generated.resources.Res
import waselak.app_admin.generated.resources.*
import net.marllex.waselak.core.common.crash.CrashReporter

class VendorDetailViewModel(private val apiClient: AdminApiClient) : ViewModel() {
    private val _detail = MutableStateFlow<VendorDetailDto?>(null)
    val detail: StateFlow<VendorDetailDto?> = _detail.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _error = MutableStateFlow<UiMessage?>(null)
    val error: StateFlow<UiMessage?> = _error.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    // ── Tab state ────────────────────────────────────────────────────────
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // ── Analytics data (lazy-loaded per tab) ─────────────────────────────
    private val _executiveSummary = MutableStateFlow<ExecutiveSummaryDto?>(null)
    val executiveSummary: StateFlow<ExecutiveSummaryDto?> = _executiveSummary.asStateFlow()

    private val _revenueProfit = MutableStateFlow<RevenueProfitDto?>(null)
    val revenueProfit: StateFlow<RevenueProfitDto?> = _revenueProfit.asStateFlow()

    private val _ordersIntelligence = MutableStateFlow<OrdersIntelligenceDto?>(null)
    val ordersIntelligence: StateFlow<OrdersIntelligenceDto?> = _ordersIntelligence.asStateFlow()

    private val _peakTimes = MutableStateFlow<PeakTimeAnalysisDto?>(null)
    val peakTimes: StateFlow<PeakTimeAnalysisDto?> = _peakTimes.asStateFlow()

    private val _cashierPerformance = MutableStateFlow<List<CashierPerformanceDto>>(emptyList())
    val cashierPerformance: StateFlow<List<CashierPerformanceDto>> = _cashierPerformance.asStateFlow()

    private val _deliveryPerformance = MutableStateFlow<List<DeliveryPerformanceDto>>(emptyList())
    val deliveryPerformance: StateFlow<List<DeliveryPerformanceDto>> = _deliveryPerformance.asStateFlow()

    private val _productIntelligence = MutableStateFlow<ProductIntelligenceDto?>(null)
    val productIntelligence: StateFlow<ProductIntelligenceDto?> = _productIntelligence.asStateFlow()

    private val _customerIntelligence = MutableStateFlow<CustomerIntelligenceDto?>(null)
    val customerIntelligence: StateFlow<CustomerIntelligenceDto?> = _customerIntelligence.asStateFlow()

    private val _stockOverview = MutableStateFlow<StockOverviewDto?>(null)
    val stockOverview: StateFlow<StockOverviewDto?> = _stockOverview.asStateFlow()

    private val _offersAnalytics = MutableStateFlow<OffersAnalyticsDto?>(null)
    val offersAnalytics: StateFlow<OffersAnalyticsDto?> = _offersAnalytics.asStateFlow()

    private val _discountAnalytics = MutableStateFlow<DiscountAnalyticsDto?>(null)
    val discountAnalytics: StateFlow<DiscountAnalyticsDto?> = _discountAnalytics.asStateFlow()

    private val _loyaltyAnalytics = MutableStateFlow<LoyaltyAnalyticsDto?>(null)
    val loyaltyAnalytics: StateFlow<LoyaltyAnalyticsDto?> = _loyaltyAnalytics.asStateFlow()

    private val _alerts = MutableStateFlow<AlertsResponseDto?>(null)
    val alerts: StateFlow<AlertsResponseDto?> = _alerts.asStateFlow()

    // Track which tabs have been loaded
    private val loadedTabs = mutableSetOf<Int>()

    private val _tabLoading = MutableStateFlow(false)
    val tabLoading: StateFlow<Boolean> = _tabLoading.asStateFlow()

    // ── Date Range for analytics ───────────────────────────────────────
    private val _selectedPeriod = MutableStateFlow(DateRangePeriod.LAST_30_DAYS)
    val selectedPeriod: StateFlow<DateRangePeriod> = _selectedPeriod.asStateFlow()

    // ── Orders, Customers, Workers (for detail tabs) ─────────────────────
    private val _orders = MutableStateFlow<CmsOrderListResponse?>(null)
    val orders: StateFlow<CmsOrderListResponse?> = _orders.asStateFlow()

    private val _customers = MutableStateFlow<CmsCustomerListResponse?>(null)
    val customers: StateFlow<CmsCustomerListResponse?> = _customers.asStateFlow()

    private val _workers = MutableStateFlow<CmsWorkerListResponse?>(null)
    val workers: StateFlow<CmsWorkerListResponse?> = _workers.asStateFlow()

    // ── Order detail (dialog) ──────────────────────────────────────────
    private val _orderDetail = MutableStateFlow<CmsOrderDetailDto?>(null)
    val orderDetail: StateFlow<CmsOrderDetailDto?> = _orderDetail.asStateFlow()

    private val _orderDetailLoading = MutableStateFlow(false)
    val orderDetailLoading: StateFlow<Boolean> = _orderDetailLoading.asStateFlow()

    fun clearMessage() { _message.value = null }

    fun loadVendorDetail(vendorId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = apiClient.getVendorDetail(vendorId)
                if (result != null) {
                    _detail.value = result
                } else {
                    _error.value = UiMessage.Resource(Res.string.error_loading_vendor)
                }
            } catch (e: Exception) {
                _error.value = if (e.message != null) UiMessage.Text(e.message!!) else UiMessage.Resource(Res.string.unknown_error)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectTab(vendorId: String, tabIndex: Int) {
        _selectedTab.value = tabIndex
        if (tabIndex !in loadedTabs) {
            loadAnalyticsTab(vendorId, tabIndex)
        }
    }

    private fun loadAnalyticsTab(vendorId: String, tabIndex: Int) {
        viewModelScope.launch {
            _tabLoading.value = true
            val range = getDateRange()
            val from = range.from?.toEpochMilliseconds()
            val to = range.to?.toEpochMilliseconds()
            try {
                when (tabIndex) {
                    1 -> { // Revenue & Orders
                        _executiveSummary.value = apiClient.getVendorExecutiveSummary(vendorId, from, to)
                        _revenueProfit.value = apiClient.getVendorRevenueProfit(vendorId, from, to)
                        _ordersIntelligence.value = apiClient.getVendorOrdersIntelligence(vendorId, from, to)
                    }
                    2 -> { // Peak Times
                        _peakTimes.value = apiClient.getVendorPeakTimes(vendorId, from, to)
                    }
                    3 -> { // Staff
                        _cashierPerformance.value = apiClient.getVendorCashierPerformance(vendorId, from, to)
                        _deliveryPerformance.value = apiClient.getVendorDeliveryPerformance(vendorId, from, to)
                    }
                    4 -> { // Products
                        _productIntelligence.value = apiClient.getVendorProductIntelligence(vendorId, from, to)
                    }
                    5 -> { // Customers
                        _customerIntelligence.value = apiClient.getVendorCustomerIntelligence(vendorId, from, to)
                        _customers.value = apiClient.getVendorCustomers(vendorId)
                    }
                    6 -> { // Stock (no date range — stock is current snapshot)
                        _stockOverview.value = apiClient.getVendorStockOverview(vendorId)
                    }
                    7 -> { // Offers & Discounts
                        _offersAnalytics.value = apiClient.getVendorOffersAnalytics(vendorId, from, to)
                        _discountAnalytics.value = apiClient.getVendorDiscountAnalytics(vendorId, from, to)
                        _loyaltyAnalytics.value = apiClient.getVendorLoyaltyAnalytics(vendorId, from, to)
                    }
                    8 -> { // Alerts
                        _alerts.value = apiClient.getVendorAlerts(vendorId, from, to)
                    }
                    9 -> { // Orders list
                        _orders.value = apiClient.getVendorOrders(vendorId)
                    }
                    10 -> { // Workers
                        _workers.value = apiClient.getVendorWorkers(vendorId)
                    }
                }
                loadedTabs.add(tabIndex)
            } catch (e: Exception) {
                // Tab loading failed, can retry
            } finally {
                _tabLoading.value = false
            }
        }
    }

    fun changeDateRange(vendorId: String, period: DateRangePeriod) {
        _selectedPeriod.value = period
        // Clear cached analytics so tabs reload with new date range
        val currentTab = _selectedTab.value
        if (currentTab in 1..8) {
            loadedTabs.removeAll(setOf(1, 2, 3, 4, 5, 6, 7, 8)) // Clear all analytics tabs
            loadAnalyticsTab(vendorId, currentTab)
        }
    }

    private fun getDateRange(): DateRange = DateRange.forPeriod(_selectedPeriod.value)

    fun loadMoreOrders(vendorId: String, page: Int, status: String? = null, channel: String? = null, search: String? = null) {
        viewModelScope.launch {
            _tabLoading.value = true
            try {
                _orders.value = apiClient.getVendorOrders(vendorId, page = page, status = status, channel = channel, search = search)
            } finally {
                _tabLoading.value = false
            }
        }
    }

    fun loadOrderDetail(vendorId: String, orderId: String) {
        viewModelScope.launch {
            _orderDetailLoading.value = true
            try {
                _orderDetail.value = apiClient.getVendorOrderDetail(vendorId, orderId)
            } finally {
                _orderDetailLoading.value = false
            }
        }
    }

    fun clearOrderDetail() {
        _orderDetail.value = null
    }

    fun loadMoreCustomers(vendorId: String, page: Int, search: String? = null, sortBy: String = "total_spent") {
        viewModelScope.launch {
            _tabLoading.value = true
            try {
                _customers.value = apiClient.getVendorCustomers(vendorId, page = page, search = search, sortBy = sortBy)
            } finally {
                _tabLoading.value = false
            }
        }
    }

    fun updateVendor(vendorId: String, request: UpdateVendorRequest) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val success = apiClient.updateVendor(vendorId, request)
                if (success) {
                    _message.value = UiMessage.Resource(Res.string.vendor_updated, isSuccess = true)
                    loadVendorDetail(vendorId)
                } else {
                    _message.value = UiMessage.Resource(Res.string.vendor_update_failed)
                }
            } catch (e: Exception) {
                _message.value = UiMessage.Text(e.message ?: "")
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun changePlan(vendorId: String, plan: String) {
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val success = apiClient.changeVendorPlan(vendorId, plan, null)
                if (success) {
                    _message.value = UiMessage.Text("Plan changed to $plan — feature defaults applied")
                    loadVendorDetail(vendorId) // Reload to get updated flags
                } else {
                    _message.value = UiMessage.Text("Failed to change plan")
                }
            } catch (e: Exception) {
                _message.value = UiMessage.Text(e.message ?: "Error changing plan")
            } finally {
                _isSaving.value = false
            }
        }
    }

    // ── User management ──────────────────────────────────────────────────
    fun createUser(vendorId: String, name: String, phone: String, password: String, role: String, email: String?) {
        viewModelScope.launch {
            val success = apiClient.createVendorUser(vendorId, name, phone, password, role, email)
            if (success) {
                _message.value = UiMessage.Resource(Res.string.user_created_success, isSuccess = true)
                loadVendorDetail(vendorId)
            } else {
                _message.value = UiMessage.Resource(Res.string.user_create_failed)
            }
        }
    }

    fun resetUserPassword(vendorId: String, userId: String, newPassword: String) {
        viewModelScope.launch {
            val success = apiClient.resetVendorUserPassword(vendorId, userId, newPassword)
            _message.value = if (success) UiMessage.Resource(Res.string.password_reset_success, isSuccess = true) else UiMessage.Resource(Res.string.password_reset_failed)
        }
    }

    fun deactivateUser(vendorId: String, userId: String) {
        viewModelScope.launch {
            val success = apiClient.deactivateVendorUser(vendorId, userId)
            if (success) {
                _message.value = UiMessage.Resource(Res.string.user_deactivated, isSuccess = true)
                loadVendorDetail(vendorId)
            } else {
                _message.value = UiMessage.Resource(Res.string.user_deactivate_failed)
            }
        }
    }

    fun retry(vendorId: String) {
        loadedTabs.clear()
        loadVendorDetail(vendorId)
    }
}
