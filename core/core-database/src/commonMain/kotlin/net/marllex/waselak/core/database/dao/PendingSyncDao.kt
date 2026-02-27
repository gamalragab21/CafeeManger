package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.Pending_sync
import net.marllex.waselak.core.database.WaselakDatabase

class PendingSyncDao(private val db: WaselakDatabase) {
    private val queries get() = db.pendingSyncQueries

    fun getAllPending(): Flow<List<Pending_sync>> =
        queries.getAllPending().asFlow().mapToList(Dispatchers.Default)

    fun getPendingCount(): Flow<Long> =
        queries.getPendingCount().asFlow().mapToOne(Dispatchers.Default)

    suspend fun getAllPendingList(): List<Pending_sync> =
        queries.getAllPending().executeAsList()

    suspend fun insertPending(item: Pending_sync) {
        queries.insertPending(
            id = item.id,
            type = item.type,
            payload = item.payload,
            created_at = item.created_at,
            retry_count = item.retry_count,
            last_error = item.last_error
        )
    }

    suspend fun deletePending(id: String) {
        queries.deletePending(id)
    }

    suspend fun updateRetry(id: String, retryCount: Int, lastError: String?) {
        queries.updateRetry(
            retry_count = retryCount,
            last_error = lastError,
            id = id
        )
    }

    suspend fun updatePayload(id: String, payload: String) {
        queries.updatePayload(payload = payload, id = id)
    }

    suspend fun deleteAll() {
        queries.deleteAll()
    }
}
