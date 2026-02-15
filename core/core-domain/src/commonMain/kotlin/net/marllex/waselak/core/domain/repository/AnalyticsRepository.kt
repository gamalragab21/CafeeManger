package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.AnalyticsSummary
import net.marllex.waselak.core.model.DailyAnalytics
import net.marllex.waselak.core.model.DeliveryPerformance
import net.marllex.waselak.core.model.Settlements

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
