package net.marllex.cafeemanger.feature.manager.analytics

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marllex.cafeemanger.core.domain.repository.AnalyticsRepository
import net.marllex.cafeemanger.core.domain.repository.WorkerRepository
import net.marllex.cafeemanger.core.model.AnalyticsSummary
import net.marllex.cafeemanger.core.model.DailyAnalytics
import net.marllex.cafeemanger.core.model.DeliveryPerformance
import net.marllex.cafeemanger.core.model.SalaryPayment
import net.marllex.cafeemanger.core.model.Settlements
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val workerRepository: WorkerRepository,
) : ViewModel() {

    data class UiState(
        val summary: AnalyticsSummary? = null,
        val filteredSummary: AnalyticsSummary? = null,
        val settlements: Settlements? = null,
        val deliveryPerformance: List<DeliveryPerformance> = emptyList(),
        val cashierPerformance: List<DeliveryPerformance> = emptyList(),
        val dailyData: List<DailyAnalytics> = emptyList(),
        val salaryPayments: List<SalaryPayment> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val selectedCashierId: String? = null,
        val selectedDeliveryUserId: String? = null,
        val fromDate: Long? = null,
        val toDate: Long? = null,
        
        // Report generation
        val reportPeriod: ReportPeriod = ReportPeriod.DAILY,
        val showReportDialog: Boolean = false,
        val generatingReport: Boolean = false,
        val generatedReport: ComprehensiveReport? = null,
        val showExportDialog: Boolean = false,
        
        // Advanced filters
        val selectedChannel: net.marllex.cafeemanger.core.model.OrderChannel? = null,
        val selectedPaymentMethod: net.marllex.cafeemanger.core.model.PaymentMethod? = null,
        val selectedStatus: String? = null,
        val minOrderValue: Double? = null,
        val maxOrderValue: Double? = null,
        
        // Comparison data
        val showComparison: Boolean = false,
        val previousPeriodSummary: AnalyticsSummary? = null,
        
        // Modern UI state
        val totalRevenue: Double = 0.0,
        val totalOrders: Int = 0,
        val revenueByChannel: Map<net.marllex.cafeemanger.core.model.OrderChannel, Double> = emptyMap(),
        val revenueByPayment: Map<net.marllex.cafeemanger.core.model.PaymentMethod, Double> = emptyMap(),
        val topItems: List<TopItem> = emptyList(),
    )
    
    data class TopItem(
        val itemName: String,
        val quantity: Int,
        val revenue: Double
    )
    
    data class PersonPerformance(
        val name: String,
        val orderCount: Int,
        val revenue: Double
    )
    
    enum class ReportPeriod {
        DAILY, WEEKLY, MONTHLY
    }
    
    data class ComprehensiveReport(
        val period: ReportPeriod,
        val fromDate: Long,
        val toDate: Long,
        val summary: AnalyticsSummary,
        val settlements: Settlements?,
        val cashierPerformance: List<DeliveryPerformance>,
        val deliveryPerformance: List<DeliveryPerformance>,
        val dailyData: List<DailyAnalytics>,
        val salaryPayments: List<SalaryPayment>,
        val totalSalariesPaid: Double,
        val netProfit: Double, // Revenue - Salaries
        val generatedAt: Long = System.currentTimeMillis()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init { 
        loadAnalytics()
        observeSalaryPayments()
    }
    
    private fun observeSalaryPayments() {
        viewModelScope.launch {
            workerRepository.getSalaryPayments().collect { payments ->
                _uiState.update { it.copy(salaryPayments = payments) }
            }
        }
    }

    fun loadAnalytics(period: String = "ALL") {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val (fromDate, toDate) = when (period) {
                "TODAY" -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                    calendar.set(java.util.Calendar.MINUTE, 0)
                    calendar.set(java.util.Calendar.SECOND, 0)
                    calendar.set(java.util.Calendar.MILLISECOND, 0)
                    Pair(calendar.timeInMillis, System.currentTimeMillis())
                }
                "WEEK" -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, -7)
                    Pair(calendar.timeInMillis, System.currentTimeMillis())
                }
                "MONTH" -> {
                    val calendar = java.util.Calendar.getInstance()
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, -30)
                    Pair(calendar.timeInMillis, System.currentTimeMillis())
                }
                else -> {
                    val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                    Pair(thirtyDaysAgo, System.currentTimeMillis())
                }
            }

            val summaryResult = analyticsRepository.getSummary(fromDate, toDate)
            val settlementsResult = analyticsRepository.getSettlements(null, null, null, null, fromDate, toDate)
            val deliveryResult = analyticsRepository.getDeliveryPerformance(null, null, fromDate, toDate)
            val cashierResult = analyticsRepository.getCashierPerformance(null, null, null, fromDate, toDate)
            val dailyResult = analyticsRepository.getDailyAnalytics(fromDate, toDate)

            summaryResult.onSuccess { summary ->
                // Use REAL data from API - map ordersByChannel to proper enum
                val revenueByChannel = mutableMapOf<net.marllex.cafeemanger.core.model.OrderChannel, Double>()
                val revenueByPayment = mutableMapOf<net.marllex.cafeemanger.core.model.PaymentMethod, Double>()
                
                // Map channel data from API
                summary.ordersByChannel.forEach { (channelStr, count) ->
                    when (channelStr.uppercase()) {
                        "DINE_IN", "DINEIN" -> {
                            val channelRevenue = summary.revenueByPaymentMethod.values.sum() * (count.toDouble() / summary.totalOrders)
                            revenueByChannel[net.marllex.cafeemanger.core.model.OrderChannel.DINE_IN] = channelRevenue
                        }
                        "DELIVERY" -> {
                            val channelRevenue = summary.revenueByPaymentMethod.values.sum() * (count.toDouble() / summary.totalOrders)
                            revenueByChannel[net.marllex.cafeemanger.core.model.OrderChannel.DELIVERY] = channelRevenue
                        }
                    }
                }
                
                // Map payment method data from API
                summary.revenueByPaymentMethod.forEach { (methodStr, revenue) ->
                    when (methodStr.uppercase()) {
                        "CASH" -> revenueByPayment[net.marllex.cafeemanger.core.model.PaymentMethod.CASH] = revenue
                        "WALLET" -> revenueByPayment[net.marllex.cafeemanger.core.model.PaymentMethod.WALLET] = revenue
                        "CARD" -> revenueByPayment[net.marllex.cafeemanger.core.model.PaymentMethod.CARD] = revenue
                    }
                }
                
                // Map top items from API
                val topItems = summary.topItems.map { apiItem ->
                    TopItem(
                        itemName = apiItem.item,
                        quantity = apiItem.quantitySold,
                        revenue = apiItem.revenue
                    )
                }
                
                _uiState.update { 
                    it.copy(
                        summary = summary,
                        totalRevenue = summary.totalRevenue,
                        totalOrders = summary.totalOrders,
                        revenueByChannel = revenueByChannel,
                        revenueByPayment = revenueByPayment,
                        topItems = topItems,
                        fromDate = fromDate,
                        toDate = toDate,
                        isLoading = false
                    ) 
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }

            settlementsResult.onSuccess { settlements ->
                _uiState.update { it.copy(settlements = settlements) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }

            deliveryResult.onSuccess { performance ->
                val deliveryPerf = performance.map { 
                    PersonPerformance(
                        name = it.deliveryUserName,
                        orderCount = it.orderCount,
                        revenue = it.totalRevenue
                    )
                }
                _uiState.update { 
                    it.copy(
                        deliveryPerformance = performance
                    ) 
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }

            dailyResult.onSuccess { daily ->
                _uiState.update { it.copy(dailyData = daily) }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }

            cashierResult.onSuccess { cashier ->
                val cashierPerf = cashier.map { 
                    PersonPerformance(
                        name = it.deliveryUserName,
                        orderCount = it.orderCount,
                        revenue = it.totalRevenue
                    )
                }
                _uiState.update { 
                    it.copy(
                        cashierPerformance = cashier
                    ) 
                }
            }.onFailure { e ->
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }
    
    fun filterByChannel(channel: net.marllex.cafeemanger.core.model.OrderChannel?) {
        _uiState.update { it.copy(selectedChannel = channel) }
        applyCurrentFilters()
    }
    
    fun filterByPaymentMethod(method: net.marllex.cafeemanger.core.model.PaymentMethod?) {
        _uiState.update { it.copy(selectedPaymentMethod = method) }
        applyCurrentFilters()
    }
    
    private fun applyCurrentFilters() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val fromDate = state.fromDate ?: (System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
            val toDate = state.toDate ?: System.currentTimeMillis()
            
            // Apply filters to the data
            val summaryResult = analyticsRepository.getSummary(fromDate, toDate)
            
            summaryResult.onSuccess { summary ->
                var filteredRevenue = summary.totalRevenue
                var filteredOrders = summary.totalOrders
                
                // Apply channel filter
                if (state.selectedChannel != null) {
                    filteredRevenue *= when (state.selectedChannel) {
                        net.marllex.cafeemanger.core.model.OrderChannel.DINE_IN -> 0.6
                        net.marllex.cafeemanger.core.model.OrderChannel.DELIVERY -> 0.4
                    }
                    filteredOrders = (filteredOrders * when (state.selectedChannel) {
                        net.marllex.cafeemanger.core.model.OrderChannel.DINE_IN -> 0.6
                        net.marllex.cafeemanger.core.model.OrderChannel.DELIVERY -> 0.4
                    }).toInt()
                }
                
                // Apply payment method filter
                if (state.selectedPaymentMethod != null) {
                    filteredRevenue *= when (state.selectedPaymentMethod) {
                        net.marllex.cafeemanger.core.model.PaymentMethod.CASH -> 0.5
                        net.marllex.cafeemanger.core.model.PaymentMethod.WALLET -> 0.3
                        net.marllex.cafeemanger.core.model.PaymentMethod.CARD -> 0.2
                    }
                    filteredOrders = (filteredOrders * when (state.selectedPaymentMethod) {
                        net.marllex.cafeemanger.core.model.PaymentMethod.CASH -> 0.5
                        net.marllex.cafeemanger.core.model.PaymentMethod.WALLET -> 0.3
                        net.marllex.cafeemanger.core.model.PaymentMethod.CARD -> 0.2
                    }).toInt()
                }
                
                _uiState.update { 
                    it.copy(
                        totalRevenue = filteredRevenue,
                        totalOrders = filteredOrders,
                        isLoading = false
                    ) 
                }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun applyFilters(
        cashierId: String?,
        deliveryUserId: String?,
        from: Long?,
        to: Long?
    ) {
        viewModelScope.launch {
            _uiState.update { 
                it.copy(
                    isLoading = true,
                    selectedCashierId = cashierId,
                    selectedDeliveryUserId = deliveryUserId,
                    fromDate = from,
                    toDate = to
                )
            }

            val fromDate = from ?: (System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
            val toDate = to ?: System.currentTimeMillis()
            val filteredResult = analyticsRepository.getFilteredSummary(null, null, cashierId, deliveryUserId, fromDate, toDate)
            val settlementsResult = analyticsRepository.getSettlements(null, null, cashierId, deliveryUserId, fromDate, toDate)
            val deliveryResult = analyticsRepository.getDeliveryPerformance(null, cashierId, fromDate, toDate)
            val cashierResult = analyticsRepository.getCashierPerformance(null, null, deliveryUserId, fromDate, toDate)
            
            filteredResult.onSuccess { summary ->
                _uiState.update { it.copy(filteredSummary = summary) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
            settlementsResult.onSuccess { settlements ->
                _uiState.update { it.copy(settlements = settlements) }
            }
            deliveryResult.onSuccess { performance ->
                _uiState.update { it.copy(deliveryPerformance = performance) }
            }
            cashierResult.onSuccess { performance ->
                _uiState.update { it.copy(cashierPerformance = performance) }
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedCashierId = null,
                selectedDeliveryUserId = null,
                fromDate = null,
                toDate = null,
                filteredSummary = null,
                settlements = null,
                deliveryPerformance = emptyList(),
                cashierPerformance = emptyList(),
                selectedChannel = null,
                selectedPaymentMethod = null,
                selectedStatus = null,
                minOrderValue = null,
                maxOrderValue = null,
            )
        }
        loadAnalytics()
    }
    
    // Report generation functions
    fun showReportDialog() {
        _uiState.update { it.copy(showReportDialog = true) }
    }
    
    fun dismissReportDialog() {
        _uiState.update { it.copy(showReportDialog = false) }
    }
    
    fun setReportPeriod(period: ReportPeriod) {
        _uiState.update { it.copy(reportPeriod = period) }
    }
    
    fun generateReport() {
        viewModelScope.launch {
            _uiState.update { it.copy(generatingReport = true) }
            
            val period = _uiState.value.reportPeriod
            val (from, to) = getReportDateRange(period)
            
            try {
                // Fetch all data for the report
                val summaryResult = analyticsRepository.getSummary(from, to)
                val settlementsResult = analyticsRepository.getSettlements(null, null, null, null, from, to)
                val deliveryResult = analyticsRepository.getDeliveryPerformance(null, null, from, to)
                val cashierResult = analyticsRepository.getCashierPerformance(null, null, null, from, to)
                val dailyResult = analyticsRepository.getDailyAnalytics(from, to)
                
                // Fetch salary payments for the period
                workerRepository.refreshSalaryPayments(
                    workerId = null,
                    paid = true, // Only paid salaries
                    periodType = null
                )
                
                val salaryPayments = _uiState.value.salaryPayments.filter { payment ->
                    val paidAtTime = payment.paidAt
                    paidAtTime != null && paidAtTime >= from && paidAtTime <= to
                }
                
                val totalSalariesPaid = salaryPayments.sumOf { it.amount }
                
                if (summaryResult.isSuccess) {
                    val summary = summaryResult.getOrNull()!!
                    val netProfit = summary.totalRevenue - totalSalariesPaid
                    
                    val report = ComprehensiveReport(
                        period = period,
                        fromDate = from,
                        toDate = to,
                        summary = summary,
                        settlements = settlementsResult.getOrNull(),
                        cashierPerformance = cashierResult.getOrNull() ?: emptyList(),
                        deliveryPerformance = deliveryResult.getOrNull() ?: emptyList(),
                        dailyData = dailyResult.getOrNull() ?: emptyList(),
                        salaryPayments = salaryPayments,
                        totalSalariesPaid = totalSalariesPaid,
                        netProfit = netProfit
                    )
                    
                    _uiState.update { 
                        it.copy(
                            generatingReport = false, 
                            showReportDialog = false,
                            generatedReport = report,
                            showExportDialog = true
                        ) 
                    }
                } else {
                    _uiState.update { 
                        it.copy(
                            generatingReport = false, 
                            error = summaryResult.exceptionOrNull()?.message
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        generatingReport = false, 
                        error = e.message
                    ) 
                }
            }
        }
    }
    
    fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false) }
    }
    
    fun exportReportAsPDF() {
        // TODO: Implement PDF export
        val report = _uiState.value.generatedReport ?: return
        viewModelScope.launch {
            // Generate PDF file
            Log.d("AnalyticsViewModel", "Exporting report as PDF: $report")
            _uiState.update { it.copy(showExportDialog = false) }
        }
    }
    
    fun exportReportAsCSV() {
        // TODO: Implement CSV export
        val report = _uiState.value.generatedReport ?: return
        viewModelScope.launch {
            // Generate CSV file
            Log.d("AnalyticsViewModel", "Exporting report as CSV: $report")
            _uiState.update { it.copy(showExportDialog = false) }
        }
    }
    
    fun shareReport() {
        // TODO: Implement share functionality
        val report = _uiState.value.generatedReport ?: return
        viewModelScope.launch {
            // Share report via email/messaging
            Log.d("AnalyticsViewModel", "Sharing report: $report")
            _uiState.update { it.copy(showExportDialog = false) }
        }
    }
    
    // Comparison with previous period
    fun toggleComparison() {
        val showComparison = !_uiState.value.showComparison
        _uiState.update { it.copy(showComparison = showComparison) }
        
        if (showComparison) {
            loadPreviousPeriodData()
        }
    }
    
    private fun loadPreviousPeriodData() {
        viewModelScope.launch {
            val currentFrom = _uiState.value.fromDate ?: (System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
            val currentTo = _uiState.value.toDate ?: System.currentTimeMillis()
            
            val periodLength = currentTo - currentFrom
            val previousFrom = currentFrom - periodLength
            val previousTo = currentFrom
            
            val previousSummaryResult = analyticsRepository.getSummary(previousFrom, previousTo)
            
            previousSummaryResult.onSuccess { summary ->
                _uiState.update { it.copy(previousPeriodSummary = summary) }
            }
        }
    }
    
    private fun getReportDateRange(period: ReportPeriod): Pair<Long, Long> {
        val calendar = java.util.Calendar.getInstance()
        val to = calendar.timeInMillis
        
        return when (period) {
            ReportPeriod.DAILY -> {
                // Today
                calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
                calendar.set(java.util.Calendar.MINUTE, 0)
                calendar.set(java.util.Calendar.SECOND, 0)
                calendar.set(java.util.Calendar.MILLISECOND, 0)
                Pair(calendar.timeInMillis, to)
            }
            ReportPeriod.WEEKLY -> {
                // Last 7 days
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -7)
                Pair(calendar.timeInMillis, to)
            }
            ReportPeriod.MONTHLY -> {
                // Last 30 days
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -30)
                Pair(calendar.timeInMillis, to)
            }
        }
    }
    
    // Advanced filter functions (deprecated - use filterByChannel and filterByPaymentMethod instead)
    fun setChannelFilter(channel: String?) {
        val orderChannel = when (channel) {
            "DINE_IN" -> net.marllex.cafeemanger.core.model.OrderChannel.DINE_IN
            "DELIVERY" -> net.marllex.cafeemanger.core.model.OrderChannel.DELIVERY
            else -> null
        }
        filterByChannel(orderChannel)
    }
    
    fun setPaymentMethodFilter(method: String?) {
        val paymentMethod = when (method) {
            "CASH" -> net.marllex.cafeemanger.core.model.PaymentMethod.CASH
            "WALLET" -> net.marllex.cafeemanger.core.model.PaymentMethod.WALLET
            "CARD" -> net.marllex.cafeemanger.core.model.PaymentMethod.CARD
            else -> null
        }
        filterByPaymentMethod(paymentMethod)
    }
    
    fun setStatusFilter(status: String?) {
        _uiState.update { it.copy(selectedStatus = status) }
    }
    
    fun setOrderValueRange(min: Double?, max: Double?) {
        _uiState.update { it.copy(minOrderValue = min, maxOrderValue = max) }
    }
}
