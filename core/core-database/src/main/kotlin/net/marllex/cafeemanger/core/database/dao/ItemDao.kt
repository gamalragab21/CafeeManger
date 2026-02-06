package net.marllex.cafeemanger.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.database.entity.ItemEntity

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE vendor_id = :vendorId ORDER BY name ASC")
    fun getItems(vendorId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE vendor_id = :vendorId AND category_id = :categoryId ORDER BY name ASC")
    fun getItemsByCategory(vendorId: String, categoryId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE vendor_id = :vendorId AND available = 1 ORDER BY name ASC")
    fun getAvailableItems(vendorId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    fun getItemById(id: String): Flow<ItemEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ItemEntity)

    @Update
    suspend fun updateItem(item: ItemEntity)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM items WHERE vendor_id = :vendorId")
    suspend fun deleteAllItems(vendorId: String)
}
