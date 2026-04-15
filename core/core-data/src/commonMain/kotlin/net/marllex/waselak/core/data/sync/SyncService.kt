package net.marllex.waselak.core.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.marllex.waselak.core.database.dao.OrderDao
import net.marllex.waselak.core.database.dao.PendingSyncDao
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CheckInRequest
import net.marllex.waselak.core.network.dto.CheckInWithPinRequest
import net.marllex.waselak.core.network.dto.CheckOutRequest
import net.marllex.waselak.core.network.dto.CheckOutWithPinRequest
import net.marllex.waselak.core.network.dto.CreateOrderRequest
import net.marllex.waselak.core.network.dto.RefundOrderRequest
import net.marllex.waselak.core.network.dto.UpdateOrderStatusRequest
import net.marllex.waselak.core.network.dto.UpdatePaymentStatusRequest
import net.marllex.waselak.core.network.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.common.logging.AppLogger

enum class SyncState { IDLE, SYNCING, SUCCESS, ERROR }

class SyncService(
    private val api: WaselakApiClient,
    private val pendingSyncDao: PendingSyncDao,
    private val orderDao: OrderDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    companion object {
        private const val MAX_RETRIES = 5
    }

    /** Types of items that were synced in the last syncAll() call */
    var lastSyncedTypes: Set<String> = emptySet()
        private set

    suspend fun syncAll(): Int {
        _syncState.value = SyncState.SYNCING
        var syncedCount = 0
        val syncedTypes = mutableSetOf<String>()
        val offlineIdMap = mutableMapOf<String, String>()
        try {
            val pending = pendingSyncDao.getAllPendingList()
            AppLogger.i("Sync", "syncAll started: ${pending.size} pending items")
            // Clean up items that exceeded max retries
            val staleItems = pending.filter { (it.retry_count ?: 0) >= MAX_RETRIES }
            if (staleItems.isNotEmpty()) {
                AppLogger.w("Sync", "Cleaning up ${staleItems.size} stale items that exceeded max retries")
                for (stale in staleItems) {
                    AppLogger.w("Sync", "  Removing stale: type=${stale.type}, id=${stale.id}, retries=${stale.retry_count}, lastError=${stale.last_error}")
                    pendingSyncDao.deletePending(stale.id)
                }
            }

            val activePending = pending.filter { (it.retry_count ?: 0) < MAX_RETRIES }
            // Process ORDERs first (others may depend on their server IDs)
            val orders = activePending.filter { it.type == "ORDER" }
            val others = activePending.filter { it.type != "ORDER" }
            val sortedPending = orders + others
            for (item in sortedPending) {
                AppLogger.d("Sync", "Processing: type=${item.type}, id=${item.id}, retries=${item.retry_count}, payload=${item.payload}")
                try {
                    when (item.type) {
                        "ORDER" -> {
                            val request = json.decodeFromString<CreateOrderRequest>(item.payload)
                            AppLogger.d("Sync", "Syncing ORDER: offlineId=${item.id}")
                            val response = api.createOrder(request)
                            val order = response.toDomain()
                            val offlineId = item.id
                            val serverId = order.id
                            AppLogger.i("Sync", "ORDER synced: offlineId=$offlineId -> serverId=$serverId")

                            orderDao.deleteOrderItems(offlineId)
                            orderDao.deleteOrder(offlineId)
                            orderDao.insertOrder(order.toDbEntity().copy(sync_status = "SYNCED"))
                            orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
                            AppLogger.d("Sync", "ORDER DB updated: removed offline, inserted server order")

                            offlineIdMap[offlineId] = serverId
                            remapDependentItems(offlineId, serverId)
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++; syncedTypes.add(item.type)
                        }
                        "CHECK_IN" -> {
                            val params = json.decodeFromString<CheckInPayload>(item.payload)
                            AppLogger.d("Sync", "Syncing CHECK_IN: workerId=${params.workerId}, hasPin=${params.pin != null}")
                            if (params.pin != null) {
                                api.checkInWithPin(CheckInWithPinRequest(params.workerId, params.pin))
                            } else {
                                api.checkIn(CheckInRequest(params.workerId))
                            }
                            AppLogger.i("Sync", "CHECK_IN synced: workerId=${params.workerId}")
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++; syncedTypes.add(item.type)
                        }
                        "CHECK_OUT" -> {
                            val params = json.decodeFromString<CheckOutPayload>(item.payload)
                            AppLogger.d("Sync", "Syncing CHECK_OUT: attendanceId=${params.attendanceId}, hasPin=${params.pin != null}")
                            if (params.pin != null) {
                                api.checkOutWithPin(params.attendanceId, CheckOutWithPinRequest(params.pin))
                            } else {
                                api.checkOut(params.attendanceId, CheckOutRequest())
                            }
                            AppLogger.i("Sync", "CHECK_OUT synced: attendanceId=${params.attendanceId}")
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++; syncedTypes.add(item.type)
                        }
                        "PAYMENT_UPDATE" -> {
                            val params = json.decodeFromString<PaymentUpdatePayload>(item.payload)
                            val resolvedOrderId = offlineIdMap[params.orderId] ?: params.orderId
                            AppLogger.d("Sync", "Syncing PAYMENT_UPDATE: orderId=${params.orderId}, resolved=$resolvedOrderId, status=${params.status}")

                            if (resolvedOrderId.startsWith("offline-")) {
                                val hasPendingOrder = pending.any { it.type == "ORDER" && it.id == resolvedOrderId }
                                if (hasPendingOrder) {
                                    AppLogger.d("Sync", "PAYMENT_UPDATE deferred: waiting for ORDER $resolvedOrderId")
                                    continue
                                }
                                AppLogger.w("Sync", "PAYMENT_UPDATE failed: cannot resolve offline ID $resolvedOrderId")
                                pendingSyncDao.updateRetry(
                                    id = item.id, retryCount = 3,
                                    lastError = "Cannot resolve offline order ID. The order was already synced. Please delete this item."
                                )
                                continue
                            }

                            val response = api.updatePaymentStatus(
                                resolvedOrderId,
                                UpdatePaymentStatusRequest(params.status, params.paymentMethod),
                            )
                            val order = response.toDomain()
                            orderDao.insertOrder(order.toDbEntity())
                            AppLogger.i("Sync", "PAYMENT_UPDATE synced: orderId=$resolvedOrderId, newStatus=${params.status}")
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++; syncedTypes.add(item.type)
                        }
                        "ORDER_STATUS_UPDATE" -> {
                            val params = json.decodeFromString<OrderStatusPayload>(item.payload)
                            val resolvedOrderId = offlineIdMap[params.orderId] ?: params.orderId
                            AppLogger.d("Sync", "Syncing ORDER_STATUS_UPDATE: orderId=${params.orderId}, resolved=$resolvedOrderId, status=${params.status}")

                            if (resolvedOrderId.startsWith("offline-")) {
                                val hasPendingOrder = pending.any { it.type == "ORDER" && it.id == resolvedOrderId }
                                if (hasPendingOrder) {
                                    AppLogger.d("Sync", "ORDER_STATUS_UPDATE deferred: waiting for ORDER $resolvedOrderId")
                                    continue
                                }
                                AppLogger.w("Sync", "ORDER_STATUS_UPDATE failed: cannot resolve offline ID $resolvedOrderId")
                                pendingSyncDao.updateRetry(
                                    id = item.id, retryCount = 3,
                                    lastError = "Cannot resolve offline order ID. The order was already synced. Please delete this item."
                                )
                                continue
                            }

                            val response = api.updateOrderStatus(
                                resolvedOrderId,
                                UpdateOrderStatusRequest(params.status),
                            )
                            val order = response.toDomain()
                            orderDao.insertOrder(order.toDbEntity())
                            AppLogger.i("Sync", "ORDER_STATUS_UPDATE synced: orderId=$resolvedOrderId, newStatus=${params.status}")
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++; syncedTypes.add(item.type)
                        }
                        "REFUND" -> {
                            val params = json.decodeFromString<RefundPayload>(item.payload)
                            val resolvedOrderId = offlineIdMap[params.orderId] ?: params.orderId
                            AppLogger.d("Sync", "Syncing REFUND: orderId=${params.orderId}, resolved=$resolvedOrderId")

                            if (resolvedOrderId.startsWith("offline-")) {
                                val hasPendingOrder = pending.any { it.type == "ORDER" && it.id == resolvedOrderId }
                                if (hasPendingOrder) continue
                                AppLogger.w("Sync", "REFUND failed: cannot resolve offline ID $resolvedOrderId")
                                pendingSyncDao.updateRetry(
                                    id = item.id, retryCount = 3,
                                    lastError = "Cannot resolve offline order ID. The order was already synced. Please delete this item."
                                )
                                continue
                            }

                            val response = api.refundOrder(
                                resolvedOrderId,
                                RefundOrderRequest(params.reason),
                            )
                            val order = response.toDomain()
                            orderDao.insertOrder(order.toDbEntity())
                            AppLogger.i("Sync", "REFUND synced: orderId=$resolvedOrderId")
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++; syncedTypes.add(item.type)
                        }
                    }
                } catch (e: Exception) {
                    val statusCode = (e as? net.marllex.waselak.core.network.ApiException)?.statusCode
                    AppLogger.e("Sync", "Sync item FAILED: type=${item.type}, id=${item.id}, statusCode=$statusCode", e)
                    // Non-retryable errors: remove immediately
                    if (statusCode == 409 || statusCode == 400 || statusCode == 404) {
                        AppLogger.w("Sync", "Removing non-retryable item ${item.id} (status=$statusCode): ${e.message}")
                        pendingSyncDao.deletePending(item.id)
                    } else {
                        val newRetryCount = (item.retry_count ?: 0) + 1
                        pendingSyncDao.updateRetry(
                            id = item.id,
                            retryCount = newRetryCount,
                            lastError = e.message
                        )
                        if (newRetryCount >= MAX_RETRIES) {
                            AppLogger.w("Sync", "Item ${item.id} reached max retries ($newRetryCount), will not retry again")
                        }
                    }
                }
            }
            lastSyncedTypes = syncedTypes.toSet()
            AppLogger.i("Sync", "syncAll completed: synced=$syncedCount of ${activePending.size} active, types=$syncedTypes (${staleItems.size} stale removed)")
            _syncState.value = if (syncedCount > 0) SyncState.SUCCESS else SyncState.IDLE
        } catch (e: Exception) {
            AppLogger.e("Sync", "syncAll FAILED with exception", e)
            _syncState.value = SyncState.ERROR
        }
        return syncedCount
    }

    /**
     * After an ORDER sync succeeds, update all dependent pending items
     * (PAYMENT_UPDATE, ORDER_STATUS_UPDATE) that reference the old offline ID
     * to use the new server UUID. This ensures future sync runs use the correct ID.
     */
    private suspend fun remapDependentItems(offlineId: String, serverId: String) {
        val allPending = pendingSyncDao.getAllPendingList()
        for (item in allPending) {
            when (item.type) {
                "PAYMENT_UPDATE" -> {
                    val params = json.decodeFromString<PaymentUpdatePayload>(item.payload)
                    if (params.orderId == offlineId) {
                        val updated = params.copy(orderId = serverId)
                        pendingSyncDao.updatePayload(item.id, json.encodeToString(updated))
                    }
                }
                "ORDER_STATUS_UPDATE" -> {
                    val params = json.decodeFromString<OrderStatusPayload>(item.payload)
                    if (params.orderId == offlineId) {
                        val updated = params.copy(orderId = serverId)
                        pendingSyncDao.updatePayload(item.id, json.encodeToString(updated))
                    }
                }
                "REFUND" -> {
                    val params = json.decodeFromString<RefundPayload>(item.payload)
                    if (params.orderId == offlineId) {
                        val updated = params.copy(orderId = serverId)
                        pendingSyncDao.updatePayload(item.id, json.encodeToString(updated))
                    }
                }
            }
        }
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

@Serializable
data class PaymentUpdatePayload(
    val orderId: String,
    val status: String,
    val paymentMethod: String? = null,
)

@Serializable
data class OrderStatusPayload(
    val orderId: String,
    val status: String,
)

@Serializable
data class RefundPayload(
    val orderId: String,
    val reason: String,
)
