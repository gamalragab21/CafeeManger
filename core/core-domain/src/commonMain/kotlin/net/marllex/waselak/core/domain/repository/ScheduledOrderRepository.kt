package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.ScheduledOrder
import net.marllex.waselak.core.network.dto.CreateScheduledOrderRequest

interface ScheduledOrderRepository {
    suspend fun getScheduledOrders(status: String? = null, limit: Int = 50, offset: Int = 0): Result<List<ScheduledOrder>>
    suspend fun getScheduledOrder(id: String): Result<ScheduledOrder>
    suspend fun createScheduledOrder(request: CreateScheduledOrderRequest): Result<ScheduledOrder>
    suspend fun updateStatus(id: String, status: String, notes: String? = null): Result<ScheduledOrder>
    suspend fun deleteScheduledOrder(id: String): Result<Unit>
}
