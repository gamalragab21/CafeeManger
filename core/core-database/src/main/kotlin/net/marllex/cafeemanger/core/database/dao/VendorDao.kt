package net.marllex.cafeemanger.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.database.entity.VendorEntity

@Dao
interface VendorDao {
    @Query("SELECT * FROM vendors WHERE id = :id")
    fun getVendorById(id: String): Flow<VendorEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVendor(vendor: VendorEntity)

    @Update
    suspend fun updateVendor(vendor: VendorEntity)

    @Query("DELETE FROM vendors WHERE id = :id")
    suspend fun deleteVendor(id: String)
}
