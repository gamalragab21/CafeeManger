package net.marllex.cafeemanger.core.data.repository

import net.marllex.cafeemanger.core.domain.repository.AnalyticsRepository
import net.marllex.cafeemanger.core.model.AnalyticsSummary
import net.marllex.cafeemanger.core.model.DailyAnalytics
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class AnalyticsRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
) : AnalyticsRepository {

    override suspend fun getSummary(from: Long, to: Long): Result<AnalyticsSummary> = runCatching {
        api.getAnalyticsSummary(from, to).toDomain(from, to)
    }

    override suspend fun getDailyAnalytics(from: Long, to: Long): Result<List<DailyAnalytics>> =
        runCatching {
            api.getDailyAnalytics(from, to).map { it.toDomain() }
        }
}
