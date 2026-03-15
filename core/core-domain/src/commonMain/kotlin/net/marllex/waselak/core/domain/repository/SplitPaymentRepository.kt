package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.OrderPayment
import net.marllex.waselak.core.model.SplitPaymentSummary

interface SplitPaymentRepository {
    suspend fun getPaymentSummary(orderId: String): Result<SplitPaymentSummary>
    suspend fun addPayment(orderId: String, paymentMethod: String, amount: Double, note: String? = null): Result<OrderPayment>
    suspend fun deletePayment(orderId: String, paymentId: String): Result<Unit>
}
