package net.marllex.waselak.core.data.repository

import io.ktor.client.call.body
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import kotlinx.datetime.Clock
import net.marllex.waselak.core.domain.repository.AnalyticsRepository
import net.marllex.waselak.core.model.AnalyticsSummary
import net.marllex.waselak.core.model.DailyAnalytics
import net.marllex.waselak.core.model.DeliveryPerformance
import net.marllex.waselak.core.model.Settlements
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.mapper.toDomain

class AnalyticsRepositoryImpl(
    private val api: WaselakApiClient,
) : AnalyticsRepository {

    override suspend fun getSummary(from: Long?, to: Long?): Result<AnalyticsSummary> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        val fromDate = from ?: (now - (30L * 24 * 60 * 60 * 1000))
        val toDate = to ?: now
        api.getAnalyticsSummary(fromDate, toDate).toDomain(fromDate, toDate)
    }

    override suspend fun getFilteredSummary(
        status: String?,
        channel: String?,
        cashierId: String?,
        deliveryUserId: String?,
        from: Long?,
        to: Long?
    ): Result<AnalyticsSummary> = runCatching {
        val now = Clock.System.now().toEpochMilliseconds()
        val fromDate = from ?: (now - (30L * 24 * 60 * 60 * 1000))
        val toDate = to ?: now
        api.getFilteredAnalyticsSummary(status, channel, cashierId, deliveryUserId, fromDate, toDate).toDomain(fromDate, toDate)
    }

    override suspend fun getSettlements(
        status: String?,
        channel: String?,
        cashierId: String?,
        deliveryUserId: String?,
        from: Long?,
        to: Long?
    ): Result<Settlements> = runCatching {
        api.getSettlements(status, channel, cashierId, deliveryUserId, from, to).toDomain()
    }

    override suspend fun getDeliveryPerformance(
        status: String?,
        cashierId: String?,
        from: Long?,
        to: Long?
    ): Result<List<DeliveryPerformance>> =
        runCatching {
            api.getDeliveryPerformance(status, cashierId, from, to).map { it.toDomain() }
        }

    override suspend fun getCashierPerformance(
        status: String?,
        channel: String?,
        deliveryUserId: String?,
        from: Long?,
        to: Long?
    ): Result<List<DeliveryPerformance>> =
        runCatching {
            api.getCashierPerformance(status, channel, deliveryUserId, from, to).map { it.toDomain() }
        }

    override suspend fun getDailyAnalytics(from: Long, to: Long): Result<List<DailyAnalytics>> =
        runCatching {
            api.getDailyAnalytics(from, to).map { it.toDomain() }
        }

    override suspend fun exportOrdersPDF(fromDate: Long, toDate: Long): Result<ByteArray> = runCatching {
        val response = api.exportOrdersPDF(fromDate, toDate)
        if (response.status.isSuccess()) {
            response.readBytes()
        } else {
            throw Exception("Failed to export PDF: ${response.status}")
        }
    }

    override suspend fun exportOrdersExcel(fromDate: Long, toDate: Long): Result<ByteArray> = runCatching {
        val response = api.exportOrdersExcel(fromDate, toDate)
        if (response.status.isSuccess()) {
            response.readBytes()
        } else {
            throw Exception("Failed to export Excel: ${response.status}")
        }
    }

    override suspend fun getExportPreview(fromDate: Long, toDate: Long): Result<Map<String, Any>> = runCatching {
        val response = api.getExportPreview(fromDate, toDate)
        if (response.status.isSuccess()) {
            response.body()
        } else {
            throw Exception("Failed to load preview: ${response.status}")
        }
    }
}
