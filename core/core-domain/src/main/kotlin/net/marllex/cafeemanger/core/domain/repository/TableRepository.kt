package net.marllex.cafeemanger.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.model.Table

interface TableRepository {
    fun getTables(): Flow<List<Table>>
    fun getAvailableTables(): Flow<List<Table>>
    suspend fun refreshTables(): Result<List<Table>>
    suspend fun createTable(number: String, capacity: Int): Result<Table>
    suspend fun updateTable(id: String, number: String?, capacity: Int?): Result<Table>
    suspend fun deleteTable(id: String): Result<Unit>
    suspend fun updateTableStatus(id: String, status: String): Result<Table>
}
