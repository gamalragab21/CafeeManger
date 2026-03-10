package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.*

interface AnalyticsRepository {
    suspend fun getSummary(from: Long?, to: Long?): Result<AnalyticsSummary>
    suspend fun getFilteredSummary(
        status: String? = null,
        channel: String? = null,
        cashierId: String? = null,
        deliveryUserId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): Result<AnalyticsSummary>
    suspend fun getSettlements(
        status: String? = null,
        channel: String? = null,
        cashierId: String? = null,
        deliveryUserId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): Result<Settlements>
    suspend fun getDeliveryPerformance(
        status: String? = null,
        cashierId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): Result<List<DeliveryPerformance>>
    suspend fun getCashierPerformance(
        status: String? = null,
        channel: String? = null,
        deliveryUserId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): Result<List<DeliveryPerformance>>
    suspend fun getDailyAnalytics(from: Long, to: Long): Result<List<DailyAnalytics>>

    // Dashboard V2 methods
    suspend fun getExecutiveSummary(from: Long?, to: Long?): Result<ExecutiveSummary>
    suspend fun getRevenueProfit(from: Long?, to: Long?): Result<RevenueProfit>
    suspend fun getOrdersIntelligence(from: Long?, to: Long?): Result<OrdersIntelligence>
    suspend fun getPeakTimeAnalysis(from: Long?, to: Long?): Result<PeakTimeAnalysis>
    suspend fun getCashierPerformanceV2(from: Long?, to: Long?): Result<List<CashierPerformanceV2>>
    suspend fun getDeliveryPerformanceV2(from: Long?, to: Long?): Result<List<DeliveryPerformanceV2>>
    suspend fun getProductIntelligence(from: Long?, to: Long?, limit: Int? = null): Result<ProductIntelligence>
    suspend fun getCustomerIntelligence(from: Long?, to: Long?): Result<CustomerIntelligence>
    suspend fun getAnalyticsAlerts(from: Long?, to: Long?): Result<List<AnalyticsAlert>>
    suspend fun getStockOverview(): Result<StockOverview>

    // Offers / Discount / Loyalty analytics
    suspend fun getOffersAnalytics(from: Long?, to: Long?): Result<OffersAnalytics>
    suspend fun getDiscountAnalytics(from: Long?, to: Long?): Result<DiscountAnalytics>
    suspend fun getLoyaltyAnalytics(from: Long?, to: Long?): Result<LoyaltyAnalytics>

    // Export methods
    suspend fun exportOrdersPDF(fromDate: Long, toDate: Long): Result<ByteArray>
    suspend fun exportOrdersExcel(fromDate: Long, toDate: Long): Result<ByteArray>
    suspend fun getExportPreview(fromDate: Long, toDate: Long): Result<Map<String, Any>>
}
