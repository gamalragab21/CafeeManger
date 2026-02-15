package net.marllex.cafeemanger.core.domain.repository

import net.marllex.cafeemanger.core.model.AnalyticsSummary
import net.marllex.cafeemanger.core.model.DailyAnalytics
import net.marllex.cafeemanger.core.model.DeliveryPerformance
import net.marllex.cafeemanger.core.model.Settlements

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
    
    // Export methods
    suspend fun exportOrdersPDF(fromDate: Long, toDate: Long): Result<ByteArray>
    suspend fun exportOrdersExcel(fromDate: Long, toDate: Long): Result<ByteArray>
    suspend fun getExportPreview(fromDate: Long, toDate: Long): Result<Map<String, Any>>
}
