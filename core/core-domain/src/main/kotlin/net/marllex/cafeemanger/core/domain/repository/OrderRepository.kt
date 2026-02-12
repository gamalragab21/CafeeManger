package net.marllex.cafeemanger.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.model.Order
import net.marllex.cafeemanger.core.model.OrderChannel
import net.marllex.cafeemanger.core.model.OrderStatus
import net.marllex.cafeemanger.core.model.PaymentMethod
import net.marllex.cafeemanger.core.model.ReceiptShareLink
import net.marllex.cafeemanger.core.network.dto.CreateOrderItemRequest

interface OrderRepository {
    fun getOrders(status: String? = null, channel: String? = null): Flow<List<Order>>
    fun getOrderById(id: String): Flow<Order?>
    fun getMyDeliveryOrders(status: String? = null): Flow<List<Order>>

    suspend fun refreshOrders(
        status: String? = null,
        channel: String? = null,
        cashierId: String? = null,
        deliveryUserId: String? = null,
        from: Long? = null,
        to: Long? = null
    ): Result<List<Order>>
    suspend fun refreshMyDeliveryOrders(status: String? = null): Result<List<Order>>
    suspend fun getAvailableDeliveryOrders(): Result<List<Order>>
    suspend fun createOrder(
        channel: OrderChannel,
        tableId: String?,
        clientName: String?, clientPhone: String?, clientAddress: String?,
        geoLat: Double?, geoLng: Double?,
        paymentMethod: PaymentMethod,
        taxPlaceId: String?,
        notes: String?,
        items: List<CreateOrderItemRequest>
    ): Result<Order>
    suspend fun fetchOrder(id: String): Result<Order>
    suspend fun updateOrder(
        id: String,
        clientName: String? = null, clientPhone: String? = null,
        clientAddress: String? = null, notes: String? = null,
        paymentMethod: String? = null, deliveryFee: Double? = null,
        taxPlaceId: String? = null,
        items: List<CreateOrderItemRequest>? = null,
    ): Result<Order>
    suspend fun updateOrderStatus(id: String, status: OrderStatus): Result<Order>
    suspend fun assignDeliveryUser(id: String, deliveryUserId: String): Result<Order>
    suspend fun shareReceipt(id: String): Result<ReceiptShareLink>
}
