package net.marllex.cafeemanger.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.marllex.cafeemanger.core.database.entity.UserEntity

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE vendor_id = :vendorId ORDER BY name ASC")
    fun getUsers(vendorId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE vendor_id = :vendorId AND role = :role ORDER BY name ASC")
    fun getUsersByRole(vendorId: String, role: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserById(id: String): Flow<UserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUser(id: String)

    @Query("DELETE FROM users WHERE vendor_id = :vendorId")
    suspend fun deleteAllUsers(vendorId: String)
}
