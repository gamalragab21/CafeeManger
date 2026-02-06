package net.marllex.cafeemanger.core.domain.repository

import net.marllex.cafeemanger.core.model.AnalyticsSummary
import net.marllex.cafeemanger.core.model.DailyAnalytics

interface AnalyticsRepository {
    suspend fun getSummary(from: Long, to: Long): Result<AnalyticsSummary>
    suspend fun getDailyAnalytics(from: Long, to: Long): Result<List<DailyAnalytics>>
}
