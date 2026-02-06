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
        from: Long? = null,
        to: Long? = null
    ): Result<AnalyticsSummary>
    suspend fun getSettlements(from: Long?, to: Long?): Result<Settlements>
    suspend fun getDeliveryPerformance(from: Long?, to: Long?): Result<List<DeliveryPerformance>>
    suspend fun getDailyAnalytics(from: Long, to: Long): Result<List<DailyAnalytics>>
}
