package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.SplitPaymentRepository
import net.marllex.waselak.core.model.OrderPayment
import net.marllex.waselak.core.model.SplitPaymentSummary
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateOrderPaymentRequest
import net.marllex.waselak.core.network.mapper.toDomain

class SplitPaymentRepositoryImpl(
    private val api: WaselakApiClient,
) : SplitPaymentRepository {

    override suspend fun getPaymentSummary(orderId: String): Result<SplitPaymentSummary> = runCatching {
        api.getOrderPayments(orderId).toDomain()
    }

    override suspend fun addPayment(orderId: String, paymentMethod: String, amount: Double, note: String?): Result<OrderPayment> = runCatching {
        api.addOrderPayment(orderId, CreateOrderPaymentRequest(paymentMethod, amount, note)).toDomain()
    }

    override suspend fun deletePayment(orderId: String, paymentId: String): Result<Unit> = runCatching {
        api.deleteOrderPayment(orderId, paymentId)
    }
}
