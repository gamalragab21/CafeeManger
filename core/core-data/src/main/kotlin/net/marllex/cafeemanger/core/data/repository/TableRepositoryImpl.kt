package net.marllex.cafeemanger.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.cafeemanger.core.database.dao.TableDao
import net.marllex.cafeemanger.core.database.mapper.toDomain
import net.marllex.cafeemanger.core.database.mapper.toEntity
import net.marllex.cafeemanger.core.domain.repository.AuthRepository
import net.marllex.cafeemanger.core.domain.repository.TableRepository
import net.marllex.cafeemanger.core.model.Table
import net.marllex.cafeemanger.core.network.CafeeMangerApi
import net.marllex.cafeemanger.core.network.dto.CreateTableRequest
import net.marllex.cafeemanger.core.network.dto.UpdateTableRequest
import net.marllex.cafeemanger.core.network.dto.UpdateTableStatusRequest
import net.marllex.cafeemanger.core.network.mapper.toDomain
import javax.inject.Inject

class TableRepositoryImpl @Inject constructor(
    private val api: CafeeMangerApi,
    private val tableDao: TableDao,
    private val authRepository: AuthRepository,
) : TableRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getTables(): Flow<List<Table>> =
        tableDao.getTables(vendorId).map { list -> list.map { it.toDomain() } }

    override fun getAvailableTables(): Flow<List<Table>> =
        tableDao.getTablesByStatus(vendorId, "AVAILABLE").map { list -> list.map { it.toDomain() } }

    override suspend fun refreshTables(): Result<List<Table>> = runCatching {
        val response = api.getTables()
        val tables = response.map { it.toDomain() }
        tableDao.deleteAllTables(vendorId)
        tableDao.insertTables(tables.map { it.toEntity() })
        tables
    }

    override suspend fun createTable(number: String, capacity: Int): Result<Table> = runCatching {
        val response = api.createTable(CreateTableRequest(number, capacity))
        val table = response.toDomain()
        tableDao.insertTable(table.toEntity())
        table
    }

    override suspend fun updateTable(id: String, number: String?, capacity: Int?): Result<Table> = runCatching {
        val response = api.updateTable(id, UpdateTableRequest(number, capacity))
        val table = response.toDomain()
        tableDao.insertTable(table.toEntity())
        table
    }

    override suspend fun deleteTable(id: String): Result<Unit> = runCatching {
        api.deleteTable(id)
        tableDao.deleteTable(id)
    }

    override suspend fun updateTableStatus(id: String, status: String): Result<Table> = runCatching {
        val response = api.updateTableStatus(id, UpdateTableStatusRequest(status))
        val table = response.toDomain()
        tableDao.insertTable(table.toEntity())
        table
    }
}
