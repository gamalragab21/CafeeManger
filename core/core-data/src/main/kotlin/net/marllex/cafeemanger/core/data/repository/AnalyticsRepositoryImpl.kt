package net.marllex.cafeemanger.core.data.repository

import net.marllex.cafeemanger.core.domain.repository.AnalyticsRepository
import net.marllex.cafeemanger.core.model.AnalyticsSummary
import net.marllex.cafeemanger.core.model.DailyAnalytics
import net.marllex.cafeemanger.core.model.DeliveryPerformance
import net.marllex.cafeemanger.core.model.Settlements
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class AnalyticsRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
) : AnalyticsRepository {

    override suspend fun getSummary(from: Long?, to: Long?): Result<AnalyticsSummary> = runCatching {
        val fromDate = from ?: (System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
        val toDate = to ?: System.currentTimeMillis()
        api.getAnalyticsSummary(fromDate, toDate).toDomain(fromDate, toDate)
    }

    override suspend fun getFilteredSummary(
        status: String?,
        channel: String?,
        from: Long?,
        to: Long?
    ): Result<AnalyticsSummary> = runCatching {
        val fromDate = from ?: (System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000))
        val toDate = to ?: System.currentTimeMillis()
        api.getFilteredAnalyticsSummary(status, channel, fromDate, toDate).toDomain(fromDate, toDate)
    }

    override suspend fun getSettlements(from: Long?, to: Long?): Result<Settlements> = runCatching {
        api.getSettlements(from, to).toDomain()
    }

    override suspend fun getDeliveryPerformance(from: Long?, to: Long?): Result<List<DeliveryPerformance>> =
        runCatching {
            api.getDeliveryPerformance(from, to).map { it.toDomain() }
        }

    override suspend fun getDailyAnalytics(from: Long, to: Long): Result<List<DailyAnalytics>> =
        runCatching {
            api.getDailyAnalytics(from, to).map { it.toDomain() }
        }
}
