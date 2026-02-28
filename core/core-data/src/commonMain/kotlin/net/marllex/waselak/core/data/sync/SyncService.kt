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
        // Track offline-ID → server-ID mappings for dependent items in this sync run
        val offlineIdMap = mutableMapOf<String, String>()
        try {
            val pending = pendingSyncDao.getAllPendingList()
            for (item in pending) {
                try {
                    when (item.type) {
                        "ORDER" -> {
                            val request = json.decodeFromString<CreateOrderRequest>(item.payload)
                            val response = api.createOrder(request)
                            val order = response.toDomain()

                            val offlineId = item.id   // e.g. "offline-1772229943841"
                            val serverId = order.id    // real server UUID

                            // Remove old offline order and its items from local DB
                            orderDao.deleteOrderItems(offlineId)
                            orderDao.deleteOrder(offlineId)

                            // Insert the server order with items
                            orderDao.insertOrder(order.toDbEntity().copy(sync_status = "SYNCED"))
                            orderDao.insertOrderItems(order.items.map { it.toDbEntity() })

                            // Track the mapping for in-memory resolution later in this loop
                            offlineIdMap[offlineId] = serverId

                            // Update dependent pending items in DB so future sync runs use server ID
                            remapDependentItems(offlineId, serverId)

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
                        "PAYMENT_UPDATE" -> {
                            val params = json.decodeFromString<PaymentUpdatePayload>(item.payload)
                            // Resolve offline ID → server ID (from this sync run or DB remap)
                            val resolvedOrderId = offlineIdMap[params.orderId] ?: params.orderId

                            // If still an offline ID, check if there's a pending ORDER for it
                            if (resolvedOrderId.startsWith("offline-")) {
                                val hasPendingOrder = pending.any { it.type == "ORDER" && it.id == resolvedOrderId }
                                if (hasPendingOrder) {
                                    // ORDER will sync first and remap — skip until next run
                                    continue
                                }
                                // No pending ORDER: legacy item — mark as failed so user can delete
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
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++
                        }
                        "ORDER_STATUS_UPDATE" -> {
                            val params = json.decodeFromString<OrderStatusPayload>(item.payload)
                            // Resolve offline ID → server ID (from this sync run or DB remap)
                            val resolvedOrderId = offlineIdMap[params.orderId] ?: params.orderId

                            // If still an offline ID, check if there's a pending ORDER for it
                            if (resolvedOrderId.startsWith("offline-")) {
                                val hasPendingOrder = pending.any { it.type == "ORDER" && it.id == resolvedOrderId }
                                if (hasPendingOrder) {
                                    // ORDER will sync first and remap — skip until next run
                                    continue
                                }
                                // No pending ORDER: legacy item — mark as failed so user can delete
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
                            pendingSyncDao.deletePending(item.id)
                            syncedCount++
                        }
                        "REFUND" -> {
                            val params = json.decodeFromString<RefundPayload>(item.payload)
                            val resolvedOrderId = offlineIdMap[params.orderId] ?: params.orderId

                            if (resolvedOrderId.startsWith("offline-")) {
                                val hasPendingOrder = pending.any { it.type == "ORDER" && it.id == resolvedOrderId }
                                if (hasPendingOrder) continue
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
