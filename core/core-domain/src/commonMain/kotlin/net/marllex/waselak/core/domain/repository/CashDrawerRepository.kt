package net.marllex.waselak.core.domain.repository

import net.marllex.waselak.core.model.CashDrawerSession
import net.marllex.waselak.core.model.CashMovement
import net.marllex.waselak.core.model.DrawerSummary

interface CashDrawerRepository {
    suspend fun openDrawer(openingBalance: Double, notes: String? = null): Result<CashDrawerSession>
    suspend fun closeDrawer(closingBalance: Double, notes: String? = null): Result<CashDrawerSession>
    suspend fun getCurrentSession(): Result<CashDrawerSession>
    suspend fun createMovement(type: String, amount: Double, reason: String? = null, orderId: String? = null): Result<CashMovement>
    suspend fun getMovements(sessionId: String? = null, type: String? = null): Result<List<CashMovement>>
    suspend fun getSessions(limit: Int = 20, offset: Int = 0): Result<List<CashDrawerSession>>
    suspend fun getSummary(): Result<DrawerSummary>
}
