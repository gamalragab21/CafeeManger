package net.marllex.cafeemanger.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.cafeemanger.core.database.dao.OrderDao
import net.marllex.cafeemanger.core.database.mapper.toDomain
import net.marllex.cafeemanger.core.database.mapper.toEntity
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.domain.repository.OrderRepository
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderChannel
import net.marllex.cafeemanger.core.model.OrderItem
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.model.PaymentMethod
import net.marllex.cafeemanger.core.model.ReceiptShareLink
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.*
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class OrderRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
    private val orderDao: OrderDao,
    private val authRepository: AuthRepository,
) : OrderRepository {

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
        from: Long?,
        to: Long?
    ): Result<List<Order>> =
        runCatching {
            val response = api.getOrders(
                status = status,
                channel = channel,
                cashierId = cashierId,
                deliveryUserId = deliveryUserId,
                from = from,
                to = to
            )
            val orders = response.orders.map { it.toDomain() }
            orderDao.insertOrders(orders.map { it.toEntity() })
            orders.forEach { order ->
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toEntity() })
            }
            orders
        }

    override suspend fun refreshMyDeliveryOrders(status: String?): Result<List<Order>> =
        runCatching {
            val response = api.getMyDeliveryOrders(status = status)
            val orders = response.map { it.toDomain() }
            orderDao.insertOrders(orders.map { it.toEntity() })
            orders.forEach { order ->
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toEntity() })
            }
            orders
        }

    override suspend fun getAvailableDeliveryOrders(): Result<List<Order>> =
        runCatching {
            val response = api.getAvailableDeliveryOrders()
            val orders = response.map { it.toDomain() }
            orderDao.insertOrders(orders.map { it.toEntity() })
            orders.forEach { order ->
                orderDao.deleteOrderItems(order.id)
                orderDao.insertOrderItems(order.items.map { it.toEntity() })
            }
            orders
        }

    override suspend fun createOrder(
        channel: OrderChannel, tableId: String?,
        clientName: String?, clientPhone: String?, clientAddress: String?,
        geoLat: Double?, geoLng: Double?,
        paymentMethod: PaymentMethod, taxPlaceId: String?, notes: String?,
        items: List<CreateOrderItemRequest>
    ): Result<Order> = runCatching {
        val response = api.createOrder(
            CreateOrderRequest(
                channel = channel.name, tableId = tableId,
                clientName = clientName, clientPhone = clientPhone,
                clientAddress = clientAddress,
                geoLat = geoLat, geoLng = geoLng,
                paymentMethod = paymentMethod.name,
                taxPlaceId = taxPlaceId,
                notes = notes,
                items = items
            )
        )
        val order = response.toDomain()
        orderDao.insertOrder(order.toEntity())
        orderDao.insertOrderItems(order.items.map { it.toEntity() })
        // Stock deduction is handled server-side automatically
        order
    }

    override suspend fun fetchOrder(id: String): Result<Order> =
        runCatching {
            val response = api.getOrder(id)
            val order = response.toDomain()
            orderDao.insertOrder(order.toEntity())
            orderDao.deleteOrderItems(order.id)
            orderDao.insertOrderItems(order.items.map { it.toEntity() })
            order
        }

    override suspend fun updateOrderStatus(id: String, status: OrderStatus): Result<Order> =
        runCatching {
            val response = api.updateOrderStatus(id, UpdateOrderStatusRequest(status.name))
            val order = response.toDomain()
            // Update local DB with full order from server
            orderDao.insertOrder(order.toEntity())
            orderDao.deleteOrderItems(order.id)
            orderDao.insertOrderItems(order.items.map { it.toEntity() })
            order
        }

    override suspend fun assignDeliveryUser(id: String, deliveryUserId: String): Result<Order> =
        runCatching {
            val response = api.assignDeliveryUser(id, AssignDeliveryRequest(deliveryUserId))
            val order = response.toDomain()
            // Update local DB with actual data from server
            orderDao.insertOrder(order.toEntity())
            orderDao.deleteOrderItems(order.id)
            orderDao.insertOrderItems(order.items.map { it.toEntity() })
            order
        }

    override suspend fun shareReceipt(id: String): Result<ReceiptShareLink> =
        runCatching {
            val response = api.shareReceipt(id)
            ReceiptShareLink(
                url = response.url,
                token = response.token,
                expiresAt = response.expiresAt
            )
        }
}
