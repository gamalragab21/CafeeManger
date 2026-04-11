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
import net.marllex.waselak.core.common.logging.AppLogger

class CashDrawerRepositoryImpl(
    private val api: WaselakApiClient,
) : CashDrawerRepository {

    companion object {
        private const val TAG = "CashDrawer"
        private const val OFFLINE_MSG = "درج الكاش يحتاج اتصال بالإنترنت. يرجى التحقق من الشبكة والمحاولة مرة أخرى."
    }

    override suspend fun openDrawer(openingBalance: Double, notes: String?): Result<CashDrawerSession> = runCatching {
        try {
            api.openCashDrawer(OpenDrawerRequest(openingBalance, notes)).toDomain()
        } catch (e: Exception) {
            AppLogger.e(TAG, "openDrawer failed (possibly offline)", e)
            throw IllegalStateException(OFFLINE_MSG, e)
        }
    }

    override suspend fun closeDrawer(closingBalance: Double, notes: String?): Result<CashDrawerSession> = runCatching {
        try {
            api.closeCashDrawer(CloseDrawerRequest(closingBalance, notes)).toDomain()
        } catch (e: Exception) {
            AppLogger.e(TAG, "closeDrawer failed (possibly offline)", e)
            throw IllegalStateException(OFFLINE_MSG, e)
        }
    }

    override suspend fun getCurrentSession(cashierId: String?): Result<CashDrawerSession?> = runCatching {
        try {
            api.getCurrentDrawerSession(cashierId)?.toDomain()
        } catch (e: Exception) {
            AppLogger.e(TAG, "getCurrentSession failed (possibly offline)", e)
            null
        }
    }

    override suspend fun createMovement(type: String, amount: Double, reason: String?, orderId: String?): Result<CashMovement> = runCatching {
        try {
            api.createCashMovement(CreateCashMovementRequest(type, amount, reason, orderId)).toDomain()
        } catch (e: Exception) {
            AppLogger.e(TAG, "createMovement failed (possibly offline)", e)
            throw IllegalStateException(OFFLINE_MSG, e)
        }
    }

    override suspend fun getMovements(sessionId: String?, type: String?): Result<List<CashMovement>> = runCatching {
        try {
            api.getCashMovements(sessionId, type).map { it.toDomain() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getMovements failed (possibly offline)", e)
            emptyList() // Return empty list instead of crashing
        }
    }

    override suspend fun getSessions(limit: Int, offset: Int, cashierId: String?): Result<List<CashDrawerSession>> = runCatching {
        try {
            api.getCashDrawerSessions(limit, offset, cashierId).map { it.toDomain() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getSessions failed (possibly offline)", e)
            emptyList()
        }
    }

    override suspend fun getAllOpenSessions(): Result<List<CashDrawerSession>> = runCatching {
        try {
            api.getAllOpenDrawerSessions().map { it.toDomain() }
        } catch (e: Exception) {
            AppLogger.e(TAG, "getAllOpenSessions failed", e)
            emptyList()
        }
    }

    override suspend fun getSummary(cashierId: String?): Result<DrawerSummary> = runCatching {
        try {
            api.getCashDrawerSummary(cashierId).toDomain()
        } catch (e: Exception) {
            AppLogger.e(TAG, "getSummary failed (possibly offline)", e)
            throw IllegalStateException(OFFLINE_MSG, e)
        }
    }
}
