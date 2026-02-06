package net.marllex.cafeemanger.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.database.entity.OrderEntity
import net.marllex.cafeemanger.core.database.entity.OrderItemEntity

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders WHERE vendor_id = :vendorId ORDER BY created_at DESC")
    fun getOrders(vendorId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE vendor_id = :vendorId AND status = :status ORDER BY created_at DESC")
    fun getOrdersByStatus(vendorId: String, status: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE vendor_id = :vendorId AND channel = :channel ORDER BY created_at DESC")
    fun getOrdersByChannel(vendorId: String, channel: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE delivery_user_id = :userId AND status IN (:statuses) ORDER BY created_at DESC")
    fun getDeliveryOrders(userId: String, statuses: List<String>): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE id = :id")
    fun getOrderById(id: String): Flow<OrderEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrders(orders: List<OrderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity)

    @Query("UPDATE orders SET status = :status, updated_at = :updatedAt WHERE id = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String, updatedAt: Long)

    @Query("UPDATE orders SET delivery_user_id = :userId, status = :status, updated_at = :updatedAt WHERE id = :orderId")
    suspend fun assignDeliveryUser(orderId: String, userId: String, status: String = "ASSIGNED", updatedAt: Long)

    // ─── Order Items ─────────────────────────────────────────────
    @Query("SELECT * FROM order_items WHERE order_id = :orderId")
    fun getOrderItems(orderId: String): Flow<List<OrderItemEntity>>

    @Query("SELECT * FROM order_items WHERE order_id = :orderId")
    suspend fun getOrderItemsList(orderId: String): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE order_id IN (:orderIds)")
    suspend fun getOrderItemsForOrders(orderIds: List<String>): List<OrderItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE order_id = :orderId")
    suspend fun deleteOrderItems(orderId: String)
}
