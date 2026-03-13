package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.ProductReturn
import net.marllex.waselak.core.model.ReturnsSummary
import net.marllex.waselak.core.network.dto.CreateReturnRequest

interface ReturnRepository {
    suspend fun getReturns(status: String? = null, orderId: String? = null, limit: Int = 50, offset: Int = 0): Result<List<ProductReturn>>
    suspend fun getReturn(id: String): Result<ProductReturn>
    suspend fun createReturn(request: CreateReturnRequest): Result<ProductReturn>
    suspend fun processReturn(id: String, status: String, notes: String? = null): Result<ProductReturn>
    suspend fun getSummary(): Result<ReturnsSummary>
}
