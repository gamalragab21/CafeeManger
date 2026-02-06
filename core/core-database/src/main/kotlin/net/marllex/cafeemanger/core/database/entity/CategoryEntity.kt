package net.marllex.cafeemanger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    val name: String,
    @ColumnInfo(name = "display_order") val displayOrder: Int
)
