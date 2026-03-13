package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.ReturnRepository
import net.marllex.waselak.core.model.ProductReturn
import net.marllex.waselak.core.model.ReturnsSummary
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateReturnRequest
import net.marllex.waselak.core.network.dto.ProcessReturnRequest
import net.marllex.waselak.core.network.mapper.toDomain

class ReturnRepositoryImpl(
    private val api: WaselakApiClient,
) : ReturnRepository {

    override suspend fun getReturns(status: String?, orderId: String?, limit: Int, offset: Int): Result<List<ProductReturn>> = runCatching {
        api.getReturns(status, orderId, limit, offset).map { it.toDomain() }
    }

    override suspend fun getReturn(id: String): Result<ProductReturn> = runCatching {
        api.getReturn(id).toDomain()
    }

    override suspend fun createReturn(request: CreateReturnRequest): Result<ProductReturn> = runCatching {
        api.createReturn(request).toDomain()
    }

    override suspend fun processReturn(id: String, status: String, notes: String?): Result<ProductReturn> = runCatching {
        api.processReturn(id, ProcessReturnRequest(status, notes)).toDomain()
    }

    override suspend fun getSummary(): Result<ReturnsSummary> = runCatching {
        api.getReturnsSummary().toDomain()
    }
}
