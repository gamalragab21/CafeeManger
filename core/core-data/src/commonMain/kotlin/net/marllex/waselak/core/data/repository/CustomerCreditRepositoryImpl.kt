package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.CustomerCreditRepository
import net.marllex.waselak.core.model.CreditTransaction
import net.marllex.waselak.core.model.CustomerCredit
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreditChargeRequest
import net.marllex.waselak.core.network.dto.CreditPaymentRequest
import net.marllex.waselak.core.network.dto.SetCreditLimitRequest
import net.marllex.waselak.core.network.mapper.toDomain

class CustomerCreditRepositoryImpl(
    private val api: WaselakApiClient,
) : CustomerCreditRepository {

    override suspend fun getCredit(customerId: String): Result<CustomerCredit> = runCatching {
        api.getCustomerCredit(customerId).toDomain()
    }

    override suspend fun setCreditLimit(customerId: String, limit: Double): Result<CustomerCredit> = runCatching {
        api.setCustomerCreditLimit(customerId, SetCreditLimitRequest(limit)).toDomain()
    }

    override suspend fun chargeCredit(customerId: String, amount: Double, orderId: String?, note: String?): Result<CreditTransaction> = runCatching {
        api.chargeCustomerCredit(customerId, CreditChargeRequest(amount, orderId, note)).toDomain()
    }

    override suspend fun payCredit(customerId: String, amount: Double, note: String?): Result<CreditTransaction> = runCatching {
        api.payCustomerCredit(customerId, CreditPaymentRequest(amount, note)).toDomain()
    }

    override suspend fun getTransactions(customerId: String, limit: Int, offset: Int): Result<List<CreditTransaction>> = runCatching {
        api.getCustomerCreditTransactions(customerId, limit, offset).map { it.toDomain() }
    }

    override suspend fun getDebtors(): Result<List<CustomerCredit>> = runCatching {
        api.getCreditDebtors().map { it.toDomain() }
    }
}
