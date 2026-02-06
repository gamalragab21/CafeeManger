package net.marllex.cafeemanger.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.database.entity.TableEntity

@Dao
interface TableDao {
    @Query("SELECT * FROM tables WHERE vendor_id = :vendorId ORDER BY number ASC")
    fun getTables(vendorId: String): Flow<List<TableEntity>>

    @Query("SELECT * FROM tables WHERE vendor_id = :vendorId AND status = :status ORDER BY number ASC")
    fun getTablesByStatus(vendorId: String, status: String): Flow<List<TableEntity>>

    @Query("SELECT * FROM tables WHERE id = :id")
    fun getTableById(id: String): Flow<TableEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(tables: List<TableEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: TableEntity)

    @Update
    suspend fun updateTable(table: TableEntity)

    @Query("DELETE FROM tables WHERE id = :id")
    suspend fun deleteTable(id: String)

    @Query("DELETE FROM tables WHERE vendor_id = :vendorId")
    suspend fun deleteAllTables(vendorId: String)
}
