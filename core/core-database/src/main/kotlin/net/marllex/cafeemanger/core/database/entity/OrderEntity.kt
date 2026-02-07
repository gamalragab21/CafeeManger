package net.marllex.cafeemanger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "vendor_id") val vendorId: String,
    val channel: String,
    val status: String,
    @ColumnInfo(name = "table_id") val tableId: String?,
    @ColumnInfo(name = "cashier_id") val cashierId: String,
    @ColumnInfo(name = "cashier_name") val cashierName: String? = null,
    @ColumnInfo(name = "delivery_user_id") val deliveryUserId: String?,
    @ColumnInfo(name = "delivery_user_name") val deliveryUserName: String? = null,
    @ColumnInfo(name = "client_name") val clientName: String?,
    @ColumnInfo(name = "client_phone") val clientPhone: String?,
    @ColumnInfo(name = "client_address") val clientAddress: String?,
    @ColumnInfo(name = "geo_lat") val geoLat: Double?,
    @ColumnInfo(name = "geo_lng") val geoLng: Double?,
    @ColumnInfo(name = "payment_method") val paymentMethod: String,
    val subtotal: Double,
    @ColumnInfo(name = "delivery_fee") val deliveryFee: Double = 0.0,
    val tax: Double,
    val total: Double,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?
)

@Entity(tableName = "order_items")
data class OrderItemEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "item_id") val itemId: String,
    @ColumnInfo(name = "item_name_snapshot") val itemNameSnapshot: String,
    @ColumnInfo(name = "item_price_snapshot") val itemPriceSnapshot: Double,
    val quantity: Int,
    val note: String?
)
