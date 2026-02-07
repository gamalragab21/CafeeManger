package net.marllex.cafeemanger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stock")
data class StockEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "item_name") val itemName: String,
    val quantity: Int,
    @ColumnInfo(name = "min_quantity") val minQuantity: Int,
    @ColumnInfo(name = "cost_price") val costPrice: Double,
    val unit: String,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long,
)

@Entity(tableName = "stock_transactions")
data class StockTransactionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "stock_id") val stockId: String,
    val type: String,
    val quantity: Int,
    @ColumnInfo(name = "previous_quantity") val previousQuantity: Int,
    @ColumnInfo(name = "order_id") val orderId: String?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
