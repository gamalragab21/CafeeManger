package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.KdsRepository
import net.marllex.waselak.core.model.KdsOrder
import net.marllex.waselak.core.model.KdsSummary
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.AssignStationRequest
import net.marllex.waselak.core.network.dto.BulkUpdateKitchenStatusRequest
import net.marllex.waselak.core.network.dto.UpdateKitchenStatusRequest
import net.marllex.waselak.core.network.mapper.toDomain

class KdsRepositoryImpl(
    private val api: WaselakApiClient,
) : KdsRepository {

    override suspend fun getKdsOrders(station: String?, status: String?): Result<List<KdsOrder>> = runCatching {
        api.getKdsOrders(station, status).map { it.toDomain() }
    }

    override suspend fun updateItemStatus(itemId: String, status: String): Result<Unit> = runCatching {
        api.updateKdsItemStatus(itemId, UpdateKitchenStatusRequest(status))
    }

    override suspend fun bulkUpdateStatus(orderId: String, itemIds: List<String>, status: String): Result<Unit> = runCatching {
        api.bulkUpdateKdsStatus(orderId, BulkUpdateKitchenStatusRequest(itemIds, status))
    }

    override suspend fun getSummary(): Result<KdsSummary> = runCatching {
        api.getKdsSummary().toDomain()
    }

    override suspend fun assignStation(itemId: String, station: String?): Result<Unit> = runCatching {
        api.assignKdsStation(itemId, AssignStationRequest(station))
    }
}
