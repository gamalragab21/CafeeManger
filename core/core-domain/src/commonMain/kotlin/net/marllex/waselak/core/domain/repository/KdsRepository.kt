package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.KdsOrder
import net.marllex.waselak.core.model.KdsSummary

interface KdsRepository {
    suspend fun getKdsOrders(station: String? = null, status: String? = null): Result<List<KdsOrder>>
    suspend fun updateItemStatus(itemId: String, status: String): Result<Unit>
    suspend fun bulkUpdateStatus(orderId: String, itemIds: List<String>, status: String): Result<Unit>
    suspend fun getSummary(): Result<KdsSummary>
    suspend fun assignStation(itemId: String, station: String?): Result<Unit>
}
