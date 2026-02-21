package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Order_items
import net.marllex.waselak.core.database.Orders

class OrderDao(private val db: WaselakDatabase) {
    private val orderQueries get() = db.orderQueries
    private val orderItemQueries get() = db.orderItemQueries

    fun getOrders(vendorId: String): Flow<List<Orders>> =
        orderQueries.getOrders(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getOrdersByStatus(vendorId: String, status: String): Flow<List<Orders>> =
        orderQueries.getOrdersByStatus(vendorId, status).asFlow().mapToList(Dispatchers.Default)

    fun getOrdersByChannel(vendorId: String, channel: String): Flow<List<Orders>> =
        orderQueries.getOrdersByChannel(vendorId, channel).asFlow().mapToList(Dispatchers.Default)

    fun getDeliveryOrders(userId: String, statuses: List<String>): Flow<List<Orders>> =
        orderQueries.getDeliveryOrders(userId, statuses).asFlow().mapToList(Dispatchers.Default)

    fun getOrderById(id: String): Flow<Orders?> =
        orderQueries.getOrderById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun insertOrders(orders: List<Orders>) {
        db.transaction {
            orders.forEach { order -> insertOrderInternal(order) }
        }
    }

    suspend fun insertOrder(order: Orders) {
        insertOrderInternal(order)
    }

    private fun insertOrderInternal(order: Orders) {
        orderQueries.insertOrder(
            id = order.id,
            vendor_id = order.vendor_id,
            channel = order.channel,
            status = order.status,
            table_id = order.table_id,
            table_number = order.table_number,
            cashier_id = order.cashier_id,
            cashier_name = order.cashier_name,
            delivery_user_id = order.delivery_user_id,
            delivery_user_name = order.delivery_user_name,
            client_name = order.client_name,
            client_phone = order.client_phone,
            client_address = order.client_address,
            customer_id = order.customer_id,
            geo_lat = order.geo_lat,
            geo_lng = order.geo_lng,
            payment_method = order.payment_method,
            payment_status = order.payment_status,
            payment_timing = order.payment_timing,
            payment_confirmed_at = order.payment_confirmed_at,
            payment_confirmed_by = order.payment_confirmed_by,
            subtotal = order.subtotal,
            delivery_fee = order.delivery_fee,
            discount = order.discount,
            discount_type = order.discount_type,
            tax = order.tax,
            tax_percent = order.tax_percent,
            total = order.total,
            notes = order.notes,
            created_at = order.created_at,
            updated_at = order.updated_at
        )
    }

    suspend fun updateOrderStatus(orderId: String, status: String, updatedAt: Long) {
        orderQueries.updateOrderStatus(status, updatedAt, orderId)
    }

    suspend fun updatePaymentStatus(paymentStatus: String, updatedAt: Long, orderId: String) {
        orderQueries.updatePaymentStatus(paymentStatus, updatedAt, orderId)
    }

    suspend fun assignDeliveryUser(orderId: String, userId: String, updatedAt: Long) {
        orderQueries.assignDeliveryUser(userId, updatedAt, orderId)
    }

    fun getOrdersByCustomerId(customerId: String, limit: Long): Flow<List<Orders>> =
        orderQueries.getOrdersByCustomerId(customerId, limit).asFlow().mapToList(Dispatchers.Default)

    // ─── Order Items ─────────────────────────────────────────────
    fun getOrderItems(orderId: String): Flow<List<Order_items>> =
        orderItemQueries.getOrderItems(orderId).asFlow().mapToList(Dispatchers.Default)

    suspend fun getOrderItemsList(orderId: String): List<Order_items> =
        orderItemQueries.getOrderItems(orderId).executeAsList()

    suspend fun getOrderItemsForOrders(orderIds: List<String>): List<Order_items> =
        orderItemQueries.getOrderItemsForOrders(orderIds).executeAsList()

    suspend fun insertOrderItems(items: List<Order_items>) {
        db.transaction {
            items.forEach { item ->
                orderItemQueries.insertOrderItem(
                    id = item.id,
                    order_id = item.order_id,
                    item_id = item.item_id,
                    item_name_snapshot = item.item_name_snapshot,
                    item_price_snapshot = item.item_price_snapshot,
                    quantity = item.quantity,
                    note = item.note
                )
            }
        }
    }

    suspend fun deleteOrderItems(orderId: String) {
        orderItemQueries.deleteOrderItems(orderId)
    }
}
