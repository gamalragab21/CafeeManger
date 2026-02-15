package net.marllex.waselak.core.database.dao

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import net.marllex.waselak.core.database.WaselakDatabase
import net.marllex.waselak.core.database.Tables

class TableDao(private val db: WaselakDatabase) {
    private val queries get() = db.dineTableQueries

    fun getTables(vendorId: String): Flow<List<Tables>> =
        queries.getTables(vendorId).asFlow().mapToList(Dispatchers.Default)

    fun getTablesByStatus(vendorId: String, status: String): Flow<List<Tables>> =
        queries.getTablesByStatus(vendorId, status).asFlow().mapToList(Dispatchers.Default)

    fun getTableById(id: String): Flow<Tables?> =
        queries.getTableById(id).asFlow().mapToOneOrNull(Dispatchers.Default)

    suspend fun insertTables(tables: List<Tables>) {
        db.transaction {
            tables.forEach { table ->
                queries.insertTable(
                    id = table.id,
                    vendor_id = table.vendor_id,
                    number = table.number,
                    capacity = table.capacity,
                    status = table.status
                )
            }
        }
    }

    suspend fun insertTable(table: Tables) {
        queries.insertTable(
            id = table.id,
            vendor_id = table.vendor_id,
            number = table.number,
            capacity = table.capacity,
            status = table.status
        )
    }

    suspend fun deleteTable(id: String) {
        queries.deleteTable(id)
    }

    suspend fun deleteAllTables(vendorId: String) {
        queries.deleteAllTables(vendorId)
    }
}
