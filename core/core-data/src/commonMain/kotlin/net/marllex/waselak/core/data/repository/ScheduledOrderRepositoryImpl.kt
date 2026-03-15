package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.ScheduledOrderRepository
import net.marllex.waselak.core.model.ScheduledOrder
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateScheduledOrderRequest
import net.marllex.waselak.core.network.dto.UpdateScheduledOrderStatusRequest
import net.marllex.waselak.core.network.mapper.toDomain

class ScheduledOrderRepositoryImpl(
    private val api: WaselakApiClient,
) : ScheduledOrderRepository {

    override suspend fun getScheduledOrders(status: String?, limit: Int, offset: Int): Result<List<ScheduledOrder>> = runCatching {
        api.getScheduledOrders(status, limit, offset).map { it.toDomain() }
    }

    override suspend fun getScheduledOrder(id: String): Result<ScheduledOrder> = runCatching {
        api.getScheduledOrder(id).toDomain()
    }

    override suspend fun createScheduledOrder(request: CreateScheduledOrderRequest): Result<ScheduledOrder> = runCatching {
        api.createScheduledOrder(request).toDomain()
    }

    override suspend fun updateStatus(id: String, status: String, notes: String?): Result<ScheduledOrder> = runCatching {
        api.updateScheduledOrderStatus(id, UpdateScheduledOrderStatusRequest(status, notes)).toDomain()
    }

    override suspend fun deleteScheduledOrder(id: String): Result<Unit> = runCatching {
        api.deleteScheduledOrder(id)
    }
}
