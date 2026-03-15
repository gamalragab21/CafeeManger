package net.marllex.waselak.core.data.repository

import net.marllex.waselak.core.domain.repository.CashDrawerRepository
import net.marllex.waselak.core.model.CashDrawerSession
import net.marllex.waselak.core.model.CashMovement
import net.marllex.waselak.core.model.DrawerSummary
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CloseDrawerRequest
import net.marllex.waselak.core.network.dto.CreateCashMovementRequest
import net.marllex.waselak.core.network.dto.OpenDrawerRequest
import net.marllex.waselak.core.network.mapper.toDomain

class CashDrawerRepositoryImpl(
    private val api: WaselakApiClient,
) : CashDrawerRepository {

    override suspend fun openDrawer(openingBalance: Double, notes: String?): Result<CashDrawerSession> = runCatching {
        api.openCashDrawer(OpenDrawerRequest(openingBalance, notes)).toDomain()
    }

    override suspend fun closeDrawer(closingBalance: Double, notes: String?): Result<CashDrawerSession> = runCatching {
        api.closeCashDrawer(CloseDrawerRequest(closingBalance, notes)).toDomain()
    }

    override suspend fun getCurrentSession(): Result<CashDrawerSession?> = runCatching {
        api.getCurrentDrawerSession()?.toDomain()
    }

    override suspend fun createMovement(type: String, amount: Double, reason: String?, orderId: String?): Result<CashMovement> = runCatching {
        api.createCashMovement(CreateCashMovementRequest(type, amount, reason, orderId)).toDomain()
    }

    override suspend fun getMovements(sessionId: String?, type: String?): Result<List<CashMovement>> = runCatching {
        api.getCashMovements(sessionId, type).map { it.toDomain() }
    }

    override suspend fun getSessions(limit: Int, offset: Int): Result<List<CashDrawerSession>> = runCatching {
        api.getCashDrawerSessions(limit, offset).map { it.toDomain() }
    }

    override suspend fun getSummary(): Result<DrawerSummary> = runCatching {
        api.getCashDrawerSummary().toDomain()
    }
}
