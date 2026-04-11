package net.marllex.waselak.feature.manager.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import net.marllex.waselak.core.domain.repository.AnalyticsRepository
import net.marllex.waselak.core.domain.repository.InstallmentRepository
import net.marllex.waselak.core.model.*
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.userFriendlyMessage
import net.marllex.waselak.core.common.crash.CrashReporter

class AnalyticsViewModel(
    private val analyticsRepository: AnalyticsRepository,
    private val installmentRepository: InstallmentRepository,
) : ViewModel() {
    private companion object { private const val TAG = "Analytics" }


    // ── Section loading state ────────────────────────────────────────
    sealed class SectionState<out T> {
        data object Loading : SectionState<Nothing>()
        data class Success<T>(val data: T) : SectionState<T>()
        data class Error(val message: String) : SectionState<Nothing>()
    }

    // ── Time period filter ───────────────────────────────────────────
    enum class TimePeriod {
        TODAY, YESTERDAY, THIS_WEEK, LAST_7_DAYS, LAST_14_DAYS,
        THIS_MONTH, LAST_MONTH, LAST_3_MONTHS, CUSTOM
    }

    // ── Export state ─────────────────────────────────────────────────
    sealed class ExportState {
        data object Idle : ExportState()
        data object Exporting : ExportState()
        data class Done(val message: String) : ExportState()
        data class Failed(val message: String) : ExportState()
    }

    // ── Dashboard filters ───────────────────────────────────────────
    data class DashboardFilters(
        val timePeriod: TimePeriod = TimePeriod.LAST_7_DAYS,
        val fromDate: Long? = null,
        val toDate: Long? = null,
    )

    // ── Main UI state ───────────────────────────────────────────────
    data class DashboardUiState(
        val filters: DashboardFilters = DashboardFilters(),
        val executiveSummary: SectionState<ExecutiveSummary> = SectionState.Loading,
        val revenueProfit: SectionState<RevenueProfit> = SectionState.Loading,
        val ordersIntelligence: SectionState<OrdersIntelligence> = SectionState.Loading,
        val peakTimeAnalysis: SectionState<PeakTimeAnalysis> = SectionState.Loading,
        val cashierPerformance: SectionState<List<CashierPerformanceV2>> = SectionState.Loading,
        val deliveryPerformance: SectionState<List<DeliveryPerformanceV2>> = SectionState.Loading,
        val productIntelligence: SectionState<ProductIntelligence> = SectionState.Loading,
        val customerIntelligence: SectionState<CustomerIntelligence> = SectionState.Loading,
        val alerts: SectionState<List<AnalyticsAlert>> = SectionState.Loading,
        val stockOverview: SectionState<StockOverview> = SectionState.Loading,
        val offersAnalytics: SectionState<OffersAnalytics> = SectionState.Loading,
        val discountAnalytics: SectionState<DiscountAnalytics> = SectionState.Loading,
        val loyaltyAnalytics: SectionState<LoyaltyAnalytics> = SectionState.Loading,
        val staffCosts: SectionState<StaffCostsAnalytics> = SectionState.Loading,
        val supplierAnalytics: SectionState<SupplierAnalytics> = SectionState.Loading,
        val creditAnalytics: SectionState<CreditAnalytics> = SectionState.Loading,
        val doctorStats: SectionState<List<DoctorStats>> = SectionState.Loading,
        val returnsAnalytics: SectionState<ReturnsAnalytics> = SectionState.Loading,
        val installmentAnalytics: SectionState<InstallmentAnalytics> = SectionState.Loading,
        val exportState: ExportState = ExportState.Idle,
        val isFeatureGated: Boolean = false,
        val showFeatureNotAvailable: Boolean = false,
        val featureNotAvailableMessage: String = "",
    )

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadAllSections()
    }

    // ── Public actions ───────────────────────────────────────────────

    fun setTimePeriod(period: TimePeriod) {
        _uiState.update { it.copy(filters = it.filters.copy(timePeriod = period)) }
        loadAllSections()
    }

    fun setCustomDateRange(from: Long, to: Long) {
        _uiState.update {
            it.copy(
                filters = DashboardFilters(
                    timePeriod = TimePeriod.CUSTOM,
                    fromDate = from,
                    toDate = to,
                )
            )
        }
        loadAllSections()
    }

    fun retrySection(section: String) {
        AppLogger.d(TAG, "retrySection called")
        val (from, to) = getDateRange()
        viewModelScope.launch {
            when (section) {
                "executiveSummary" -> loadExecutiveSummary(from, to)
                "revenueProfit" -> loadRevenueProfit(from, to)
                "ordersIntelligence" -> loadOrdersIntelligence(from, to)
                "peakTimeAnalysis" -> loadPeakTimeAnalysis(from, to)
                "cashierPerformance" -> loadCashierPerformance(from, to)
                "deliveryPerformance" -> loadDeliveryPerformance(from, to)
                "productIntelligence" -> loadProductIntelligence(from, to)
                "customerIntelligence" -> loadCustomerIntelligence(from, to)
                "alerts" -> loadAlerts(from, to)
                "stockOverview" -> loadStockOverview()
                "offersAnalytics" -> loadOffersAnalytics(from, to)
                "discountAnalytics" -> loadDiscountAnalytics(from, to)
                "loyaltyAnalytics" -> loadLoyaltyAnalytics(from, to)
                "staffCosts" -> loadStaffCosts(from, to)
                "supplierAnalytics" -> loadSupplierAnalytics(from, to)
                "creditAnalytics" -> loadCreditAnalytics(from, to)
                "doctorStats" -> loadDoctorStats(from, to)
                "returnsAnalytics" -> loadReturnsAnalytics(from, to)
                "installmentAnalytics" -> loadInstallmentAnalytics(from, to)
            }
        }
    }

    fun exportPDF(fileSaver: (ByteArray, String) -> String) {
        val (from, to) = getDateRange()
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Exporting) }
            analyticsRepository.exportOrdersPDF(from, to)
                .onSuccess { bytes ->
                    try {
                        val fileName = buildExportFileName(from, to, "pdf")
                        val path = fileSaver(bytes, fileName)
                        AppLogger.i(TAG, "PDF exported successfully: $path")
                        _uiState.update { it.copy(exportState = ExportState.Done("PDF saved to Downloads")) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(exportState = ExportState.Failed("Save failed: ${e.message}")) }
                    }
                }
                .onFailure { e ->
                    CrashReporter.captureException(e)
                    AppLogger.e(TAG, "Load failed", e); _uiState.update { it.copy(exportState = ExportState.Failed(e.message ?: "Export failed")) } }
        }
    }

    fun exportExcel(fileSaver: (ByteArray, String) -> String) {
        val (from, to) = getDateRange()
        viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportState.Exporting) }
            analyticsRepository.exportOrdersExcel(from, to)
                .onSuccess { bytes ->
                    try {
                        val fileName = buildExportFileName(from, to, "xlsx")
                        val path = fileSaver(bytes, fileName)
                        _uiState.update { it.copy(exportState = ExportState.Done("Excel saved to Downloads")) }
                    } catch (e: Exception) {
                        _uiState.update { it.copy(exportState = ExportState.Failed("Save failed: ${e.message}")) }
                    }
                }
                .onFailure { e -> _uiState.update { it.copy(exportState = ExportState.Failed(e.message ?: "Export failed")) } }
        }
    }

    private fun buildExportFileName(from: Long, to: Long, extension: String): String {
        val tz = TimeZone.currentSystemDefault()
        val fromDate = Instant.fromEpochMilliseconds(from).toLocalDateTime(tz).date
        val toDate = Instant.fromEpochMilliseconds(to).toLocalDateTime(tz).date
        return "sales_report_${fromDate}_to_${toDate}.$extension"
    }

    fun clearExportState() {
        _uiState.update { it.copy(exportState = ExportState.Idle) }
    }

    fun dismissFeatureNotAvailable() {
        _uiState.update { it.copy(showFeatureNotAvailable = false) }
    }

    // ── Parallel loading ────────────────────────────────────────────

    fun loadAllSections() {
        val (from, to) = getDateRange()

        // Set all sections to loading
        _uiState.update {
            it.copy(
                executiveSummary = SectionState.Loading,
                revenueProfit = SectionState.Loading,
                ordersIntelligence = SectionState.Loading,
                peakTimeAnalysis = SectionState.Loading,
                cashierPerformance = SectionState.Loading,
                deliveryPerformance = SectionState.Loading,
                productIntelligence = SectionState.Loading,
                customerIntelligence = SectionState.Loading,
                alerts = SectionState.Loading,
                stockOverview = SectionState.Loading,
                offersAnalytics = SectionState.Loading,
                discountAnalytics = SectionState.Loading,
                loyaltyAnalytics = SectionState.Loading,
                staffCosts = SectionState.Loading,
                supplierAnalytics = SectionState.Loading,
                installmentAnalytics = SectionState.Loading,
            )
        }

        // Launch all sections in parallel
        viewModelScope.launch {
            val d1 = async { loadExecutiveSummary(from, to) }
            val d2 = async { loadRevenueProfit(from, to) }
            val d3 = async { loadOrdersIntelligence(from, to) }
            val d4 = async { loadPeakTimeAnalysis(from, to) }
            val d5 = async { loadCashierPerformance(from, to) }
            val d6 = async { loadDeliveryPerformance(from, to) }
            val d7 = async { loadProductIntelligence(from, to) }
            val d8 = async { loadCustomerIntelligence(from, to) }
            val d9 = async { loadAlerts(from, to) }
            val d10 = async { loadStockOverview() }
            val d11 = async { loadOffersAnalytics(from, to) }
            val d12 = async { loadDiscountAnalytics(from, to) }
            val d13 = async { loadLoyaltyAnalytics(from, to) }
            val d14 = async { loadStaffCosts(from, to) }
            val d15 = async { loadSupplierAnalytics(from, to) }
            val d16 = async { loadCreditAnalytics(from, to) }
            val d17 = async { loadDoctorStats(from, to) }
            val d18 = async { loadReturnsAnalytics(from, to) }
            val d19 = async { loadInstallmentAnalytics(from, to) }

            // Await all (each one updates state independently)
            d1.await(); d2.await(); d3.await(); d4.await(); d5.await()
            d6.await(); d7.await(); d8.await(); d9.await(); d10.await()
            d11.await(); d12.await(); d13.await(); d14.await(); d15.await()
            d16.await(); d17.await(); d18.await(); d19.await()
        }
    }

    private suspend fun loadExecutiveSummary(from: Long, to: Long) {
        analyticsRepository.getExecutiveSummary(from, to)
            .onSuccess { data -> _uiState.update { it.copy(executiveSummary = SectionState.Success(data)) } }
            .onFailure { e ->
                    CrashReporter.captureException(e)
                _uiState.update { it.copy(executiveSummary = SectionState.Error(e.userFriendlyMessage())) }
            }
    }

    private suspend fun loadRevenueProfit(from: Long, to: Long) {
        analyticsRepository.getRevenueProfit(from, to)
            .onSuccess { data -> _uiState.update { it.copy(revenueProfit = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(revenueProfit = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadOrdersIntelligence(from: Long, to: Long) {
        analyticsRepository.getOrdersIntelligence(from, to)
            .onSuccess { data -> _uiState.update { it.copy(ordersIntelligence = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(ordersIntelligence = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadPeakTimeAnalysis(from: Long, to: Long) {
        analyticsRepository.getPeakTimeAnalysis(from, to)
            .onSuccess { data -> _uiState.update { it.copy(peakTimeAnalysis = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(peakTimeAnalysis = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadCashierPerformance(from: Long, to: Long) {
        analyticsRepository.getCashierPerformanceV2(from, to)
            .onSuccess { data -> _uiState.update { it.copy(cashierPerformance = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(cashierPerformance = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadDeliveryPerformance(from: Long, to: Long) {
        analyticsRepository.getDeliveryPerformanceV2(from, to)
            .onSuccess { data -> _uiState.update { it.copy(deliveryPerformance = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(deliveryPerformance = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadProductIntelligence(from: Long, to: Long) {
        analyticsRepository.getProductIntelligence(from, to)
            .onSuccess { data -> _uiState.update { it.copy(productIntelligence = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(productIntelligence = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadCustomerIntelligence(from: Long, to: Long) {
        analyticsRepository.getCustomerIntelligence(from, to)
            .onSuccess { data -> _uiState.update { it.copy(customerIntelligence = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(customerIntelligence = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadAlerts(from: Long, to: Long) {
        analyticsRepository.getAnalyticsAlerts(from, to)
            .onSuccess { data -> _uiState.update { it.copy(alerts = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(alerts = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadStockOverview() {
        analyticsRepository.getStockOverview()
            .onSuccess { data -> _uiState.update { it.copy(stockOverview = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(stockOverview = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadOffersAnalytics(from: Long, to: Long) {
        analyticsRepository.getOffersAnalytics(from, to)
            .onSuccess { data -> _uiState.update { it.copy(offersAnalytics = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(offersAnalytics = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadDiscountAnalytics(from: Long, to: Long) {
        analyticsRepository.getDiscountAnalytics(from, to)
            .onSuccess { data -> _uiState.update { it.copy(discountAnalytics = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(discountAnalytics = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadLoyaltyAnalytics(from: Long, to: Long) {
        analyticsRepository.getLoyaltyAnalytics(from, to)
            .onSuccess { data -> _uiState.update { it.copy(loyaltyAnalytics = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(loyaltyAnalytics = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadStaffCosts(from: Long, to: Long) {
        analyticsRepository.getStaffCostsAnalytics(from, to)
            .onSuccess { data -> _uiState.update { it.copy(staffCosts = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(staffCosts = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadSupplierAnalytics(from: Long, to: Long) {
        analyticsRepository.getSupplierAnalytics(from, to)
            .onSuccess { data -> _uiState.update { it.copy(supplierAnalytics = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(supplierAnalytics = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadCreditAnalytics(from: Long, to: Long) {
        analyticsRepository.getCreditAnalytics(from, to)
            .onSuccess { data -> _uiState.update { it.copy(creditAnalytics = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(creditAnalytics = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadDoctorStats(from: Long, to: Long) {
        analyticsRepository.getDoctorStats(from, to)
            .onSuccess { data -> _uiState.update { it.copy(doctorStats = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(doctorStats = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadReturnsAnalytics(from: Long, to: Long) {
        analyticsRepository.getReturnsAnalytics(from, to)
            .onSuccess { data -> _uiState.update { it.copy(returnsAnalytics = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(returnsAnalytics = SectionState.Error(e.userFriendlyMessage())) } }
    }

    private suspend fun loadInstallmentAnalytics(from: Long, to: Long) {
        installmentRepository.getAnalytics(from, to)
            .onSuccess { data -> _uiState.update { it.copy(installmentAnalytics = SectionState.Success(data)) } }
            .onFailure { e -> _uiState.update { it.copy(installmentAnalytics = SectionState.Error(e.userFriendlyMessage())) } }
    }

    // ── Date range calculation ───────────────────────────────────────

    private fun getDateRange(): Pair<Long, Long> {
        val filters = _uiState.value.filters
        if (filters.timePeriod == TimePeriod.CUSTOM && filters.fromDate != null && filters.toDate != null) {
            return filters.fromDate to filters.toDate
        }

        val now = Clock.System.now()
        val tz = TimeZone.currentSystemDefault()
        val today = now.toLocalDateTime(tz).date
        val toMs = now.toEpochMilliseconds()

        return when (filters.timePeriod) {
            TimePeriod.TODAY -> {
                today.atStartOfDayIn(tz).toEpochMilliseconds() to toMs
            }
            TimePeriod.YESTERDAY -> {
                val yesterday = today.minus(1, DateTimeUnit.DAY)
                yesterday.atStartOfDayIn(tz).toEpochMilliseconds() to today.atStartOfDayIn(tz).toEpochMilliseconds()
            }
            TimePeriod.THIS_WEEK -> {
                val startOfWeek = today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
                startOfWeek.atStartOfDayIn(tz).toEpochMilliseconds() to toMs
            }
            TimePeriod.LAST_7_DAYS -> {
                today.minus(7, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() to toMs
            }
            TimePeriod.LAST_14_DAYS -> {
                today.minus(14, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() to toMs
            }
            TimePeriod.THIS_MONTH -> {
                val startOfMonth = LocalDate(today.year, today.month, 1)
                startOfMonth.atStartOfDayIn(tz).toEpochMilliseconds() to toMs
            }
            TimePeriod.LAST_MONTH -> {
                val startOfLastMonth = today.minus(1, DateTimeUnit.MONTH).let {
                    LocalDate(it.year, it.month, 1)
                }
                val startOfThisMonth = LocalDate(today.year, today.month, 1)
                startOfLastMonth.atStartOfDayIn(tz).toEpochMilliseconds() to startOfThisMonth.atStartOfDayIn(tz).toEpochMilliseconds()
            }
            TimePeriod.LAST_3_MONTHS -> {
                today.minus(90, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() to toMs
            }
            TimePeriod.CUSTOM -> {
                // Fallback to last 7 days if custom but no dates set
                today.minus(7, DateTimeUnit.DAY).atStartOfDayIn(tz).toEpochMilliseconds() to toMs
            }
        }
    }
}
