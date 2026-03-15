package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.CreditTransaction
import net.marllex.waselak.core.model.CustomerCredit

interface CustomerCreditRepository {
    suspend fun getCredit(customerId: String): Result<CustomerCredit>
    suspend fun setCreditLimit(customerId: String, limit: Double): Result<CustomerCredit>
    suspend fun chargeCredit(customerId: String, amount: Double, orderId: String? = null, note: String? = null): Result<CreditTransaction>
    suspend fun payCredit(customerId: String, amount: Double, note: String? = null): Result<CreditTransaction>
    suspend fun getTransactions(customerId: String, limit: Int = 50, offset: Int = 0): Result<List<CreditTransaction>>
    suspend fun getDebtors(): Result<List<CustomerCredit>>
}
