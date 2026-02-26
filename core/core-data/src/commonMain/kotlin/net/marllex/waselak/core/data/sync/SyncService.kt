package net.marllex.waselak.core.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.marllex.waselak.core.database.dao.OrderDao
import net.marllex.waselak.core.database.dao.PendingSyncDao
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CheckInRequest
import net.marllex.waselak.core.network.dto.CheckInWithPinRequest
import net.marllex.waselak.core.network.dto.CheckOutRequest
import net.marllex.waselak.core.network.dto.CheckOutWithPinRequest
import net.marllex.waselak.core.network.dto.CreateOrderRequest
import net.marllex.waselak.core.network.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity

enum class SyncState { IDLE, SYNCING, SUCCESS, ERROR }

class SyncService(
    private val api: WaselakApiClient,
    private val pendingSyncDao: PendingSyncDao,
    private val orderDao: OrderDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun syncAll(): Int {
        _syncState.value = SyncState.SYNCING
        var syncedCount = 0
        try {
            val pending = pendingSyncDao.getAllPendingList()
            for (item in pending) {
                try {
                    when (item.type) {
                        "ORDER" -> {
                            val request = json.decodeFromString<CreateOrderRequest>(item.payload)
                            val response = api.createOrder(request)
                            val order = response.toDomain()
                            orderDao.insertOrder(order.toDbEntity().copy(sync_status = "SYNCED"))
                            orderDao.updateSyncStatus(item.id, "SYNCED")
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++
                        }
                        "CHECK_IN" -> {
                            val params = json.decodeFromString<CheckInPayload>(item.payload)
                            if (params.pin != null) {
                                api.checkInWithPin(CheckInWithPinRequest(params.workerId, params.pin))
                            } else {
                                api.checkIn(CheckInRequest(params.workerId))
                            }
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++
                        }
                        "CHECK_OUT" -> {
                            val params = json.decodeFromString<CheckOutPayload>(item.payload)
                            if (params.pin != null) {
                                api.checkOutWithPin(params.attendanceId, CheckOutWithPinRequest(params.pin))
                            } else {
                                api.checkOut(params.attendanceId, CheckOutRequest())
                            }
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++
                        }
                    }
                } catch (e: Exception) {
                    pendingSyncDao.updateRetry(
                        id = item.id,
                        retryCount = (item.retry_count ?: 0) + 1,
                        lastError = e.message
                    )
                }
            }
            _syncState.value = if (syncedCount > 0) SyncState.SUCCESS else SyncState.IDLE
        } catch (e: Exception) {
            _syncState.value = SyncState.ERROR
        }
        return syncedCount
    }
}

@Serializable
data class CheckInPayload(
    val workerId: String,
    val pin: String? = null,
)

@Serializable
data class CheckOutPayload(
    val attendanceId: String,
    val workerId: String,
    val pin: String? = null,
)
