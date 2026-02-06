package net.marllex.cafeemanger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    @ColumnInfo(name = "category_id") val categoryId: String,
    val name: String,
    val description: String?,
    val price: Double,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    val available: Boolean
)
