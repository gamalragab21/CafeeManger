package net.marllex.cafeemanger.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vendors")
data class VendorEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "logo_url") val logoUrl: String?,
    val address: String,
    @ColumnInfo(name = "contact_phone") val contactPhone: String,
    @ColumnInfo(name = "wallet_phone") val walletPhone: String?,
    @ColumnInfo(name = "default_delivery_fee") val defaultDeliveryFee: Double = 0.0,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long?
)
