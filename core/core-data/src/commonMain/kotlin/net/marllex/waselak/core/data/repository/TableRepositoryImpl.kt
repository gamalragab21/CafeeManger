package net.marllex.waselak.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.marllex.waselak.core.database.dao.TableDao
import net.marllex.waselak.core.database.mapper.toDomain
import net.marllex.waselak.core.database.mapper.toDbEntity
import net.marllex.waselak.core.domain.repository.AuthRepository
import net.marllex.waselak.core.domain.repository.TableRepository
import net.marllex.waselak.core.model.Table
import net.marllex.waselak.core.network.WaselakApiClient
import net.marllex.waselak.core.network.dto.CreateTableRequest
import net.marllex.waselak.core.network.dto.UpdateTableRequest
import net.marllex.waselak.core.network.dto.UpdateTableStatusRequest
import net.marllex.waselak.core.common.logging.AppLogger
import net.marllex.waselak.core.network.mapper.toDomain

class TableRepositoryImpl constructor(
    private val api: WaselakApiClient,
    private val tableDao: TableDao,
    private val authRepository: AuthRepository,
) : TableRepository {

    private val vendorId: String get() = authRepository.getCurrentVendorId() ?: ""

    override fun getTables(): Flow<List<Table>> =
        tableDao.getTables(vendorId).map { list -> list.map { it.toDomain() } }

    override fun getAvailableTables(): Flow<List<Table>> =
        tableDao.getTablesByStatus(vendorId, "AVAILABLE").map { list -> list.map { it.toDomain() } }

    override suspend fun refreshTables(): Result<List<Table>> = runCatching {
        AppLogger.d("TableRepo", "Refreshing tables")
        val response = api.getTables()
        val tables = response.map { it.toDomain() }
        AppLogger.i("TableRepo", "Fetched ${tables.size} tables")
        tableDao.deleteAllTables(vendorId)
        tableDao.insertTables(tables.map { it.toDbEntity() })
        tables
    }

    override suspend fun createTable(number: String, capacity: Int): Result<Table> = runCatching {
        AppLogger.d("TableRepo", "Creating table: number=$number, capacity=$capacity")
        val response = api.createTable(CreateTableRequest(number, capacity))
        val table = response.toDomain()
        AppLogger.i("TableRepo", "Table created: id=${table.id}")
        tableDao.insertTable(table.toDbEntity())
        table
    }

    override suspend fun updateTable(id: String, number: String?, capacity: Int?): Result<Table> = runCatching {
        AppLogger.d("TableRepo", "Updating table: id=$id")
        val response = api.updateTable(id, UpdateTableRequest(number, capacity))
        val table = response.toDomain()
        tableDao.insertTable(table.toDbEntity())
        table
    }

    override suspend fun deleteTable(id: String): Result<Unit> = runCatching {
        AppLogger.d("TableRepo", "Deleting table: id=$id")
        api.deleteTable(id)
        tableDao.deleteTable(id)
        AppLogger.i("TableRepo", "Table deleted: id=$id")
    }

    override suspend fun updateTableStatus(id: String, status: String): Result<Table> = runCatching {
        AppLogger.d("TableRepo", "Updating table status: id=$id, status=$status")
        val response = api.updateTableStatus(id, UpdateTableStatusRequest(status))
        val table = response.toDomain()
        tableDao.insertTable(table.toDbEntity())
        table
    }
}
