package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.marllex.waselak.core.data.offline.OfflineModeManager
import net.marllex.waselak.core.data.sync.OrderStatusPayload
import net.marllex.waselak.core.data.sync.PaymentUpdatePayload
import net.marllex.waselak.core.data.sync.RefundPayload
import net.marllex.waselak.core.database.Pending_sync
import net.marllex.waselak.core.database.dao.ItemDao
import net.marllex.waselak.core.database.dao.OrderDao
import net.marllex.waselak.core.database.dao.PendingSyncDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.OrderRepository
import net.marllex.waselak.core.domain.repository.PaginatedResult
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.OrderItem
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.PaymentStatus
import net.marllex.waselak.core.model.PaymentTiming
import net.marllex.waselak.core.model.ReceiptShareLink
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.*
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.mapper.toDomain

class OrderRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val orderDao: OrderDao,
    private val authRepository: AuthRepository,
    private val offlineModeManager: OfflineModeManager,
    private val pendingSyncDao: PendingSyncDao,
    private val itemDao: ItemDao,
) : OrderRepository {

    private val json = Json { ignoreUnknownKeys = true }

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    private suspend fun attachItems(orders: List<Order>): List<Order> {
        if (orders.isEmpty()) return orders
        val orderIds = orders.map { it.id }
        val allItems = orderDao.getOrderItemsForOrders(orderIds)
            .map { it.toDomain() }
            .groupBy { it.orderId }
        return orders.map { order ->
            order.copy(items = allItems[order.id] ?: emptyList())
        }
    }

    override fun getOrders(status: String?, channel: String?): Flow<List<Order>> {
        return when {
            status != null -> orderDao.getOrdersByStatus(vendorId, status)
            channel != null -> orderDao.getOrdersByChannel(vendorId, channel)
            else -> orderDao.getOrders(vendorId)
        }.map { list ->
            val orders = list.map { it.toDomain() }
            attachItems(orders)
        }
    }

    override fun getOrderById(id: String): Flow<Order?> =
        orderDao.getOrderById(id).map { entity ->
            entity?.let { orderEntity ->
                val items = orderDao.getOrderItemsList(id).map { it.toDomain() }
                orderEntity.toDomain(items)
            }
        }

    override fun getMyDeliveryOrders(status: String?): Flow<List<Order>> {
        val userId = authRepository.getCurrentUserId() ?: ""
        val statuses = if (status != null) listOf(status) else listOf("ASSIGNED", "OUT_FOR_DELIVERY", "DELIVERED")
        return orderDao.getDeliveryOrders(userId, statuses).map { list ->
            val orders = list.map { it.toDomain() }
            attachItems(orders)
        }
    }

    override suspend fun refreshOrders(
        status: String?,
        channel: String?,
        cashierId: String?,
        deliveryUserId: String?,
        tableId: String?,
        from: Long?,
        to: Long?,
        limit: Int,
        offset: Int,
    ): Result<PaginatedResult<Order>> =
        runCatching {
            AppLogger.d("OrderRepo", "Refreshing orders: status=$status, channel=$channel, limit=$limit, offset=$offset")
            val response = api.getOrders(
                status = status,
                channel = channel,
                cashierId = cashierId,
                deliveryUserId = deliveryUserId,
                tableId = tableId,
                from = from,
                to = to,
                limit = limit,
                offset = offset
            )
            val orders = response.orders.map { it.toDomain() }
            AppLogger.i("OrderRepo", "Fetched ${orders.size} orders (total: ${response.total}, hasMore: ${response.hasMore})")
            orders.forEach { o -> AppLogger.d("OrderRepo", "  order: id=${o.id}, status=${o.status}, channel=${o.channel}, total=${o.total}") }
            AppLogger.d("OrderRepo", "DB: inserting ${orders.size} orders")
            orderDao.insertOrders(orders.map { it.toDbEntity() })
            orders.forEach { order ->
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
            }
            PaginatedResult(orders, response.total, response.hasMore)
        }

    override suspend fun refreshMyDeliveryOrders(status: String?, limit: Int, offset: Int): Result<PaginatedResult<Order>> =
        runCatching {
            AppLogger.d("OrderRepo", "Refreshing my delivery orders: status=$status, limit=$limit, offset=$offset")
            val response = api.getMyDeliveryOrders(status = status, limit = limit, offset = offset)
            val orders = response.orders.map { it.toDomain() }
            AppLogger.i("OrderRepo", "Fetched ${orders.size} delivery orders (total: ${response.total})")
            orderDao.insertOrders(orders.map { it.toDbEntity() })
            orders.forEach { order ->
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
            }
            PaginatedResult(orders, response.total, response.hasMore)
        }

    override suspend fun getAvailableDeliveryOrders(): Result<List<Order>> =
        runCatching {
            AppLogger.d("OrderRepo", "Fetching available delivery orders")
            val response = api.getAvailableDeliveryOrders()
            val orders = response.map { it.toDomain() }
            AppLogger.i("OrderRepo", "Found ${orders.size} available delivery orders")
            orderDao.insertOrders(orders.map { it.toDbEntity() })
            orders.forEach { order ->
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
            }
            orders
        }

    override suspend fun createOrder(
        channel: OrderChannel, tableId: String?,
        clientName: String?, clientPhone: String?, clientAddress: String?,
        customerId: String?,
        geoLat: Double?, geoLng: Double?,
        paymentMethod: PaymentMethod, paymentTiming: PaymentTiming,
        taxPlaceId: String?, reservationId: String?, notes: String?,
        items: List<CreateOrderItemRequest>,
        discount: Double, discountType: String,
        offerId: String?, pointsRedeemed: Int, discountReason: String?,
    ): Result<Order> = runCatching {
        AppLogger.d("OrderRepo", "Creating order: channel=${channel.name}, items=${items.size}")
        val request = CreateOrderRequest(
            channel = channel.name, tableId = tableId,
            clientName = clientName, clientPhone = clientPhone,
            clientAddress = clientAddress,
            customerId = customerId,
            geoLat = geoLat, geoLng = geoLng,
            paymentMethod = paymentMethod.name,
            paymentTiming = paymentTiming.name,
            taxPlaceId = taxPlaceId,
            reservationId = reservationId,
            notes = notes,
            discount = discount,
            discountType = discountType,
            items = items,
            offerId = offerId,
            pointsRedeemed = pointsRedeemed,
            discountReason = discountReason,
        )

        if (offlineModeManager.isOfflineActive.value) {
            AppLogger.d("OrderRepo", "Creating offline order")
            saveOrderLocally(request, channel, tableId, clientName, clientPhone,
                clientAddress, customerId, geoLat, geoLng, paymentMethod, paymentTiming, notes, items)
        } else {
            try {
                val response = api.createOrder(request)
                val order = response.toDomain()
                AppLogger.i("OrderRepo", "Order created: id=${order.id}, status=${order.status}, channel=${order.channel}, total=${order.total}, items=${order.items.size}")
                AppLogger.d("OrderRepo", "DB: inserting created order ${order.id}")
                orderDao.insertOrder(order.toDbEntity())
                orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
                order
            } catch (e: Exception) {
                // If API fails and offline mode is enabled, save locally as fallback
                if (offlineModeManager.offlineModeEnabled.value) {
                    saveOrderLocally(request, channel, tableId, clientName, clientPhone,
                        clientAddress, customerId, geoLat, geoLng, paymentMethod, paymentTiming, notes, items)
                } else {
                    throw e
                }
            }
        }
    }

    override suspend fun updateOrder(
        id: String,
        clientName: String?, clientPhone: String?,
        clientAddress: String?, notes: String?,
        paymentMethod: String?, deliveryFee: Double?,
        taxPlaceId: String?,
        items: List<CreateOrderItemRequest>?,
    ): Result<Order> = runCatching {
        AppLogger.d("OrderRepo", "Updating order: id=$id")
        val response = api.updateOrder(
            id, UpdateOrderRequest(
                clientName = clientName, clientPhone = clientPhone,
                clientAddress = clientAddress, notes = notes,
                paymentMethod = paymentMethod, deliveryFee = deliveryFee,
                taxPlaceId = taxPlaceId, items = items,
            )
        )
        val order = response.toDomain()
        AppLogger.i("OrderRepo", "Order updated: id=${order.id}")
        orderDao.insertOrder(order.toDbEntity())
        orderDao.deleteOrderItems(order.id)
        orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
        order
    }

    private suspend fun saveOrderLocally(
        request: CreateOrderRequest,
        channel: OrderChannel, tableId: String?,
        clientName: String?, clientPhone: String?,
        clientAddress: String?, customerId: String?,
        geoLat: Double?, geoLng: Double?,
        paymentMethod: PaymentMethod, paymentTiming: PaymentTiming,
        notes: String?, items: List<CreateOrderItemRequest>,
    ): Order {
        val localId = "offline-${Clock.System.now().toEpochMilliseconds()}"
        val now = Clock.System.now().toEpochMilliseconds()
        AppLogger.i("OrderRepo", "Saving order offline: localId=$localId, channel=${channel.name}, items=${items.size}")

        val orderItems = items.mapIndexed { index, item ->
            val dbItem = itemDao.getItemById(item.itemId).firstOrNull()
            val variantAdjustment = item.variantSelections?.sumOf { it.priceAdjustment } ?: 0.0
            val variantSnapshot = if (item.variantSelections?.isNotEmpty() == true) {
                json.encodeToString(item.variantSelections)
            } else null
            OrderItem(
                id = "offline-item-$now-$index",
                orderId = localId, itemId = item.itemId,
                itemNameSnapshot = dbItem?.name ?: item.itemId,
                itemPriceSnapshot = (dbItem?.price ?: 0.0) + variantAdjustment,
                quantity = item.quantity, note = item.note,
                variantOptionsSnapshot = variantSnapshot,
            )
        }
        val subtotal = orderItems.sumOf { it.itemPriceSnapshot * it.quantity }

        val order = Order(
            id = localId, vendorId = vendorId,
            channel = channel, status = OrderStatus.CREATED,
            tableId = tableId, cashierId = authRepository.getCurrentUserId() ?: "",
            cashierName = null,
            clientName = clientName, clientPhone = clientPhone,
            clientAddress = clientAddress, customerId = customerId,
            geoLat = geoLat, geoLng = geoLng,
            paymentMethod = paymentMethod, paymentTiming = paymentTiming,
            subtotal = subtotal, total = subtotal, notes = notes,
            items = orderItems,
            createdAt = now, syncStatus = "PENDING_SYNC",
        )
        orderDao.insertOrder(order.toDbEntity())
        orderDao.insertOrderItems(order.items.map { it.toDbEntity() })

        AppLogger.d("OrderRepo", "DB: inserted offline order $localId, subtotal=$subtotal")
        pendingSyncDao.insertPending(Pending_sync(
            id = localId, type = "ORDER",
            payload = json.encodeToString(request),
            created_at = now, retry_count = 0, last_error = null,
        ))
        AppLogger.d("OrderRepo", "PendingSync: queued ORDER for sync, id=$localId")
        return order
    }

    override suspend fun fetchOrder(id: String): Result<Order> =
        runCatching {
            AppLogger.d("OrderRepo", "Fetching order: id=$id")
            val response = api.getOrder(id)
            val order = response.toDomain()
            orderDao.insertOrder(order.toDbEntity())
            orderDao.deleteOrderItems(order.id)
            orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
            order
        }

    override suspend fun updateOrderStatus(id: String, status: OrderStatus): Result<Order> =
        runCatching {
            AppLogger.d("OrderRepo", "Updating order status: id=$id, status=${status.name}")
            try {
                val response = api.updateOrderStatus(id, UpdateOrderStatusRequest(status.name))
                val order = response.toDomain()
                AppLogger.i("OrderRepo", "Order status updated: id=$id, newStatus=${order.status}, paymentStatus=${order.paymentStatus}")
                AppLogger.d("OrderRepo", "DB: updating order $id with new status ${order.status}")
                orderDao.insertOrder(order.toDbEntity())
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
                order
            } catch (e: Exception) {
                AppLogger.e("OrderRepo", "Order status update FAILED: id=$id, target=${status.name}", e)
                if (offlineModeManager.offlineModeEnabled.value) {
                    AppLogger.i("OrderRepo", "Saving status update offline: id=$id, status=${status.name}")
                    val now = Clock.System.now().toEpochMilliseconds()
                    orderDao.updateOrderStatus(id, status.name, now)
                    pendingSyncDao.insertPending(Pending_sync(
                        id = "status-$id-$now", type = "ORDER_STATUS_UPDATE",
                        payload = json.encodeToString(OrderStatusPayload(id, status.name)),
                        created_at = now, retry_count = 0, last_error = null,
                    ))
                    AppLogger.d("OrderRepo", "DB: reading order $id from local cache")
                    val items = orderDao.getOrderItemsList(id).map { it.toDomain() }
                    val cachedOrder = orderDao.getOrderById(id).firstOrNull()?.toDomain(items)
                    AppLogger.d("OrderRepo", "DB: local order found=${cachedOrder != null}")
                    cachedOrder ?: throw e
                } else throw e
            }
        }

    override suspend fun updatePaymentStatus(id: String, status: PaymentStatus, paymentMethod: PaymentMethod?): Result<Order> =
        runCatching {
            AppLogger.d("OrderRepo", "Updating payment status: id=$id, status=${status.name}")
            try {
                val response = api.updatePaymentStatus(id, UpdatePaymentStatusRequest(status.name, paymentMethod?.name))
                val order = response.toDomain()
                AppLogger.i("OrderRepo", "Payment status updated: id=$id, paymentStatus=${order.paymentStatus}, method=${paymentMethod?.name}")
                AppLogger.d("OrderRepo", "DB: updating order $id with payment status ${order.paymentStatus}")
                orderDao.insertOrder(order.toDbEntity())
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
                order
            } catch (e: Exception) {
                AppLogger.e("OrderRepo", "Payment status update FAILED: id=$id, target=${status.name}", e)
                if (offlineModeManager.offlineModeEnabled.value) {
                    AppLogger.i("OrderRepo", "Saving payment update offline: id=$id, status=${status.name}")
                    val now = Clock.System.now().toEpochMilliseconds()
                    orderDao.updatePaymentStatus(status.name, now, id)
                    pendingSyncDao.insertPending(Pending_sync(
                        id = "payment-$id-$now", type = "PAYMENT_UPDATE",
                        payload = json.encodeToString(PaymentUpdatePayload(id, status.name, paymentMethod?.name)),
                        created_at = now, retry_count = 0, last_error = null,
                    ))
                    val items = orderDao.getOrderItemsList(id).map { it.toDomain() }
                    orderDao.getOrderById(id).firstOrNull()?.toDomain(items)
                        ?: throw e
                } else throw e
            }
        }

    override suspend fun assignDeliveryUser(id: String, deliveryUserId: String): Result<Order> =
        runCatching {
            AppLogger.d("OrderRepo", "Assigning delivery user: orderId=$id, deliveryUserId=$deliveryUserId")
            val response = api.assignDeliveryUser(id, AssignDeliveryRequest(deliveryUserId))
            val order = response.toDomain()
            AppLogger.i("OrderRepo", "Delivery user assigned: orderId=$id, deliveryUser=${order.deliveryUserName}, status=${order.status}")
            AppLogger.d("OrderRepo", "DB: updating order $id with delivery assignment")
            orderDao.insertOrder(order.toDbEntity())
            orderDao.deleteOrderItems(order.id)
            orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
            order
        }

    override suspend fun refundOrder(id: String, reason: String): Result<Order> =
        runCatching {
            AppLogger.d("OrderRepo", "Refunding order: id=$id")
            try {
                val response = api.refundOrder(id, RefundOrderRequest(reason))
                val order = response.toDomain()
                AppLogger.i("OrderRepo", "Order refunded: id=$id, status=${order.status}, paymentStatus=${order.paymentStatus}")
                AppLogger.d("OrderRepo", "DB: updating refunded order $id")
                orderDao.insertOrder(order.toDbEntity())
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toDbEntity() })
                order
            } catch (e: Exception) {
                AppLogger.e("OrderRepo", "Refund FAILED: id=$id, reason=$reason", e)
                if (offlineModeManager.offlineModeEnabled.value) {
                    AppLogger.i("OrderRepo", "Saving refund offline: id=$id")
                    val now = Clock.System.now().toEpochMilliseconds()
                    orderDao.refundOrder(id, reason, now)
                    pendingSyncDao.insertPending(Pending_sync(
                        id = "refund-$id-$now", type = "REFUND",
                        payload = json.encodeToString(RefundPayload(id, reason)),
                        created_at = now, retry_count = 0, last_error = null,
                    ))
                    val items = orderDao.getOrderItemsList(id).map { it.toDomain() }
                    orderDao.getOrderById(id).firstOrNull()?.toDomain(items)
                        ?: throw e
                } else throw e
            }
        }

    override suspend fun shareReceipt(id: String): Result<ReceiptShareLink> =
        runCatching {
            AppLogger.d("OrderRepo", "Sharing receipt: orderId=$id")
            val response = api.shareReceipt(id)
            ReceiptShareLink(
                url = response.url,
                token = response.token,
                expiresAt = response.expiresAt
            )
        }
}
