package net.marllex.cafeemanger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    val role: String,
    val name: String,
    val phone: String,
    val email: String?,
    val active: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long?
)
