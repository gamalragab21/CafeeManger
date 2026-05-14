package net.marllex.waselak.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.model.Order
import net.marllex.waselak.core.model.OrderChannel
import net.marllex.waselak.core.model.OrderStatus
import net.marllex.waselak.core.model.PaymentMethod
import net.marllex.waselak.core.model.PaymentStatus
import net.marllex.waselak.core.model.PaymentTiming
import net.marllex.waselak.core.model.ReceiptShareLink
import net.marllex.waselak.core.network.dto.CreateOrderItemRequest

data class PaginatedResult<T>(
    val data: List<T>,
    val total: Int,
    val hasMore: Boolean,
)

interface OrderRepository {
    fun getOrders(status: String? = null, channel: String? = null): Flow<List<Order>>
    fun getOrderById(id: String): Flow<Order?>
    fun getMyDeliveryOrders(status: String? = null): Flow<List<Order>>

    suspend fun refreshOrders(
        status: String? = null,
        channel: String? = null,
        cashierId: String? = null,
        deliveryUserId: String? = null,
        tableId: String? = null,
        from: Long? = null,
        to: Long? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): Result<PaginatedResult<Order>>
    suspend fun refreshMyDeliveryOrders(status: String? = null, limit: Int = 50, offset: Int = 0): Result<PaginatedResult<Order>>
    suspend fun getAvailableDeliveryOrders(): Result<List<Order>>
    suspend fun createOrder(
        channel: OrderChannel,
        tableId: String?,
        clientName: String?, clientPhone: String?, clientAddress: String?,
        customerId: String?,
        geoLat: Double?, geoLng: Double?,
        paymentMethod: PaymentMethod,
        paymentTiming: PaymentTiming = PaymentTiming.PAY_NOW,
        taxPlaceId: String?,
        reservationId: String? = null,
        notes: String?,
        items: List<CreateOrderItemRequest>,
        discount: Double = 0.0,
        discountType: String = "FIXED",
        offerId: String? = null,
        pointsRedeemed: Int = 0,
        discountReason: String? = null,
        doctorName: String? = null,
        diagnosis: String? = null,
        deliveryUserId: String? = null,
        /**
         * Short-lived manager PIN approval token (from `verifyOverridePin`). Required
         * when `discount > 0` and the cashier is not themselves a manager; otherwise
         * the server returns 403 DISCOUNT_REQUIRES_MANAGER.
         */
        managerOverrideToken: String? = null,
        /**
         * Explicit delivery fee computed by the POS (zone flat-fee or vendor default).
         * Passed to the backend AND used by the offline order path so the local copy
         * matches what the backend would have computed. 0.0 means no delivery fee.
         */
        deliveryFee: Double = 0.0,
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
    suspend fun updatePaymentStatus(id: String, status: PaymentStatus, paymentMethod: PaymentMethod? = null): Result<Order>
    suspend fun assignDeliveryUser(id: String, deliveryUserId: String): Result<Order>
    suspend fun refundOrder(id: String, reason: String): Result<Order>
    suspend fun shareReceipt(id: String): Result<ReceiptShareLink>
}
